package dev.geolens.measure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.measure.CalculationRun;
import dev.geolens.measure.Score;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * PostgreSQL JDBC implementasyonu — Go {@code measure/service.go} sorgularının karşılığı.
 * <p>RLS (ADR-004): her işlem, Go middleware'i gibi {@code set_config('app.tenant_id', ?, true)}
 * ile transaction-scoped tenant bağlamında çalışır.
 */
@Repository
public class JdbcScoreDao implements ScoreDao {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcScoreDao(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private static void setTenant(JdbcTemplate jdbc, String tenantId) {
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
    public void saveCalculationRun(CalculationRun run, String tenantId) {
        if (tenantId == null) {
            return;
        }
        runInTenant(tenantId, () -> jdbc.update("""
                INSERT INTO measure.calculation_runs (id, panel_id, tenant_id, algorithm_version, component_values, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, now())
                """,
                run.id(), run.panelId(), tenantId, run.algorithmVersion(),
                json(run.scoreComponents())));
    }

    @Override
    public void saveScore(Score score) {
        if (score.tenantId() == null) {
            return;
        }
        runInTenant(score.tenantId(), () -> jdbc.update("""
                INSERT INTO measure.scores (id, panel_id, brand_id, workspace_id, tenant_id, value, ci_low, ci_high,
                    fidelity_label, engine_breakdown, panel_version, calculation_run_id, freshness_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, now(), now())
                """,
                score.id(), score.panelId(), score.brandId(), score.workspaceId(), score.tenantId(),
                score.value(), score.ciLow(), score.ciHigh(), score.fidelityLabel(),
                json(score.engineBreakdown()), score.panelVersion(), score.calculationRunId()));
    }

    @Override
    public Score findById(String scoreId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT id, COALESCE(panel_id, ''), COALESCE(brand_id, ''), COALESCE(workspace_id, ''),
                           COALESCE(tenant_id, ''), value, COALESCE(ci_low, 0), COALESCE(ci_high, 0), fidelity_label,
                           COALESCE(engine_breakdown::text, '{}'), panel_version, COALESCE(calculation_run_id, ''),
                           freshness_at, created_at
                    FROM measure.scores
                    WHERE id = ?
                    """, scoreId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }

        Map<String, Double> breakdown = parseBreakdown((String) row.get("engine_breakdown"));
        return new Score(
                (String) row.get("id"),
                (String) row.get("panel_id"),
                (String) row.get("brand_id"),
                (String) row.get("workspace_id"),
                (String) row.get("tenant_id"),
                ((Number) row.get("value")).doubleValue(),
                ((Number) row.get("ci_low")).doubleValue(),
                ((Number) row.get("ci_high")).doubleValue(),
                (String) row.get("fidelity_label"),
                breakdown,
                (String) row.get("panel_version"),
                (String) row.get("calculation_run_id"),
                ((Timestamp) row.get("freshness_at")).toInstant(),
                ((Timestamp) row.get("created_at")).toInstant());
    }

    private static String json(Map<String, Double> map) {
        try {
            String raw = MAPPER.writeValueAsString(map == null ? Map.of() : map);
            return "null".equals(raw) ? "{}" : raw;
        } catch (JsonProcessingException e) {
            return "{}";
        }
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