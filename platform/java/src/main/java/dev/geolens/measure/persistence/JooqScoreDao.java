package dev.geolens.measure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.measure.CalculationRun;
import dev.geolens.measure.Score;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.geolens.jooq.measure.tables.CalculationRuns.CALCULATION_RUNS;
import static dev.geolens.jooq.measure.tables.Scores.SCORES;

/**
 * PostgreSQL JOOQ implementasyonu — ADR-014: type-safe SQL, RLS dostu.
 * <p>RLS (ADR-004): her işlem, önceki JDBC sürümü gibi {@code set_config('app.tenant_id', ?, true)}
 * ile transaction-scoped tenant bağlamında çalışır. {@code DSLContext}, Spring transaction'ına
 * bağlı bağlantıyı kullandığından {@code TransactionTemplate} içindeki set_config tüm sorguları kapsar.
 */
@Repository
public class JooqScoreDao implements ScoreDao {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public JooqScoreDao(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    private static void setTenant(DSLContext dsl, String tenantId) {
        dsl.fetch("SELECT set_config('app.tenant_id', ?, true)", tenantId);
    }

    private <T> T inTenant(String tenantId, Supplier<T> work) {
        return tx.execute(status -> {
            setTenant(dsl, tenantId);
            return work.get();
        });
    }

    private void runInTenant(String tenantId, Runnable work) {
        tx.executeWithoutResult(status -> {
            setTenant(dsl, tenantId);
            work.run();
        });
    }

    @Override
    public void saveCalculationRun(CalculationRun run, String tenantId) {
        if (tenantId == null) {
            return;
        }
        // created_at DB default'undan gelir (now()); component_values JSONB → ::jsonb cast
        runInTenant(tenantId, () -> dsl.insertInto(CALCULATION_RUNS)
                .columns(CALCULATION_RUNS.ID, CALCULATION_RUNS.PANEL_ID, CALCULATION_RUNS.TENANT_ID,
                        CALCULATION_RUNS.ALGORITHM_VERSION, CALCULATION_RUNS.COMPONENT_VALUES)
                .values(DSL.val(run.id()), DSL.val(run.panelId()), DSL.val(tenantId),
                        DSL.val(run.algorithmVersion()), jsonb(run.scoreComponents()))
                .execute());
    }

    @Override
    public void saveScore(Score score) {
        if (score.tenantId() == null) {
            return;
        }
        // created_at DB default'undan gelir (now()); engine_breakdown JSONB → ::jsonb cast
        runInTenant(score.tenantId(), () -> dsl.insertInto(SCORES)
                .columns(SCORES.ID, SCORES.PANEL_ID, SCORES.BRAND_ID, SCORES.WORKSPACE_ID, SCORES.TENANT_ID,
                        SCORES.VALUE, SCORES.CI_LOW, SCORES.CI_HIGH, SCORES.FIDELITY_LABEL,
                        SCORES.ENGINE_BREAKDOWN, SCORES.PANEL_VERSION, SCORES.CALCULATION_RUN_ID,
                        SCORES.FRESHNESS_AT)
                .values(DSL.val(score.id()), DSL.val(score.panelId()), DSL.val(score.brandId()),
                        DSL.val(score.workspaceId()), DSL.val(score.tenantId()),
                        DSL.val(score.value()), DSL.val(score.ciLow()), DSL.val(score.ciHigh()),
                        DSL.val(score.fidelityLabel()),
                        jsonb(score.engineBreakdown()), DSL.val(score.panelVersion()),
                        DSL.val(score.calculationRunId()), DSL.val(toOffsetDateTime(score.freshnessAt())))
                .execute());
    }

    @Override
    public Score findById(String scoreId) {
        Optional<? extends Record> maybe = dsl.select(
                        SCORES.ID,
                        DSL.coalesce(SCORES.PANEL_ID, ""),
                        DSL.coalesce(SCORES.BRAND_ID, ""),
                        DSL.coalesce(SCORES.WORKSPACE_ID, ""),
                        DSL.coalesce(SCORES.TENANT_ID, ""),
                        SCORES.VALUE,
                        DSL.coalesce(SCORES.CI_LOW, 0.0),
                        DSL.coalesce(SCORES.CI_HIGH, 0.0),
                        SCORES.FIDELITY_LABEL,
                        SCORES.ENGINE_BREAKDOWN,
                        SCORES.PANEL_VERSION,
                        DSL.coalesce(SCORES.CALCULATION_RUN_ID, ""),
                        SCORES.FRESHNESS_AT,
                        SCORES.CREATED_AT)
                .from(SCORES)
                .where(SCORES.ID.eq(scoreId))
                .fetchOptional();
        if (maybe.isEmpty()) {
            return null;
        }
        Record row = maybe.get();
        Map<String, Double> breakdown = parseBreakdown(jsonData(row.get(SCORES.ENGINE_BREAKDOWN)));
        return new Score(
                row.get(SCORES.ID),
                row.get(SCORES.PANEL_ID),
                row.get(SCORES.BRAND_ID),
                row.get(SCORES.WORKSPACE_ID),
                row.get(SCORES.TENANT_ID),
                row.get(SCORES.VALUE),
                row.get(SCORES.CI_LOW),
                row.get(SCORES.CI_HIGH),
                row.get(SCORES.FIDELITY_LABEL),
                breakdown,
                row.get(SCORES.PANEL_VERSION),
                row.get(SCORES.CALCULATION_RUN_ID),
                toInstant(row.get(SCORES.FRESHNESS_AT)),
                toInstant(row.get(SCORES.CREATED_AT)));
    }

    /** JSONB kolon değeri: jOOQ kolonu JSON tipinde ürettiğinden (H2'de jsonb yok)
     *  PostgreSQL'de birebir eski SQL gibi {@code ?::jsonb} cast'i uygulanır. */
    private static Field<JSON> jsonb(Map<String, Double> map) {
        String raw = json(map);
        return DSL.field("{0}::jsonb", JSON.class, DSL.val(JSON.valueOf(raw)));
    }

    private static String jsonData(JSON json) {
        return json == null ? null : json.data();
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static java.time.Instant toInstant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
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
