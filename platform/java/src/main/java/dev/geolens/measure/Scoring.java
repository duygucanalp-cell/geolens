package dev.geolens.measure;

import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Saf, deterministik skor matematiği — Go {@code service.go} içindeki paket seviyesi
 * fonksiyonların portu (G2 determinizm ilkesi: aynı girdi her zaman aynı skor).
 * DB veya ML serving'e bağımlılığı yoktur.
 */
public final class Scoring {

    /** 0421 A3-3 / 0421-8INTENT varsayılan intent çarpanları (sıra: varlık, konum, kaynak, rakip, appearance, sentiment, compvis). */
    public static final Map<String, double[]> DEFAULT_INTENT_SCALE = Map.ofEntries(
            Map.entry("information", new double[]{1.25, 1.00, 0.90, 0.90, 1.10, 0.90, 0.90}),
            Map.entry("recommendation", new double[]{1.00, 1.00, 1.15, 1.00, 0.95, 1.10, 0.95}),
            Map.entry("comparison", new double[]{0.90, 1.00, 0.90, 1.40, 0.90, 0.90, 1.30}),
            Map.entry("opinion", new double[]{1.00, 1.00, 1.00, 1.00, 1.25, 1.00, 1.00}),
            Map.entry("problem", new double[]{1.00, 1.15, 1.10, 1.00, 0.90, 1.00, 1.00}),
            Map.entry("complaint", new double[]{1.00, 1.10, 1.15, 1.00, 0.90, 1.20, 1.00}),
            Map.entry("purchase", new double[]{1.00, 1.00, 1.10, 1.10, 1.00, 1.10, 1.00}),
            Map.entry("news", new double[]{1.10, 1.00, 1.20, 1.00, 0.95, 1.00, 0.95}));

    /** 0309 §6.2 v1.3 motor ağırlıkları. Kademe 3 (directional) motorlar düşük ağırlıkta tutulur. */
    public static final Map<String, Double> ENGINE_WEIGHTS_V130 = Map.ofEntries(
            Map.entry("perplexity", 0.30),
            Map.entry("chatgpt", 0.30),
            Map.entry("gemini", 0.25),
            Map.entry("google_ai_overview", 0.10),
            Map.entry("claude", 0.05),
            Map.entry("grok", 0.05),
            Map.entry("mistral", 0.05),
            Map.entry("copilot", 0.05),
            Map.entry("google_ai_mode", 0.00));

    private Scoring() {
    }

    /** 7 bileşenli skor bileşenleri (v1'de son 3 kullanılmaz). */
    public record Components(double presence, double position, double source, double competitor,
                             double appearance, double sentiment, double compvis) {
    }

    /** Güven aralığı (alt/üst). */
    public record Ci(double low, double high) {
    }

    /** 7 bileşeni hesaplar — Go {@code computeComponentScores} portu. */
    public static Components computeComponentScores(java.util.List<RawResponse> responses, String brandName) {
        return new Components(
                computePresenceShare(responses, brandName),
                computePositionWeight(responses),
                computeSourceShare(responses),
                computeCompetitorContext(responses),
                computeAppearanceRate(responses),
                computeSentimentScore(responses),
                computeCompVisibility(responses, brandName));
    }

    /** Ağırlıklı toplam skor (v1: 4 bileşen, v2: 7 bileşen) ve [0,100] normalizasyonu. */
    public static double computeTotalScore(java.util.List<RawResponse> responses, String brandName, ComponentWeights weights) {
        if (weights == null || weights.isEmpty()) {
            weights = ComponentWeights.V2_DEFAULT;
        }
        Components c = computeComponentScores(responses, brandName);

        double total;
        if (weights.isV2()) {
            total = weights.presenceShare() * c.presence()
                    + weights.positionWeight() * c.position()
                    + weights.sourceShare() * c.source()
                    + weights.competitorContext() * c.competitor()
                    + weights.appearanceRate() * c.appearance()
                    + weights.sentiment() * c.sentiment()
                    + weights.compVisibility() * c.compvis();
        } else {
            total = weights.presenceShare() * c.presence()
                    + weights.positionWeight() * c.position()
                    + weights.sourceShare() * c.source()
                    + weights.competitorContext() * c.competitor();
        }

        total = Math.min(total, 100.0);
        total = Math.max(total, 0.0);
        return total;
    }

    /** Deterministik güven aralığı — v1: sabit ±5; v2: bileşen varyansına dayalı ±3/±4. */
    public static Ci computeScoreCI(double total, ComponentWeights weights) {
        if (!weights.isV2()) {
            return new Ci(total - 5.0, total + 5.0);
        }
        double spread = 3.0;
        if (weights.appearanceRate() != 0 || weights.sentiment() != 0 || weights.compVisibility() != 0) {
            spread = 4.0;
        }
        return new Ci(total - spread, total + spread);
    }

    /** Yanıtların kaçında marka adı geçiyor (%). */
    public static double computePresenceShare(java.util.List<RawResponse> responses, String brandName) {
        if (responses == null || responses.isEmpty()) {
            return 0;
        }
        if (brandName == null || brandName.isBlank()) {
            return computeContentPresence(responses);
        }
        int mentioned = 0;
        String brandLower = brandName.toLowerCase();
        for (RawResponse resp : responses) {
            String contentLower = resp.content() == null ? "" : resp.content().toLowerCase();
            if (contentLower.contains(brandLower)) {
                mentioned++;
            }
        }
        return (double) mentioned / responses.size() * 100.0;
    }

    /** Marka adı yokken içerik varlığına bakan fallback. */
    public static double computeContentPresence(java.util.List<RawResponse> responses) {
        int nonEmpty = 0;
        for (RawResponse resp : responses) {
            if (resp.content() != null && !resp.content().isBlank()) {
                nonEmpty++;
            }
        }
        return (double) nonEmpty / responses.size() * 100.0;
    }

    /** Ortalama konum skoru: ilk 200 karakterde yüksek, sonraki 500'de orta, yoksa düşük. */
    public static double computePositionWeight(java.util.List<RawResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (RawResponse resp : responses) {
            int len = resp.content() == null ? 0 : resp.content().length();
            if (len == 0) {
                total += 0;
            } else if (len <= 200) {
                total += 90;
            } else if (len <= 700) {
                total += 60;
            } else {
                total += 30;
            }
        }
        return total / responses.size();
    }

    /** Alıntı domain çeşitliliğinden kaynak payı. Alıntı yoksa 20. */
    public static double computeSourceShare(java.util.List<RawResponse> responses) {
        int totalCitations = 0;
        java.util.Set<String> domains = new java.util.HashSet<>();

        for (RawResponse resp : responses) {
            for (var c : resp.citations()) {
                totalCitations++;
                String domain = extractDomain(c.url());
                if (!domain.isEmpty()) {
                    domains.add(domain);
                }
            }
        }

        if (totalCitations == 0) {
            return 20;
        }

        int domainCount = domains.size();
        if (domainCount >= 5) {
            return 100;
        } else if (domainCount >= 3) {
            return 75;
        } else if (domainCount >= 1) {
            return 50;
        }
        return 20;
    }

    /** Marka farklılaşması — benzersiz kaynak (domain) sayısına dayalı rakip bağlamı. */
    public static double computeCompetitorContext(java.util.List<RawResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return 50;
        }

        Map<String, Map<String, Integer>> citationDomains = new LinkedHashMap<>();
        for (RawResponse resp : responses) {
            Map<String, Integer> domains = new LinkedHashMap<>();
            for (var c : resp.citations()) {
                String domain = extractDomain(c.url());
                if (!domain.isEmpty()) {
                    domains.merge(domain, 1, Integer::sum);
                }
            }
            String content = resp.content() == null ? "" : resp.content();
            String key = resp.engineName() + content.substring(0, Math.min(30, content.length()));
            citationDomains.put(key, domains);
        }

        if (citationDomains.isEmpty()) {
            return 30;
        }

        java.util.Set<String> totalUniqueSources = new java.util.HashSet<>();
        for (Map<String, Integer> domains : citationDomains.values()) {
            totalUniqueSources.addAll(domains.keySet());
        }

        int uniqueSourceCount = totalUniqueSources.size();
        if (uniqueSourceCount >= 5) {
            return 100;
        } else if (uniqueSourceCount >= 3) {
            return 75;
        } else if (uniqueSourceCount >= 1) {
            return 50;
        }
        return 30;
    }

    /** Appearance Rate (v2) — marka adı geçmese bile içerik varlığı sıklığı. */
    public static double computeAppearanceRate(java.util.List<RawResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return 0;
        }
        return computeContentPresence(responses);
    }

    /** Sentiment skoru (v2) — ham veriden deterministik çıkarılamadığı için nötr varsayılır (≈50). */
    public static double computeSentimentScore(java.util.List<RawResponse> responses) {
        return 50;
    }

    /** Competitive Visibility (v2) — CompetitorContext'in ölçeklenmiş hali. */
    public static double computeCompVisibility(java.util.List<RawResponse> responses, String brandName) {
        return computeCompetitorContext(responses);
    }

    /** Birden çok yanıtın fidelity etiketini birleştirir — en düşük tier'ın etiketi (en muhafazakâr). */
    public static String aggregateFidelity(java.util.List<RawResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "unknown";
        }
        Tier lowestTier = Tier.DIRECTIONAL;
        String lowestLabel = responses.get(0).fidelityLabel();
        for (RawResponse r : responses) {
            if (r.tier().code() < lowestTier.code()) {
                lowestTier = r.tier();
                lowestLabel = r.fidelityLabel();
            }
        }
        return lowestLabel;
    }

    /** Per-motor skor haritası + 0309 §6.2 ağırlıklı ortalama. */
    public static Map<String, Double> computeEngineBreakdown(java.util.List<RawResponse> responses) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (RawResponse resp : responses) {
            String key = resp.engineName();
            double present = 40.0;
            if (resp.content() != null && !resp.content().isBlank()) {
                present = 75.0;
            }
            if (breakdown.containsKey(key)) {
                breakdown.put(key, (breakdown.get(key) + present) / 2);
            } else {
                breakdown.put(key, present);
            }
        }

        if (breakdown.isEmpty()) {
            return breakdown;
        }

        Map<String, Double> weights = engineWeightsActive();
        double weightedSum = 0;
        double weightTotal = 0;
        for (Map.Entry<String, Double> e : breakdown.entrySet()) {
            Double w = weights.get(e.getKey());
            if (w == null) {
                // Bilinmeyen motor: eşit ağırlık — partial yayında weighted_average bilinmeyenleri dışlamaz.
                w = 1.0 / breakdown.size();
            }
            weightedSum += e.getValue() * w;
            weightTotal += w;
        }
        if (weightTotal > 0) {
            breakdown.put("weighted_average", Math.round(weightedSum / weightTotal * 100) / 100.0);
        }
        return breakdown;
    }

    /** Aktif motor ağırlık tablosu (ENGINE_WEIGHTS env override varsa onu, yoksa 0309 §6.2 varsayılanları). */
    public static Map<String, Double> engineWeightsActive() {
        Map<String, Double> override = engineWeightOverride();
        return override != null ? override : ENGINE_WEIGHTS_V130;
    }

    /** ENGINE_WEIGHTS env'i — "perplexity=0.30,chatgpt=0.30,...". Boşsa {@code null}. */
    static Map<String, Double> engineWeightOverride() {
        String raw = System.getenv("ENGINE_WEIGHTS");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(kv[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (v < 0) {
                continue;
            }
            out.put(kv[0].trim(), v);
        }
        return out.isEmpty() ? null : out;
    }

    /** URL'den basit domain çıkarma. */
    public static String extractDomain(String url) {
        if (url == null) {
            return "";
        }
        String u = url;
        if (u.startsWith("https://")) {
            u = u.substring("https://".length());
        } else if (u.startsWith("http://")) {
            u = u.substring("http://".length());
        }
        if (u.startsWith("www.")) {
            u = u.substring("www.".length());
        }
        String[] parts = u.split("/");
        return parts.length > 0 ? parts[0] : "";
    }

    /** 7 bileşenli ağırlıkları intent çarpanlarıyla ölçekler ve 1.0'a normalize eder (varsayılan tablo). */
    public static ComponentWeights applyIntentWeights(ComponentWeights base, String intent) {
        return applyIntentWeightsWithScale(base, intent, DEFAULT_INTENT_SCALE);
    }

    /** applyIntentWeights'in çarpan tablosu parametreli hali (INTENT_WEIGHT_SCALE override desteği). */
    public static ComponentWeights applyIntentWeightsWithScale(ComponentWeights base, String intent, Map<String, double[]> scale) {
        double[] scaleRow = scale == null ? null : scale.get(intent);
        if (scaleRow == null || !base.isV2()) {
            return base;
        }
        ComponentWeights w = new ComponentWeights(
                base.presenceShare() * scaleRow[0],
                base.positionWeight() * scaleRow[1],
                base.sourceShare() * scaleRow[2],
                base.competitorContext() * scaleRow[3],
                base.appearanceRate() * scaleRow[4],
                base.sentiment() * scaleRow[5],
                base.compVisibility() * scaleRow[6]);
        double sum = w.presenceShare() + w.positionWeight() + w.sourceShare() + w.competitorContext()
                + w.appearanceRate() + w.sentiment() + w.compVisibility();
        if (sum <= 0) {
            return base;
        }
        return new ComponentWeights(
                w.presenceShare() / sum,
                w.positionWeight() / sum,
                w.sourceShare() / sum,
                w.competitorContext() / sum,
                w.appearanceRate() / sum,
                w.sentiment() / sum,
                w.compVisibility() / sum);
    }
}