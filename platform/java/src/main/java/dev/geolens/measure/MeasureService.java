package dev.geolens.measure;

import dev.geolens.config.Config;
import dev.geolens.config.ScoreWeightsV2;
import dev.geolens.engine.Adapter;
import dev.geolens.engine.Citation;
import dev.geolens.engine.EngineException;
import dev.geolens.engine.EngineMeta;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Registry;
import dev.geolens.measure.persistence.NoopScoreDao;
import dev.geolens.measure.persistence.ScoreDao;
import dev.geolens.ml.MlClient;
import dev.geolens.ml.PromptClassification;
import dev.geolens.sentiment.ml.CircuitBreaker;
import dev.geolens.util.Ulid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ölçüm/skorlama servisi — Go {@code measure.service} portu.
 * <p>Measure n=3 örnekleme yapar (0421 M-4), CalculateScore saf deterministik skor
 * matematiğini {@link Scoring} üzerinden çalıştırır. ML serving varsa prompt intent'ine
 * göre bileşen ağırlıklarını ölçekler (0421 A3-3); serving yok/hatalı/cooldown'da
 * varsayılan GAVF ağırlıkları kullanılır (0421 M-4 fallback).
 */
public final class MeasureService {

    private final ScoreDao dao;
    private final Registry engines;
    private final Config cfg;
    private final MlClient ml;
    private final CircuitBreaker breaker;

    private final Object cacheLock = new Object();
    private String cachedPrompt;
    private PromptClassification cachedLabels;

    private final Map<String, double[]> intentScale;

    public MeasureService(ScoreDao dao, Registry engines) {
        this(dao, engines, null, null);
    }

    public MeasureService(ScoreDao dao, Registry engines, Config cfg) {
        this(dao, engines, cfg, null);
    }

    public MeasureService(ScoreDao dao, Registry engines, Config cfg, MlClient mlClient) {
        this.dao = dao != null ? dao : new NoopScoreDao();
        this.engines = engines;
        this.cfg = cfg;
        this.ml = mlClient;
        // Sentiment servisiyle aynı tip paylaşılır (Go'da tek ml.CircuitBreaker — tutarlı davranış).
        this.breaker = new CircuitBreaker("measure", CircuitBreaker.DEFAULT_COOLDOWN);
        String raw = cfg != null ? cfg.intentWeightScaleRaw : System.getenv("INTENT_WEIGHT_SCALE");
        Map<String, double[]> scale = Config.parseIntentWeightScaleRaw(raw);
        this.intentScale = scale != null ? scale : Map.of();
    }

    public static MeasureService withoutDatabase(Registry engines) {
        return new MeasureService(new NoopScoreDao(), engines, null, null);
    }

    /** Tüm kayıtlı motorlara n=3 örnekleme yapar ve sonuçları birleştirir. */
    public MeasurementResult measure(MeasurementRequest req) throws MeasureException {
        List<String> engineNames = engines.list();
        if (engineNames.isEmpty()) {
            throw new MeasureException("measure: kayıtlı motor bulunamadı");
        }

        int sampleCount = 3;
        if (cfg != null && cfg.sampleCount > 0) {
            sampleCount = cfg.sampleCount;
        }

        record Sample(String engineName, List<RawResponse> responses) {
        }

        List<Sample> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String name : engineNames) {
                Adapter adapter = engines.get(name);
                if (adapter == null) {
                    continue;
                }
                // Adapter'a tenant/workspace context'ini ekle (thread-safe copy)
                final Adapter contextual = adapter.withContext(req.tenantId(), req.workspaceId());

                List<RawResponse> samples = java.util.Collections.synchronizedList(new ArrayList<>());
                AtomicReference<EngineException> sampleErr = new AtomicReference<>();
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < sampleCount; i++) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            RawResponse resp = contextual.execute(req.promptText());
                            if (resp != null) {
                                samples.add(resp);
                            }
                        } catch (EngineException e) {
                            sampleErr.set(e);
                        }
                    }, executor));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                if (sampleErr.get() != null) {
                    // Örnekleme hatası — motor atlanır (partial yayın)
                    continue;
                }
                if (!samples.isEmpty()) {
                    results.add(new Sample(name, List.copyOf(samples)));
                }
            }
        }

        if (results.isEmpty()) {
            throw new MeasureException("measure: hiçbir motordan geçerli yanıt alınamadı");
        }

        List<RawResponse> allRaw = new ArrayList<>();
        List<Citation> allCitations = new ArrayList<>();
        EngineMeta firstMeta = null;
        for (Sample r : results) {
            allRaw.addAll(r.responses());
            for (RawResponse resp : r.responses()) {
                allCitations.addAll(resp.citations());
            }
            RawResponse first = r.responses().get(0);
            if (first != null) {
                firstMeta = new EngineMeta(r.engineName(), first.fidelityLabel(), first.tier(), 0);
            }
        }

        return new MeasurementResult(allRaw, allCitations, firstMeta,
                req.brandId(), req.brandName(), req.panelId(), req.workspaceId(), req.tenantId(), req.promptText());
    }

    /**
     * Ölçüm sonuçlarından görünürlük skorunu hesaplar. weights boşsa env override
     * (SCORE_WEIGHTS) veya GAVF varsayılanları kullanılır. Partial yayın: bazı motorlar
     * başarısız olsa bile kalan veriyle hesaplama yapılır (G2 determinizm).
     */
    public Score calculateScore(String panelId, List<MeasurementResult> results, ComponentWeights weights)
            throws MeasureException {
        if (weights == null || weights.isEmpty()) {
            weights = effectiveWeights();
        }

        // 0421 A3-3: prompt intent sınıflandırması → VI bileşen ağırlıklarını ölçekle.
        IntentWeightsResult intent = intentWeights(results, weights);
        if (intent.ok()) {
            weights = intent.weights();
        }

        String algorithmVersion = weights.isV2() ? "2.0.0" : "1.0.0";

        List<RawResponse> allResponses = new ArrayList<>();
        for (MeasurementResult r : results) {
            if (r.rawResponses() != null) {
                allResponses.addAll(r.rawResponses());
            }
        }

        if (allResponses.isEmpty()) {
            throw new MeasureException("calculate_score: hesaplama için veri yok");
        }

        MeasurementResult first = results.get(0);
        String brandName = first.brandName();
        String brandID = first.brandId();
        String workspaceID = first.workspaceId();
        String tenantID = first.tenantId();

        double totalScore = Scoring.computeTotalScore(allResponses, brandName, weights);

        Scoring.Components comps = Scoring.computeComponentScores(allResponses, brandName);

        String scoreID = Ulid.generate();
        String calcRunID = Ulid.generate();

        Map<String, Double> componentValues = new LinkedHashMap<>();
        componentValues.put("presence_share", round(comps.presence()));
        componentValues.put("position_weight", round(comps.position()));
        componentValues.put("source_share", round(comps.source()));
        componentValues.put("competitor_context", round(comps.competitor()));
        componentValues.put("total_score", round(totalScore));
        if (weights.isV2()) {
            componentValues.put("appearance_rate", round(comps.appearance()));
            componentValues.put("sentiment", round(comps.sentiment()));
            componentValues.put("comp_visibility", round(comps.compvis()));
        }

        // Calculation run — non-fatal kayıt (deterministik hesaplama kaydı)
        dao.saveCalculationRun(new CalculationRun(calcRunID, panelId, componentValues,
                algorithmVersion, "", Instant.now()), tenantID);

        Map<String, Double> engineBreakdown = Scoring.computeEngineBreakdown(allResponses);

        Scoring.Ci ci = Scoring.computeScoreCI(totalScore, weights);
        Score score = new Score(
                scoreID,
                panelId,
                brandID,
                workspaceID,
                tenantID,
                round(totalScore),
                Math.max(0, ci.low()),
                Math.min(100, ci.high()),
                Scoring.aggregateFidelity(allResponses),
                engineBreakdown,
                algorithmVersion,
                calcRunID,
                Instant.now(),
                Instant.now());

        dao.saveScore(score);
        return score;
    }

    /** Daha önce hesaplanmış bir skoru döner; kayıt yoksa {@code null}. */
    public Score getScoreById(String scoreId) {
        return dao.findById(scoreId);
    }

    /** Varsayılan (env yapılandırmalı) skor ağırlıkları — Go {@code effectiveWeights} portu. */
    ComponentWeights effectiveWeights() {
        if (cfg == null) {
            return ComponentWeights.V2_DEFAULT;
        }
        if ("1.0.0".equals(cfg.scoreAlgorithmVersion)) {
            Config.ScoreWeightsV4 w = cfg.parseScoreWeights();
            if (w != null) {
                return new ComponentWeights(w.presence(), w.position(), w.source(), w.competitor(), 0, 0, 0);
            }
            return ComponentWeights.V1_LEGACY;
        }
        ScoreWeightsV2 v2 = cfg.parseScoreWeightsV2();
        if (v2 != null) {
            return new ComponentWeights(v2.presence(), v2.position(), v2.source(), v2.competitor(),
                    v2.appearance(), v2.sentiment(), v2.compVis());
        }
        Config.ScoreWeightsV4 w = cfg.parseScoreWeights();
        if (w != null) {
            ComponentWeights base = ComponentWeights.V2_DEFAULT;
            return new ComponentWeights(w.presence(), w.position(), w.source(), w.competitor(),
                    base.appearanceRate(), base.sentiment(), base.compVisibility());
        }
        return ComponentWeights.V2_DEFAULT;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    record IntentWeightsResult(ComponentWeights weights, boolean ok) {
        static IntentWeightsResult none() {
            return new IntentWeightsResult(null, false);
        }
    }

    /**
     * Prompt intent'ine göre ağırlıkları ölçekler — Go {@code intentWeights} portu (0421 A3-3).
     * Serving yok/hatalı/cooldown'da veya prompt boşsa ok=false (varsayılan ağırlıklar — M-4).
     * Sonuç per-prompt önbelleklenir: worker aynı prompt'u yeniden sınıflandırmaz.
     */
    IntentWeightsResult intentWeights(List<MeasurementResult> results, ComponentWeights base) {
        if (ml == null) {
            return IntentWeightsResult.none();
        }
        String prompt = results.isEmpty() || results.get(0).promptText() == null
                ? ""
                : results.get(0).promptText().trim();
        if (prompt.isEmpty()) {
            return IntentWeightsResult.none();
        }

        PromptClassification labels;
        synchronized (cacheLock) {
            boolean cached = prompt.equals(cachedPrompt) && cachedLabels != null;
            if (cached) {
                labels = cachedLabels;
            } else {
                if (breaker.inCooldown()) {
                    return IntentWeightsResult.none();
                }
                try {
                    labels = ml.classifyPrompt(prompt);
                } catch (RuntimeException e) {
                    // M-4: serving'deki HER hata (ServingException, boş URL'den IllegalArgumentException
                    // vb.) varsayılan ağırlıklara düşürür — skor akışı serving'e bağımlı olmaz.
                    breaker.fail();
                    return IntentWeightsResult.none();
                }
                breaker.success();
                cachedPrompt = prompt;
                cachedLabels = labels;
            }
        }

        Map<String, double[]> scale = effectiveIntentScale();
        if (!scale.containsKey(labels.intent().label())) {
            return IntentWeightsResult.none();
        }
        return new IntentWeightsResult(Scoring.applyIntentWeightsWithScale(base, labels.intent().label(), scale), true);
    }

    /** Aktif intent çarpan tablosu — INTENT_WEIGHT_SCALE override'ı yoksa varsayılan. */
    Map<String, double[]> effectiveIntentScale() {
        return intentScale.isEmpty() ? Scoring.DEFAULT_INTENT_SCALE : intentScale;
    }
}