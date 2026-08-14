package dev.geolens.recommendation.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Brand;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.ScoreSnapshot;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * PostgreSQL JDBC implementasyonu — Go {@code service} sorgularının birebir karşılığı.
 * <p>RLS (ADR-004): her işlem, Go middleware'i gibi {@code set_config('app.tenant_id', ?, true)}
 * ile transaction-scoped tenant bağlamında çalışır. Tüm sorgularda tenant_id/workspace_id
 * WHERE kısıtı da açıkça korunur.
 */
@Repository
public class JdbcRecommendationDao implements RecommendationDao {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcRecommendationDao(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private static void setTenant(JdbcTemplate jdbc, String tenantId) {
        // set_config bir satır döndürür; executeUpdate bunu reddeder, execute() kullanılır.
        jdbc.execute("SELECT set_config('app.tenant_id', ?, true)",
                (PreparedStatementCallback<Void>) ps -> {
                    ps.setString(1, tenantId);
                    ps.execute();
                    return null;
                });
    }

    private <T> T inTenant(String tenantId, Supplier<T> work) {
        return tx.execute(status -> {
            setTenant(jdbc, tenantId);
            return work.get();
        });
    }

    private void runInTenant(String tenantId, Runnable work) {
        tx.executeWithoutResult(status -> {
            setTenant(jdbc, tenantId);
            work.run();
        });
    }

    @Override
    public ScoreSnapshot loadScore(String brandId, String workspaceId, String tenantId) {
        return inTenant(tenantId, () -> {
            Map<String, Object> row;
            try {
                row = jdbc.queryForMap("""
                        SELECT value, freshness_at, COALESCE(engine_breakdown::text, '{}') AS breakdown
                        FROM measure.scores
                        WHERE brand_id = ? AND workspace_id = ? AND tenant_id = ?
                        ORDER BY freshness_at DESC LIMIT 1
                        """, brandId, workspaceId, tenantId);
            } catch (EmptyResultDataAccessException e) {
                return ScoreSnapshot.empty();
            }

            double value = ((Number) row.get("value")).doubleValue();
            Instant freshnessAt = ((Timestamp) row.get("freshness_at")).toInstant();
            Map<String, Double> breakdown = parseBreakdown((String) row.get("breakdown"));

            double prevValue = 0;
            Instant prevAt = null;
            try {
                Map<String, Object> prev = jdbc.queryForMap("""
                        SELECT value, freshness_at FROM measure.scores
                        WHERE brand_id = ? AND workspace_id = ? AND tenant_id = ?
                        ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
                        """, brandId, workspaceId, tenantId);
                prevValue = ((Number) prev.get("value")).doubleValue();
                prevAt = ((Timestamp) prev.get("freshness_at")).toInstant();
            } catch (EmptyResultDataAccessException ignored) {
                // önceki skor yok
            }

            return new ScoreSnapshot(value, prevValue, freshnessAt, prevAt, breakdown);
        });
    }

    @Override
    public AuditSnapshot loadAudit(String brandId, String tenantId) {
        return inTenant(tenantId, () -> {
            Map<String, Object> row;
            try {
                row = jdbc.queryForMap("""
                        SELECT
                            COALESCE((robots_txt->>'disallowed_all')::boolean, false) AS robots_disallowed,
                            COALESCE((ssr->>'has_structured_data')::boolean, false)    AS has_structured,
                            COALESCE((bot_access->>'accessible')::boolean, false)      AS bot_accessible,
                            COALESCE(overall_score, 0)                                 AS overall_score
                        FROM governance.audit_results
                        WHERE brand_id = ? AND tenant_id = ?
                        ORDER BY created_at DESC LIMIT 1
                        """, brandId, tenantId);
            } catch (EmptyResultDataAccessException e) {
                return AuditSnapshot.empty();
            }

            boolean robotsDisallowed = (Boolean) row.get("robots_disallowed");
            boolean hasStructured = (Boolean) row.get("has_structured");
            boolean botAccessible = (Boolean) row.get("bot_accessible");
            double overall = ((Number) row.get("overall_score")).doubleValue();
            return new AuditSnapshot(true, overall, robotsDisallowed, hasStructured, botAccessible);
        });
    }

    @Override
    public List<Brand> listActiveBrands(String workspaceId, String tenantId) {
        return inTenant(tenantId, () -> jdbc.query("""
                SELECT id, name FROM config.brands
                WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                """, (rs, rowNum) -> new Brand(rs.getString("id"), rs.getString("name")),
                workspaceId, tenantId));
    }

    @Override
    public void save(Recommendation rec) {
        runInTenant(rec.tenantId(), () -> jdbc.update("""
                INSERT INTO recommendation.results
                    (id, brand_id, workspace_id, tenant_id, category, severity, evidence,
                     title, detail, action_url, confidence, applied, dismissed, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                rec.id(), rec.brandId(), rec.workspaceId(), rec.tenantId(),
                rec.category() == null ? null : rec.category().json(),
                rec.severity() == null ? null : rec.severity().json(),
                rec.evidence() == null ? null : rec.evidence().json(),
                rec.title(), rec.detail(), rec.actionUrl(), rec.score(),
                rec.applied(), rec.dismissed(), Timestamp.from(rec.createdAt())));
    }

    @Override
    public void markApplied(String id, String tenantId, String workspaceId) {
        runInTenant(tenantId, () -> {
            Timestamp now = Timestamp.from(Instant.now());
            int rows = jdbc.update("""
                    UPDATE recommendation.results
                    SET applied = true, applied_at = ?, updated_at = ?
                    WHERE id = ? AND tenant_id = ? AND workspace_id = ?
                    """, now, now, id, tenantId, workspaceId);
            if (rows == 0) {
                throw new RecommendationNotFoundException(
                        "recommendation: kayıt bulunamadı veya bu çalışma alanına ait değil");
            }
        });
    }

    @Override
    public void markDismissed(String id, String tenantId, String workspaceId) {
        runInTenant(tenantId, () -> {
            Timestamp now = Timestamp.from(Instant.now());
            int rows = jdbc.update("""
                    UPDATE recommendation.results
                    SET dismissed = true, dismissed_at = ?, updated_at = ?
                    WHERE id = ? AND tenant_id = ? AND workspace_id = ?
                    """, now, now, id, tenantId, workspaceId);
            if (rows == 0) {
                throw new RecommendationNotFoundException(
                        "recommendation: kayıt bulunamadı veya bu çalışma alanına ait değil");
            }
        });
    }

    @Override
    public AppliedRecommendation loadApplied(String id, String workspaceId, String tenantId) {
        return inTenant(tenantId, () -> {
            Map<String, Object> row;
            try {
                row = jdbc.queryForMap("""
                        SELECT brand_id, applied_at FROM recommendation.results
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND applied = true
                        """, id, workspaceId, tenantId);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
            String brandId = (String) row.get("brand_id");
            Instant appliedAt = row.get("applied_at") == null ? null : ((Timestamp) row.get("applied_at")).toInstant();
            return AppliedRecommendation.of(brandId, appliedAt);
        });
    }

    @Override
    public ScoreAt loadScoreAt(String brandId, String workspaceId, String tenantId, Instant at, boolean before) {
        return inTenant(tenantId, () -> {
            Map<String, Object> row;
            try {
                if (before) {
                    row = jdbc.queryForMap("""
                            SELECT value, COALESCE(fidelity_label, 'yok') AS fidelity_label, freshness_at
                            FROM measure.scores
                            WHERE brand_id = ? AND workspace_id = ? AND tenant_id = ?
                                AND freshness_at <= ?
                            ORDER BY freshness_at DESC LIMIT 1
                            """, brandId, workspaceId, tenantId, Timestamp.from(at));
                } else {
                    row = jdbc.queryForMap("""
                            SELECT value, COALESCE(fidelity_label, 'yok') AS fidelity_label, freshness_at
                            FROM measure.scores
                            WHERE brand_id = ? AND workspace_id = ? AND tenant_id = ?
                                AND freshness_at > ?
                            ORDER BY freshness_at ASC LIMIT 1
                            """, brandId, workspaceId, tenantId, Timestamp.from(at));
                }
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
            double value = ((Number) row.get("value")).doubleValue();
            String fidelity = (String) row.get("fidelity_label");
            Instant measuredAt = ((Timestamp) row.get("freshness_at")).toInstant();
            return new ScoreAt(value, fidelity, measuredAt);
        });
    }

    private static Map<String, Double> parseBreakdown(String text) {
        if (text == null || text.isBlank() || "{}".equals(text) || "null".equals(text)) {
            return null;
        }
        try {
            Map<String, Object> raw = MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {
            });
            if (raw.isEmpty()) {
                return null;
            }
            Map<String, Double> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> out.put(k, v instanceof Number n ? n.doubleValue() : 0.0));
            return out;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}