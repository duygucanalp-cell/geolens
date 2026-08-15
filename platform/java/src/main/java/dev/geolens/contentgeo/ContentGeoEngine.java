package dev.geolens.contentgeo;

import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Content GEO analiz motoru — Go {@code contentgeo/engine.go} portu (FR-E5, FR-E6).
 * <p>Citation kaynaklarından content gap'leri belirler, kaydeder; content hub skoru
 * hesaplar (topic kapsamı + kaynak çeşitliliği + otorite skoru).
 */
public class ContentGeoEngine {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGeoEngine.class);

    private final DSLContext dsl;

    public ContentGeoEngine(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Go {@code AnalyzeContentGap} karşılığı: markanın citation kaynaklarını çeker,
     * 5 içerik türünde gap'leri belirler ve {@code content.gap_analyses}'e kaydeder.
     */
    public List<ContentGapResult> analyzeContentGap(String brandId, String workspaceId, String tenantId) {
        // Citation kaynakları — DISTINCT ON (source_domain), son alıntıya göre
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT DISTINCT ON (c.source_domain) c.source_domain, c.citation_count, c.last_cited_at
                    FROM measure.citations c
                    WHERE c.tenant_id = ? AND c.brand_id = ?
                    ORDER BY c.source_domain, c.last_cited_at DESC
                    LIMIT 100
                    """, tenantId, brandId).intoMaps();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("citation sorgu: " + e.getMessage(), e);
        }

        List<int[]> sources = new ArrayList<>(); // [citation_count] — domain'e gerek yok, yalnızca sayılar
        for (Map<String, Object> r : rows) {
            sources.add(new int[]{num(r.get("citation_count"))});
        }

        List<ContentGapResult> gaps = identifyGaps(sources, rows);

        // Kaydet + priority belirle (Go birebir)
        for (ContentGapResult g : gaps) {
            ContentGapResult saved = new ContentGapResult(
                    Ulid.generate(), brandId, g.gapType(), g.gapScore(),
                    g.description(), g.recommendation(), priorityOf(g.gapScore()),
                    OffsetDateTime.now(ZoneOffset.UTC));
            try {
                dsl.execute("""
                        INSERT INTO content.gap_analyses
                            (id, brand_id, gap_type, gap_score, description, recommendation, priority, tenant_id, workspace_id, analyzed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, saved.id(), brandId, saved.gapType(), saved.gapScore(),
                        saved.description(), saved.recommendation(), saved.priority(),
                        tenantId, workspaceId, saved.analyzedAt());
            } catch (RuntimeException e) {
                LOG.warn("content gap kaydetme hatası", e);
            }
        }

        LOG.info("content gap analizi tamamlandı brand={} gaps={}", brandId, gaps.size());
        return gaps;
    }

    /** Go {@code identifyGaps}: kaynak domain türlerinden gap'leri belirler. */
    private List<ContentGapResult> identifyGaps(List<int[]> sources, List<Map<String, Object>> rows) {
        List<ContentGapResult> gaps = new ArrayList<>();

        String[][] domainTypes = {
                {"blog", "Blog/Makale"},
                {"product", "Ürün sayfası"},
                {"faq", "FAQ/SSS"},
                {"news", "Haber/Basın"},
                {"category", "Kategori sayfası"},
        };

        for (String[] dt : domainTypes) {
            int count = 0;
            for (int i = 0; i < rows.size(); i++) {
                String domain = str(rows.get(i).get("source_domain"));
                if (domain.contains(dt[0])) {
                    count += sources.get(i)[0];
                }
            }

            double gap = 1.0 - (double) count / 100.0;
            if (gap < 0) {
                gap = 0;
            }

            if (gap > 0.5) {
                gaps.add(new ContentGapResult(
                        null, null, dt[0], gap,
                        dt[1] + " türü içerik eksik veya yetersiz alıntılanıyor",
                        dt[1] + " içerik sayısı ve kalitesi artırılmalı",
                        null, null));
            }
        }

        // Spesifik gap yoksa genel öneri (Go birebir)
        if (gaps.isEmpty()) {
            gaps.add(new ContentGapResult(
                    null, null, "general", 0.3,
                    "Genel içerik kapsamı yeterli, küçük iyileştirmeler mümkün",
                    "Mevcut içerik stratejisi korunmalı, düzenli güncellemeler yapılmalı",
                    null, null));
        }

        return gaps;
    }

    /**
     * Go {@code GetContentHubScore} karşılığı: topic kapsamı + kaynak çeşitliliği +
     * otorite skorundan hub skoru ve harf notu üretir. Sorgu hataları yok sayılır (Go birebir).
     */
    public ContentHubScore getContentHubScore(String brandId, String workspaceId, String tenantId) {
        int topicCount = 0;
        double avgAuthority = 0;
        try {
            Record r = dsl.fetchOne("""
                    SELECT COUNT(DISTINCT gap_type), COALESCE(AVG(gap_score), 0)
                    FROM content.gap_analyses
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            if (r != null) {
                topicCount = num(r.get(0));
                avgAuthority = dbl(r.get(1));
            }
        } catch (RuntimeException e) {
            LOG.warn("content hub sorgu hatası", e);
        }

        int sourceCount = 0;
        try {
            Record r = dsl.fetchOne("""
                    SELECT COUNT(DISTINCT source_domain)
                    FROM measure.citations
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            if (r != null) {
                sourceCount = num(r.get(0));
            }
        } catch (RuntimeException e) {
            LOG.warn("kaynak çeşitlilik sorgu hatası", e);
        }

        double topicCoverage = (double) topicCount / 7.0 * 100.0; // 7 olası tür
        if (topicCoverage > 100) {
            topicCoverage = 100;
        }

        double sourceDiversity = (double) sourceCount / 10.0 * 100.0;
        if (sourceDiversity > 100) {
            sourceDiversity = 100;
        }

        double oppGap = 100.0 - (topicCoverage * 0.4 + sourceDiversity * 0.3 + avgAuthority * 0.3);

        double overall = 100.0 - oppGap;
        if (overall < 0) {
            overall = 0;
        }

        String grade = "F";
        if (overall >= 90) {
            grade = "A";
        } else if (overall >= 75) {
            grade = "B";
        } else if (overall >= 60) {
            grade = "C";
        } else if (overall >= 40) {
            grade = "D";
        }

        return new ContentHubScore(brandId, overall, topicCoverage, sourceDiversity,
                avgAuthority * 100, oppGap, grade);
    }

    /** Gap skoruna göre priority — Go {@code AnalyzeContentGap} içindeki mantık. */
    private static String priorityOf(double gapScore) {
        if (gapScore > 0.7) {
            return "high";
        }
        if (gapScore < 0.3) {
            return "low";
        }
        return "medium";
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static double dbl(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
