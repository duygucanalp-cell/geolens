package dev.geolens.competitive;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Competitive gap analiz motoru — Go {@code competitive/engine.go} portu (FR-D11).
 * <p>Marka ile rakipleri arasında 5 gap türünü (visibility/citation/content/topic/prompt)
 * hesaplar, ağırlıklı competitive score üretir ve snapshot + önerileri kaydeder.
 */
public class CompetitiveEngine {

    private static final Logger LOG = LoggerFactory.getLogger(CompetitiveEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Set<String> GAP_TYPES = Set.of("visibility", "citation", "content", "topic", "prompt");

    private final DSLContext dsl;

    public CompetitiveEngine(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Go {@code AnalyzeAllGaps} karşılığı: marka adını çözer, rakipleri bulur
     * (öncelik {@code brand_competitors}, yoksa aynı workspace'teki diğer markalar — fallback)
     * ve her rakip için 5'li gap analizi + snapshot/öneri kaydı yapar.
     * Rakip yoksa {@code null} döner (Go nil, nil — JSON {@code null}).
     */
    public List<GapSnapshot> analyzeAllGaps(String brandId, String workspaceId, String tenantId) {
        // Marka adı
        String brandName;
        try {
            Record r = dsl.fetchOne("""
                    SELECT name FROM config.brands WHERE id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            if (r == null) {
                throw new IllegalArgumentException("marka bulunamadı");
            }
            brandName = String.valueOf(r.get(0));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("marka bulunamadı: " + e.getMessage(), e);
        }

        // Rakipler — kullanıcı tanımlı yoksa aynı workspace'teki diğer markalar (Go birebir)
        List<Map<String, Object>> compRows;
        try {
            compRows = dsl.fetch("""
                    SELECT b.id, b.name
                    FROM config.brands b
                    JOIN config.brand_competitors bc ON bc.competitor_id = b.id
                    WHERE bc.brand_id = ? AND bc.tenant_id = ? AND b.is_active = true
                    UNION
                    SELECT b.id, b.name
                    FROM config.brands b
                    WHERE b.workspace_id = ? AND b.tenant_id = ? AND b.id != ? AND b.is_active = true
                      AND NOT EXISTS (SELECT 1 FROM config.brand_competitors WHERE brand_id = ? AND tenant_id = ?)
                    """, brandId, tenantId, workspaceId, tenantId, brandId, brandId, tenantId).intoMaps();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("rakip sorgu: " + e.getMessage(), e);
        }

        if (compRows.isEmpty()) {
            return null;
        }

        List<GapSnapshot> snapshots = new ArrayList<>();
        for (Map<String, Object> comp : compRows) {
            GapSnapshot snapshot = analyzeCompetitor(brandId, brandName,
                    str(comp.get("id")), str(comp.get("name")), tenantId);
            snapshots.add(snapshot);
            saveSnapshot(snapshot, tenantId, workspaceId);
        }

        LOG.info("competitive gap analizi tamamlandı brand={} competitors={}", brandId, compRows.size());
        return snapshots;
    }

    /** Go {@code analyzeCompetitor}: tek marka-rakip çifti için 5'li gap analizi. */
    private GapSnapshot analyzeCompetitor(String brandId, String brandName, String compId, String compName,
                                          String tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String periodStart = now.minusDays(30).format(DATE_FMT);
        String periodEnd = now.format(DATE_FMT);

        GapDetail visibility = calcVisibilityGap(brandId, compId, tenantId);
        GapDetail citation = calcCitationGap(brandId, compId, tenantId);
        GapDetail content = calcContentGap(brandId, compId, tenantId);
        GapDetail topic = calcTopicGap(brandId, compId, tenantId);
        GapDetail prompt = calcPromptGap(brandId, compId, tenantId);

        double score = calcCompetitiveScore(visibility, citation, content, topic, prompt);

        return new GapSnapshot(Ulid.generate(), brandId, brandName, compId, compName,
                visibility, citation, content, topic, prompt, score, periodStart, periodEnd, now);
    }

    /** Visibility gap (SOV tabanlı) — Go {@code calcVisibilityGap}. */
    private GapDetail calcVisibilityGap(String brandId, String compId, String tenantId) {
        double brandSOV = avgScore(brandId, tenantId);
        double compSOV = avgScore(compId, tenantId);

        double gap = brandSOV - compSOV;
        double norm = clamp(50.0 + (gap / 100.0) * 50.0);

        return new GapDetail(round2(gap), round2(norm), round2(brandSOV), round2(compSOV), direction(gap, 5));
    }

    /** Citation gap — Go {@code calcCitationGap}. */
    private GapDetail calcCitationGap(String brandId, String compId, String tenantId) {
        int brandCites = sumCitations(brandId, tenantId);
        int compCites = sumCitations(compId, tenantId);
        int totalCites = sumCitationsBoth(brandId, compId, tenantId);

        double brandRate = 0.0;
        double compRate = 0.0;
        if (totalCites > 0) {
            brandRate = (double) brandCites / totalCites * 100.0;
            compRate = (double) compCites / totalCites * 100.0;
        }

        double gap = brandRate - compRate;
        double norm = clamp(50.0 + (gap / 100.0) * 50.0);

        return new GapDetail(round2(gap), round2(norm), round2(brandRate), round2(compRate), direction(gap, 5));
    }

    /** Content gap (kaynak domain çeşitliliği) — Go {@code calcContentGap}. */
    private GapDetail calcContentGap(String brandId, String compId, String tenantId) {
        int brandDomains = distinctDomains(brandId, tenantId);
        int compDomains = distinctDomains(compId, tenantId);

        double gap = brandDomains - compDomains;
        double norm = clamp(50.0 + (gap / 20.0) * 50.0);

        return new GapDetail(gap, round2(norm), brandDomains, compDomains, direction(gap, 2));
    }

    /** Topic gap — Go {@code calcTopicGap}. */
    private GapDetail calcTopicGap(String brandId, String compId, String tenantId) {
        double brandScore = avgScore(brandId, tenantId);
        double compScore = avgScore(compId, tenantId);

        double gap = brandScore - compScore;
        double norm = clamp(50.0 + (gap / 100.0) * 50.0);

        return new GapDetail(round2(gap), round2(norm), round2(brandScore), round2(compScore), direction(gap, 5));
    }

    /** Prompt gap (tamamlanan ölçüm işi oranı proxy) — Go {@code calcPromptGap}. */
    private GapDetail calcPromptGap(String brandId, String compId, String tenantId) {
        int brandJobs = completedJobs(brandId, tenantId);
        int compJobs = completedJobs(compId, tenantId);

        double brandCoverage = brandJobs;
        double compCoverage = compJobs;
        double total = brandJobs + compJobs;
        if (total > 0) {
            brandCoverage = brandJobs / total * 100.0;
            compCoverage = compJobs / total * 100.0;
        }

        double gap = brandCoverage - compCoverage;
        double norm = 50.0 + (gap / 100.0) * 50.0; // Go'da clamp yok

        return new GapDetail(round2(gap), round2(norm), round2(brandCoverage), round2(compCoverage), direction(gap, 5));
    }

    /** Ağırlıklı bileşik skor — Go {@code calcCompetitiveScore}. */
    private double calcCompetitiveScore(GapDetail visibility, GapDetail citation, GapDetail content,
                                        GapDetail topic, GapDetail prompt) {
        double[] weights = {0.30, 0.25, 0.20, 0.15, 0.10};
        GapDetail[] gaps = {visibility, citation, content, topic, prompt};

        double score = 0.0;
        for (int i = 0; i < gaps.length; i++) {
            score += (gaps[i] != null ? gaps[i].normalized() : 50.0) * weights[i];
        }
        return round2(score);
    }

    /** Snapshot'ı kaydeder (upsert) + önerileri üretir — Go {@code saveSnapshot}. */
    private void saveSnapshot(GapSnapshot s, String tenantId, String workspaceId) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("visibility", gapDetailMap(s.visibilityGap()));
        breakdown.put("citation", gapDetailMap(s.citationGap()));
        breakdown.put("content", gapDetailMap(s.contentGap()));
        breakdown.put("topic", gapDetailMap(s.topicGap()));
        breakdown.put("prompt", gapDetailMap(s.promptGap()));
        String breakdownJson;
        try {
            breakdownJson = MAPPER.writeValueAsString(breakdown);
        } catch (Exception e) {
            breakdownJson = "{}";
        }

        String actualGapId = s.id();
        try {
            // Upsert'ten GERÇEK gap_id'yi döndürür: ON CONFLICT DO UPDATE mevcut satırı
            // güncellediğinde gap_id değişmez — öneriler eski id'ye bağlanmalı (FK bütünlüğü).
            Record r = dsl.fetchOne("""
                    INSERT INTO competitive.gap_snapshots
                        (gap_id, brand_id, competitor_id, period_start, period_end,
                         visibility_gap, citation_gap, content_gap, topic_gap, prompt_gap,
                         competitive_score, breakdown, tenant_id, workspace_id, created_at)
                    VALUES (?, ?, ?, ?::date, ?::date, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, now())
                    ON CONFLICT (brand_id, competitor_id, period_start, period_end) DO UPDATE
                    SET visibility_gap = EXCLUDED.visibility_gap,
                        citation_gap = EXCLUDED.citation_gap,
                        content_gap = EXCLUDED.content_gap,
                        topic_gap = EXCLUDED.topic_gap,
                        prompt_gap = EXCLUDED.prompt_gap,
                        competitive_score = EXCLUDED.competitive_score,
                        breakdown = EXCLUDED.breakdown,
                        created_at = now()
                    RETURNING gap_id
                    """, s.id(), s.brandId(), s.competitorId(), s.periodStart(), s.periodEnd(),
                    nullableGap(s.visibilityGap()), nullableGap(s.citationGap()), nullableGap(s.contentGap()),
                    nullableGap(s.topicGap()), nullableGap(s.promptGap()), s.competitiveScore(),
                    breakdownJson, tenantId, workspaceId);
            if (r != null) {
                actualGapId = String.valueOf(r.get(0));
            }
        } catch (RuntimeException e) {
            LOG.warn("gap snapshot kaydetme hatası", e);
        }

        saveRecommendations(actualGapId, tenantId);
    }

    /** Sabit gap bazlı önerileri kaydeder — Go {@code saveRecommendations}. */
    private void saveRecommendations(String gapId, String tenantId) {
        String[][] recs = {
                {"visibility", "medium", "Görünürlük farkı kapatmak için zayıf motorlarda strateji revizyonu yapılmalı",
                        "Visibility gap puanında +5-15 iyileşme", "korelasyonel"},
                {"citation", "high", "Alıntı oranını artırmak için blog ve editoryal içerik üretimi artırılmalı",
                        "Citation gap puanında +10-20 iyileşme", "korelasyonel"},
                {"content", "high", "İçerik çeşitliliği artırılmalı; eksik kaynak türlerine odaklanılmalı",
                        "Content gap puanında +5-10 iyileşme", "deneysel"},
                {"topic", "medium", "Zayıf konularda içerik güçlendirilmeli; topic cluster stratejisi uygulanmalı",
                        "Topic gap puanında +10-25 iyileşme", "denenebilir"},
                {"prompt", "medium", "Karşılaştırma ve öneri prompt kapsamı artırılmalı",
                        "Prompt gap puanında +5-15 iyileşme", "denenebilir"},
        };

        for (String[] r : recs) {
            try {
                dsl.execute("""
                        INSERT INTO competitive.gap_recommendations
                            (recommendation_id, gap_id, gap_type, priority, description, impact, kanit_derecesi, related_fr, tenant_id, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'FR-D11', ?, now())
                        """, Ulid.generate(), gapId, r[0], r[1], r[2], r[3], r[4], tenantId);
            } catch (RuntimeException e) {
                LOG.warn("gap recommendation kaydetme hatası", e);
            }
        }
    }

    /**
     * Go {@code GetGapDetail} karşılığı: belirli bir gap türünün son snapshot değerini döndürür.
     * Değer NULL ise null; sorgu hatasında {@code IllegalArgumentException}.
     */
    public GapDetail getGapDetail(String brandId, String competitorId, String gapType, String tenantId) {
        if (!GAP_TYPES.contains(gapType)) {
            throw new IllegalArgumentException("geçersiz gap türü: " + gapType);
        }

        Record r;
        try {
            r = dsl.fetchOne("""
                    SELECT %s_gap FROM competitive.gap_snapshots
                    WHERE brand_id = ? AND competitor_id = ? AND tenant_id = ?
                    ORDER BY created_at DESC LIMIT 1
                    """.formatted(gapType), brandId, competitorId, tenantId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("gap sorgu: " + e.getMessage(), e);
        }

        if (r == null || r.get(0) == null) {
            return null;
        }

        double gapVal = num(r.get(0));
        double normalized = 50.0 + (gapVal / 100.0) * 50.0;
        return new GapDetail(gapVal, normalized, 0, 0, direction(gapVal, 5));
    }

    // ---------- yardımcılar ----------

    private double avgScore(String brandId, String tenantId) {
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(AVG(value), 0) FROM measure.scores
                    WHERE brand_id = ? AND tenant_id = ? AND freshness_at > now() - interval '30 days'
                    """, brandId, tenantId);
            return r == null ? 0 : num(r.get(0));
        } catch (RuntimeException e) {
            LOG.debug("competitive gap: skor okuma hatası", e);
            return 0;
        }
    }

    private int sumCitations(String brandId, String tenantId) {
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(SUM(citation_count), 0) FROM measure.citations
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            return r == null ? 0 : numInt(r.get(0));
        } catch (RuntimeException e) {
            LOG.debug("competitive gap: alıntı okuma hatası", e);
            return 0;
        }
    }

    private int sumCitationsBoth(String brandId, String compId, String tenantId) {
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(SUM(citation_count), 0) FROM measure.citations
                    WHERE (brand_id = ? OR brand_id = ?) AND tenant_id = ?
                    """, brandId, compId, tenantId);
            return r == null ? 0 : numInt(r.get(0));
        } catch (RuntimeException e) {
            LOG.debug("competitive gap: toplam alıntı okuma hatası", e);
            return 0;
        }
    }

    private int distinctDomains(String brandId, String tenantId) {
        try {
            Record r = dsl.fetchOne("""
                    SELECT COUNT(DISTINCT source_domain) FROM measure.citations
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            return r == null ? 0 : numInt(r.get(0));
        } catch (RuntimeException e) {
            LOG.debug("competitive gap: kaynak alan okuma hatası", e);
            return 0;
        }
    }

    private int completedJobs(String brandId, String tenantId) {
        try {
            Record r = dsl.fetchOne("""
                    SELECT COUNT(*) FROM measure.measurement_jobs
                    WHERE brand_id = ? AND tenant_id = ? AND status = 'completed'
                    """, brandId, tenantId);
            return r == null ? 0 : numInt(r.get(0));
        } catch (RuntimeException e) {
            LOG.debug("competitive gap: iş sayısı okuma hatası", e);
            return 0;
        }
    }

    private static Map<String, Object> gapDetailMap(GapDetail d) {
        if (d == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gap_value", d.gapValue());
        m.put("normalized", d.normalized());
        m.put("brand_value", d.brandValue());
        m.put("competitor_value", d.competitorValue());
        m.put("direction", d.direction());
        return m;
    }

    private static Double nullableGap(GapDetail d) {
        return d == null ? null : d.gapValue();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String direction(double gap, double threshold) {
        if (gap > threshold) {
            return "brand_ahead";
        }
        if (gap < -threshold) {
            return "competitor_ahead";
        }
        return "equal";
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static int numInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
