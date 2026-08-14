package dev.geolens.sentiment.engine;

import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.sentiment.ml.CircuitBreaker;
import dev.geolens.sentiment.ml.MlClient;
import dev.geolens.sentiment.ml.ServingException;
import dev.geolens.sentiment.persistence.SentimentDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sentiment analizi ve hallüsinasyon tespiti motoru — Go {@code sentiment.Engine} portu.
 * <p>
 * 0421 M-4: mlClient boşsa (ML_SERVING_URL yok) tüm analizler kural tabanlı çalışır;
 * doluysa her analizde önce ML inference denenir, serving hatasında kural tabanlıya düşülür.
 * Devre kesici serving ardışık hatasında ML çağrılarını askıya alır.
 */
@Service
public final class SentimentEngine {

    private static final Logger log = LoggerFactory.getLogger(SentimentEngine.class);

    private final SentimentDao dao;
    private final MlClient ml;
    final CircuitBreaker breaker;

    public SentimentEngine(SentimentDao dao) {
        this(dao, (MlClient) null);
    }

    @Autowired
    public SentimentEngine(SentimentDao dao, ObjectProvider<MlClient> mlProvider) {
        this(dao, mlProvider.getIfAvailable());
    }

    public SentimentEngine(SentimentDao dao, MlClient mlClient) {
        this.dao = dao;
        this.ml = mlClient;
        this.breaker = new CircuitBreaker("sentiment", null);
    }

    /** Kural tabanlı analiz kelime listeleri — Go analyzeText ile aynı. */
    private static final String[] POSITIVE_WORDS = {
            "harika", "mükemmel", "başarılı", "iyi", "güzel", "kaliteli", "güvenilir",
            "yenilikçi", "lider", "en iyi", "öncü", "sağlam", "hızlı", "kolay", "great", "excellent",
            "best", "reliable", "innovative", "leading", "outstanding", "quality"};

    private static final String[] NEGATIVE_WORDS = {
            "kötü", "başarısız", "yetersiz", "sorunlu", "şikayet", "hatalı", "pahalı",
            "karmaşık", "yavaş", "zor", "bad", "poor", "failure", "expensive", "complicated",
            "slow", "issue", "problem", "complaint", "terrible"};

    /**
     * Marka için motor yanıtlarından duygu analizi yapar. PrompText 051_raw_responses_prompt
     * alanıyla cross-source karşılaştırmasına aktarılır (AnalyzeSentiment doğrudan prompt'a
     * bağlı değildir — yanıtlar marka bazında gruplanır).
     */
    public List<SentimentResult> analyzeSentiment(String tenantId, String workspaceId, String brandId, String prompt) {
        List<RawResponse> raw = dao.loadRawResponses(tenantId, brandId);
        if (raw.isEmpty()) {
            return List.of();
        }
        Map<String, List<RawResponse>> byEngine = new LinkedHashMap<>();
        for (RawResponse r : raw) {
            byEngine.computeIfAbsent(r.engineName(), k -> new ArrayList<>()).add(r);
        }
        List<SentimentResult> results = new ArrayList<>();
        for (Map.Entry<String, List<RawResponse>> e : byEngine.entrySet()) {
            SentimentResult result = analyzeWithML(e.getKey(), brandId, e.getValue());
            results.add(result);
            dao.saveSentiment(tenantId, workspaceId, result);
        }
        log.info("sentiment analizi tamamlandı, brand={}, engines={}", brandId, results.size());
        return results;
    }

    /** 0421 M-4 — her yanıt serving'e ayrı gönderilir, kelime sayısıyla ağırlıklı ortalama. */
    SentimentResult analyzeWithML(String engineName, String brandId, List<RawResponse> responses) {
        if (ml == null || breaker.inCooldown()) {
            return analyzeText(engineName, brandId, combineText(responses));
        }
        List<double[]> items = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        int failed = 0;
        for (RawResponse r : responses) {
            String text = r.content().trim();
            if (text.isEmpty()) {
                continue;
            }
            MlClient.SentimentPrediction pred;
            try {
                pred = ml.predictSentiment(text);
            } catch (ServingException err) {
                failed++;
                log.warn("sentiment: per-response ML çağrısı başarısız, engine={}, response={}", engineName, r.id());
                if (items.isEmpty()) {
                    breaker.fail();
                    break;
                }
                continue;
            }
            items.add(pred.probabilities());
            weights.add((double) text.split("\\s+").length);
        }
        if (items.isEmpty()) {
            breaker.fail();
            return analyzeText(engineName, brandId, combineText(responses));
        }
        breaker.success();
        log.debug("sentiment: per-response ML sonucu, engine={}, responses={}, failed={}",
                engineName, items.size(), failed);
        return sentimentFromProbabilities(engineName, brandId, aggregateWeighted(items, weights), items.size());
    }

    /** Ağırlıklı ortalama — Go aggregateWeighted. */
    static double[] aggregateWeighted(List<double[]> items, List<Double> weights) {
        double[] acc = new double[3];
        double total = 0;
        for (int i = 0; i < items.size(); i++) {
            double w = weights.get(i);
            for (int j = 0; j < 3; j++) {
                acc[j] += items.get(i)[j] * w;
            }
            total += w;
        }
        if (total == 0) {
            return new double[3];
        }
        return new double[]{acc[0] / total, acc[1] / total, acc[2] / total};
    }

    /** Motor yanıtlarını kural tabanlı fallback için birleştirir. */
    static String combineText(List<RawResponse> responses) {
        StringBuilder sb = new StringBuilder();
        for (RawResponse r : responses) {
            sb.append(r.content()).append(' ');
        }
        return sb.toString();
    }

    /** Softmax olasılıklarını [neg, nötr, poz] SentimentResult skorlarına çevirir. Overall = poz + nötr*0.5. */
    static SentimentResult sentimentFromProbabilities(String engineName, String brandId,
                                                      double[] probs, int mentionCount) {
        return SentimentResult.of(brandId, engineName,
                probs[2] + probs[1] * 0.5, probs[2], probs[1], probs[0], mentionCount, Instant.now());
    }

    /** Basit kural tabanlı duygu analizi — Go analyzeText ile birebir. */
    SentimentResult analyzeText(String engineName, String brandId, String text) {
        String lower = text.toLowerCase(Locale.forLanguageTag("tr"));
        String[] words = lower.split("\\s+");
        int positive = 0;
        int negative = 0;
        for (String word : words) {
            for (String pw : POSITIVE_WORDS) {
                if (word.equals(pw) || word.contains(pw)) {
                    positive++;
                    break;
                }
            }
            for (String nw : NEGATIVE_WORDS) {
                if (word.equals(nw) || word.contains(nw)) {
                    negative++;
                    break;
                }
            }
        }
        int total = positive + negative;
        if (total == 0) {
            return new SentimentResult(null, brandId, engineName, 0.5, 0.0, 1.0, 0.0, 0, null, Instant.now());
        }
        double positiveScore = (double) positive / total;
        double negativeScore = (double) negative / total;
        double neutralScore = 1.0 - positiveScore - negativeScore;
        if (neutralScore < 0) {
            neutralScore = 0;
        }
        double overall = positiveScore * 1.0 + neutralScore * 0.5 + negativeScore * 0.0;
        return new SentimentResult(null, brandId, engineName, overall,
                positiveScore, neutralScore, negativeScore, total, null, Instant.now());
    }

    /** Marka için hallüsinasyon tespiti — Go DetectHallucinations portu. */
    public List<HallucinationResult> detectHallucinations(String tenantId, String workspaceId, String brandId) {
        List<CheckTarget> targets = dao.loadCheckTargets(tenantId, brandId);
        if (targets.isEmpty()) {
            return List.of();
        }
        List<HallucinationResult> results = applyMLCrossSource(ruleBasedHallucinations(targets, brandId), targets, brandId);
        for (HallucinationResult h : results) {
            dao.saveHallucination(tenantId, workspaceId, h);
        }
        log.info("hallüsinasyon tespiti tamamlandı, brand={}, count={}", brandId, results.size());
        return results;
    }

    /** Controller birleştirildi: prompt'a göre gruplanır (051), ≥2 yanıtlı gruplara serving; T1-T4 ile merge. */
    List<HallucinationResult> applyMLCrossSource(List<HallucinationResult> base,
                                                 List<CheckTarget> targets, String brandId) {
        List<HallucinationResult> results = new ArrayList<>(base);
        if (ml == null || breaker.inCooldown()) {
            return results;
        }
        boolean mlAttempted = false;
        boolean mlFailed = false;
        for (List<CheckTarget> group : groupByPrompt(targets)) {
            if (group.size() < 2) {
                continue;
            }
            mlAttempted = true;
            try {
                results = mergeHallucinationResults(results, detectHallucinationsWithML(group, brandId));
            } catch (ServingException err) {
                log.warn("hallüsinasyon: serving çağrısı başarısız, T1-T4 kurallarına düşülüyor");
                breaker.fail();
                mlFailed = true;
                break;
            }
        }
        if (mlAttempted && !mlFailed) {
            breaker.success();
            log.debug("hallüsinasyon: cross-source ML + kurallar birleştirildi, brand={}", brandId);
        }
        return results;
    }

    /** Prompt'a göre gruplar (051_raw_responses_prompt.sql) — sıra deterministik (ilk görülme). */
    static List<List<CheckTarget>> groupByPrompt(List<CheckTarget> targets) {
        Map<String, List<CheckTarget>> groups = new LinkedHashMap<>();
        for (CheckTarget t : targets) {
            groups.computeIfAbsent(t.prompt(), k -> new ArrayList<>()).add(t);
        }
        return new ArrayList<>(groups.values());
    }

    /** `(tip|açıklama)` çifti bir kez kaydedilecek şekilde birleştirir. */
    static List<HallucinationResult> mergeHallucinationResults(List<HallucinationResult> a, List<HallucinationResult> b) {
        Map<String, HallucinationResult> seen = new LinkedHashMap<>();
        for (HallucinationResult h : a) {
            seen.put(h.hallucinationType() + "|" + h.description(), h);
        }
        for (HallucinationResult h : b) {
            seen.putIfAbsent(h.hallucinationType() + "|" + h.description(), h);
        }
        return new ArrayList<>(seen.values());
    }

    List<HallucinationResult> detectHallucinationsWithML(List<CheckTarget> targets, String brandId) {
        List<MlClient.HallucinationResponse> responses = new ArrayList<>();
        for (CheckTarget t : targets) {
            responses.add(new MlClient.HallucinationResponse(t.id(), t.engineName(), t.content()));
        }
        List<MlClient.HallucinationFinding> findings = ml.detectHallucinations(responses);
        List<HallucinationResult> out = new ArrayList<>();
        Instant now = Instant.now();
        for (MlClient.HallucinationFinding f : findings) {
            out.add(new HallucinationResult(null, brandId, f.engine(), f.type(), f.severity(),
                    f.description(), f.confidence(), null, now));
        }
        return out;
    }

    /** T1-T4 kural seti — serving yok/hatalı fallback. Go checkHallucinations portu. */
    List<HallucinationResult> ruleBasedHallucinations(List<CheckTarget> targets, String brandId) {
        List<HallucinationResult> out = new ArrayList<>();
        for (CheckTarget t : targets) {
            out.addAll(checkHallucinations(t.content(), t.brandName(), t.engineName(), brandId));
        }
        return out;
    }

    /** T1-T4 kural kontrolleri — Go checkHallucinations ile birebir. */
    List<HallucinationResult> checkHallucinations(String content, String brandName, String engineName, String brandId) {
        List<HallucinationResult> results = new ArrayList<>();
        String lower = content.toLowerCase(Locale.forLanguageTag("tr"));

        if (brandName != null && !brandName.isEmpty() && !lower.contains(brandName.toLowerCase(Locale.forLanguageTag("tr")))) {
            results.add(HallucinationResult.of(brandId, engineName, "T1", "high",
                    "'" + brandName + "' marka adı yanıtta geçmiyor", 0.6, Instant.now()));
        }
        if (lower.contains("kaynak") || lower.contains("source") || lower.contains("according to")) {
            results.add(HallucinationResult.of(brandId, engineName, "T2", "medium",
                    "AI yanıtı kaynak/citation referansı içeriyor — doğrulama gerekli", 0.4, Instant.now()));
        }
        boolean hasNumber = lower.chars().anyMatch(Character::isDigit);
        boolean hasPercent = lower.contains("%") || lower.contains("percent") || lower.contains("yüzde");
        if (hasNumber && hasPercent && !lower.contains("kaynak") && !lower.contains("source")) {
            results.add(HallucinationResult.of(brandId, engineName, "T3", "critical",
                    "AI yanıtı kaynaksız istatistik/rakam içeriyor", 0.5, Instant.now()));
        }
        String[] negWords = {"başarısız", "kötü", "şikayet", "sorun", "skandal", "dava",
                "failure", "scandal", "lawsuit", "bad", "terrible"};
        for (String nw : negWords) {
            if (lower.contains(nw)) {
                results.add(HallucinationResult.of(brandId, engineName, "T4", "medium",
                        "AI yanıtı doğrulanmamış olumsuz ifade içeriyor: '" + nw + "'", 0.3, Instant.now()));
                break;
            }
        }
        return results;
    }

    /** Markanın son duygu sonucu (yoksa null) — Go GetLatestResult portu. */
    public SentimentResult getLatestResult(String brandId, String tenantId) {
        return dao.loadLatest(brandId, tenantId);
    }
}