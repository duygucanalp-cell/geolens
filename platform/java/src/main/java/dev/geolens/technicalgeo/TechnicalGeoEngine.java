package dev.geolens.technicalgeo;

import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Technical GEO analiz motoru — Go {@code technicalgeo/engine.go} portu (FR-B6/B7/E7).
 * <p>Bilinen LLM botlarının erişim durumunu robots.txt denetim verisiyle analiz eder,
 * Schema.org kullanımını denetler ve genel skor + harf notu üretir.
 */
public class TechnicalGeoEngine {

    private static final List<String> BOTS = List.of(
            "GPTBot", "MistralAI", "Google-Extended", "PerplexityBot",
            "Claude-Web", "CCBot", "FacebookBot", "Bytespider", "Applebot");

    private static final List<String> SCHEMA_TYPES = List.of(
            "Product", "FAQ", "Organization", "Article", "BreadcrumbList",
            "HowTo", "LocalBusiness", "Review", "Service", "SoftwareApplication");

    private final DSLContext dsl;

    public TechnicalGeoEngine(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Go {@code AnalyzeBotAccess} karşılığı: her bilinen bot için erişim durumunu
     * değerlendirir, kaydeder; özet olarak yalnızca brand/url döner.
     */
    public BotAnalysisResult analyzeBotAccess(String brandId, String url, String workspaceId, String tenantId) {
        if (url == null || url.isBlank()) {
            Record rec = dsl.fetchOne("SELECT website_url FROM config.brands WHERE id = ? AND tenant_id = ?",
                    brandId, tenantId);
            if (rec == null || rec.get(0) == null) {
                throw new IllegalArgumentException("marka URL bulunamadı");
            }
            url = str(rec.get(0));
        }

        for (String bot : BOTS) {
            boolean isBlocked = false;
            String rule = "Allow";

            // robots.txt bilgisi için denetim sonucu
            String robotsTxt = "";
            try {
                Record r = dsl.fetchOne("""
                        SELECT COALESCE(a.details->>'robots_txt', '')
                        FROM governance.audit_results a
                        WHERE a.brand_id = ? AND a.tenant_id = ?
                        ORDER BY a.created_at DESC LIMIT 1
                        """, brandId, tenantId);
                if (r != null && r.get(0) != null) {
                    robotsTxt = str(r.get(0));
                }
            } catch (RuntimeException e) {
                // Go'da hata yok sayılır (robotsTxt boş kalır)
            }

            if (!robotsTxt.isEmpty()) {
                isBlocked = robotsTxt.contains("Disallow: /") && robotsTxt.contains(bot);
                if (isBlocked) {
                    rule = "Disallow";
                }
            }

            double ges = isBlocked ? 0.0 : 100.0;

            try {
                dsl.execute("""
                        INSERT INTO technical.bot_analyses
                            (id, brand_id, bot_name, url, is_blocked, robots_txt_rule, ges_score, tenant_id, workspace_id, analyzed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Ulid.generate(), brandId, bot, url, isBlocked, rule, ges,
                        tenantId, workspaceId, OffsetDateTime.now());
            } catch (RuntimeException e) {
                // Go'da warn loglanıp geçilir
            }
        }

        return new BotAnalysisResult(null, brandId, null, url, false, null, 0,
                OffsetDateTime.now().toString());
    }

    /**
     * Go {@code AnalyzeSchema} karşılığı: 10 schema tipini denetler, kaydeder;
     * özet olarak yalnızca brand döner.
     */
    public SchemaAnalysisResult analyzeSchema(String brandId, String workspaceId, String tenantId) {
        for (String st : SCHEMA_TYPES) {
            boolean isPresent = false;
            double score = 0.0;

            String details = "";
            try {
                Record r = dsl.fetchOne("""
                        SELECT COALESCE(a.details->>'structured_data', '')
                        FROM governance.audit_results a
                        WHERE a.brand_id = ? AND a.tenant_id = ?
                        ORDER BY a.created_at DESC LIMIT 1
                        """, brandId, tenantId);
                if (r != null && r.get(0) != null) {
                    details = str(r.get(0));
                }
            } catch (RuntimeException e) {
                // Go'da hata yok sayılır
            }

            if (!details.isEmpty()) {
                isPresent = details.contains(st);
                if (isPresent) {
                    score = 100.0;
                }
            }

            String rec = "";
            if (!isPresent) {
                rec = switch (st) {
                    case "Product" -> "Ürün sayfalarına Product schema eklenmeli";
                    case "FAQ" -> "SSS sayfasına FAQ schema eklenmeli";
                    case "Organization" -> "Kurumsal bilgilere Organization schema eklenmeli";
                    case "Article" -> "Blog içeriklerine Article schema eklenmeli";
                    default -> st + " schema tipi değerlendirilmeli";
                };
            }

            try {
                dsl.execute("""
                        INSERT INTO technical.schema_analyses
                            (id, brand_id, schema_type, is_present, schema_score, recommendation, tenant_id, workspace_id, analyzed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Ulid.generate(), brandId, st, isPresent, score, rec,
                        tenantId, workspaceId, OffsetDateTime.now());
            } catch (RuntimeException e) {
                // Go'da warn loglanıp geçilir
            }
        }

        return new SchemaAnalysisResult(null, brandId, null, false, 0, null,
                OffsetDateTime.now().toString());
    }

    /**
     * Go {@code GetScore} karşılığı: bot + schema ortalama skorlarından genel skor
     * ve harf notu (A-F) üretir.
     */
    public TechnicalGeoScore getScore(String brandId, String workspaceId, String tenantId) {
        double botScore = 0, schemaScore = 0;
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(AVG(ges_score), 0), COUNT(*) FROM technical.bot_analyses
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            if (r != null) {
                botScore = num(r.get(0));
            }
        } catch (RuntimeException e) {
            // Go'da warn loglanıp 0 kalır
        }

        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(AVG(schema_score), 0), COUNT(*) FROM technical.schema_analyses
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);
            if (r != null) {
                schemaScore = num(r.get(0));
            }
        } catch (RuntimeException e) {
            // Go'da warn loglanıp 0 kalır
        }

        double overall = botScore * 0.4 + schemaScore * 0.4;

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

        return new TechnicalGeoScore(brandId, overall, botScore, schemaScore, 0, grade);
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }
}
