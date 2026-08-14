package dev.geolens.recommendation.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Brand;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.ScoreSnapshot;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.geolens.jooq.config.tables.Brands.BRANDS;
import static dev.geolens.jooq.governance.tables.AuditResults.AUDIT_RESULTS;
import static dev.geolens.jooq.measure.tables.Scores.SCORES;
import static dev.geolens.jooq.recommendation.tables.Results.RESULTS;

/**
 * PostgreSQL JOOQ implementasyonu — ADR-014: type-safe SQL, RLS dostu.
 * <p>RLS (ADR-004): her işlem, önceki JDBC sürümü gibi {@code set_config('app.tenant_id', ?, true)}
 * ile transaction-scoped tenant bağlamında çalışır. Tüm sorgularda tenant_id/workspace_id
 * WHERE kısıtı da açıkça korunur.
 */
@Repository
public class JooqRecommendationDao implements RecommendationDao {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public JooqRecommendationDao(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    private static void setTenant(DSLContext dsl, String tenantId) {
        dsl.fetchValue("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId);
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
    public ScoreSnapshot loadScore(String brandId, String workspaceId, String tenantId) {
        return inTenant(tenantId, () -> {
            Condition scope = SCORES.BRAND_ID.eq(brandId)
                    .and(SCORES.WORKSPACE_ID.eq(workspaceId))
                    .and(SCORES.TENANT_ID.eq(tenantId));

            Optional<? extends Record> latest = dsl.select(SCORES.VALUE, SCORES.FRESHNESS_AT, SCORES.ENGINE_BREAKDOWN)
                    .from(SCORES)
                    .where(scope)
                    .orderBy(SCORES.FRESHNESS_AT.desc())
                    .limit(1)
                    .fetchOptional();
            if (latest.isEmpty()) {
                return ScoreSnapshot.empty();
            }

            Record row = latest.get();
            double value = row.get(SCORES.VALUE);
            Instant freshnessAt = toInstant(row.get(SCORES.FRESHNESS_AT));
            Map<String, Double> breakdown = parseBreakdown(jsonData(row.get(SCORES.ENGINE_BREAKDOWN)));

            double prevValue = 0;
            Instant prevAt = null;
            Optional<? extends Record> prev = dsl.select(SCORES.VALUE, SCORES.FRESHNESS_AT)
                    .from(SCORES)
                    .where(scope)
                    .orderBy(SCORES.FRESHNESS_AT.desc())
                    .offset(1)
                    .limit(1)
                    .fetchOptional();
            if (prev.isPresent()) {
                prevValue = prev.get().get(SCORES.VALUE);
                prevAt = toInstant(prev.get().get(SCORES.FRESHNESS_AT));
            }

            return new ScoreSnapshot(value, prevValue, freshnessAt, prevAt, breakdown);
        });
    }

    @Override
    public AuditSnapshot loadAudit(String brandId, String tenantId) {
        return inTenant(tenantId, () -> {
            Field<Boolean> robotsDisallowed = DSL.field("({0}->>'disallowed_all')::boolean", Boolean.class,
                    AUDIT_RESULTS.ROBOTS_TXT);
            Field<Boolean> hasStructured = DSL.field("({0}->>'has_structured_data')::boolean", Boolean.class,
                    AUDIT_RESULTS.SSR);
            Field<Boolean> botAccessible = DSL.field("({0}->>'accessible')::boolean", Boolean.class,
                    AUDIT_RESULTS.BOT_ACCESS);

            Optional<? extends Record> maybe = dsl.select(
                            DSL.coalesce(robotsDisallowed, false).as("robots_disallowed"),
                            DSL.coalesce(hasStructured, false).as("has_structured"),
                            DSL.coalesce(botAccessible, false).as("bot_accessible"),
                            DSL.coalesce(AUDIT_RESULTS.OVERALL_SCORE, BigDecimal.ZERO).as("overall_score"))
                    .from(AUDIT_RESULTS)
                    .where(AUDIT_RESULTS.BRAND_ID.eq(brandId).and(AUDIT_RESULTS.TENANT_ID.eq(tenantId)))
                    .orderBy(AUDIT_RESULTS.CREATED_AT.desc())
                    .limit(1)
                    .fetchOptional();
            if (maybe.isEmpty()) {
                return AuditSnapshot.empty();
            }

            Record row = maybe.get();
            boolean robotsDisallowedAll = row.get("robots_disallowed", Boolean.class);
            boolean hasStructuredData = row.get("has_structured", Boolean.class);
            boolean botAccessibleFlag = row.get("bot_accessible", Boolean.class);
            double overall = row.get("overall_score", BigDecimal.class).doubleValue();
            return new AuditSnapshot(true, overall, robotsDisallowedAll, hasStructuredData, botAccessibleFlag);
        });
    }

    @Override
    public List<Brand> listActiveBrands(String workspaceId, String tenantId) {
        return inTenant(tenantId, () -> dsl.select(BRANDS.ID, BRANDS.NAME)
                .from(BRANDS)
                .where(BRANDS.WORKSPACE_ID.eq(workspaceId)
                        .and(BRANDS.TENANT_ID.eq(tenantId))
                        .and(BRANDS.IS_ACTIVE.isTrue()))
                .fetch(r -> new Brand(r.get(BRANDS.ID), r.get(BRANDS.NAME))));
    }

    @Override
    public void save(Recommendation rec) {
        runInTenant(rec.tenantId(), () -> dsl.insertInto(RESULTS)
                .columns(RESULTS.ID, RESULTS.BRAND_ID, RESULTS.WORKSPACE_ID, RESULTS.TENANT_ID, RESULTS.CATEGORY,
                        RESULTS.SEVERITY, RESULTS.EVIDENCE, RESULTS.TITLE, RESULTS.DETAIL, RESULTS.ACTION_URL,
                        RESULTS.CONFIDENCE, RESULTS.APPLIED, RESULTS.DISMISSED, RESULTS.CREATED_AT)
                .values(rec.id(), rec.brandId(), rec.workspaceId(), rec.tenantId(),
                        rec.category() == null ? null : rec.category().json(),
                        rec.severity() == null ? null : rec.severity().json(),
                        rec.evidence() == null ? null : rec.evidence().json(),
                        rec.title(), rec.detail(), rec.actionUrl(), BigDecimal.valueOf(rec.score()),
                        rec.applied(), rec.dismissed(), toOffsetDateTime(rec.createdAt()))
                .onConflictDoNothing()
                .execute());
    }

    @Override
    public void markApplied(String id, String tenantId, String workspaceId) {
        runInTenant(tenantId, () -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int rows = dsl.update(RESULTS)
                    .set(RESULTS.APPLIED, true)
                    .set(RESULTS.APPLIED_AT, now)
                    .set(RESULTS.UPDATED_AT, now)
                    .where(RESULTS.ID.eq(id).and(RESULTS.TENANT_ID.eq(tenantId)).and(RESULTS.WORKSPACE_ID.eq(workspaceId)))
                    .execute();
            if (rows == 0) {
                throw new RecommendationNotFoundException(
                        "recommendation: kayıt bulunamadı veya bu çalışma alanına ait değil");
            }
        });
    }

    @Override
    public void markDismissed(String id, String tenantId, String workspaceId) {
        runInTenant(tenantId, () -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int rows = dsl.update(RESULTS)
                    .set(RESULTS.DISMISSED, true)
                    .set(RESULTS.DISMISSED_AT, now)
                    .set(RESULTS.UPDATED_AT, now)
                    .where(RESULTS.ID.eq(id).and(RESULTS.TENANT_ID.eq(tenantId)).and(RESULTS.WORKSPACE_ID.eq(workspaceId)))
                    .execute();
            if (rows == 0) {
                throw new RecommendationNotFoundException(
                        "recommendation: kayıt bulunamadı veya bu çalışma alanına ait değil");
            }
        });
    }

    @Override
    public AppliedRecommendation loadApplied(String id, String workspaceId, String tenantId) {
        return inTenant(tenantId, () -> {
            Optional<? extends Record> maybe = dsl.select(RESULTS.BRAND_ID, RESULTS.APPLIED_AT)
                    .from(RESULTS)
                    .where(RESULTS.ID.eq(id)
                            .and(RESULTS.WORKSPACE_ID.eq(workspaceId))
                            .and(RESULTS.TENANT_ID.eq(tenantId))
                            .and(RESULTS.APPLIED.isTrue()))
                    .fetchOptional();
            if (maybe.isEmpty()) {
                return null;
            }
            Record row = maybe.get();
            String brandId = row.get(RESULTS.BRAND_ID);
            Instant appliedAt = toInstant(row.get(RESULTS.APPLIED_AT));
            return AppliedRecommendation.of(brandId, appliedAt);
        });
    }

    @Override
    public ScoreAt loadScoreAt(String brandId, String workspaceId, String tenantId, Instant at, boolean before) {
        return inTenant(tenantId, () -> {
            Condition scope = SCORES.BRAND_ID.eq(brandId)
                    .and(SCORES.WORKSPACE_ID.eq(workspaceId))
                    .and(SCORES.TENANT_ID.eq(tenantId));
            Field<String> fidelity = DSL.coalesce(SCORES.FIDELITY_LABEL, "yok").as("fidelity_label");

            Optional<? extends Record> maybe;
            if (before) {
                maybe = dsl.select(SCORES.VALUE, fidelity, SCORES.FRESHNESS_AT)
                        .from(SCORES)
                        .where(scope.and(SCORES.FRESHNESS_AT.le(toOffsetDateTime(at))))
                        .orderBy(SCORES.FRESHNESS_AT.desc())
                        .limit(1)
                        .fetchOptional();
            } else {
                maybe = dsl.select(SCORES.VALUE, fidelity, SCORES.FRESHNESS_AT)
                        .from(SCORES)
                        .where(scope.and(SCORES.FRESHNESS_AT.gt(toOffsetDateTime(at))))
                        .orderBy(SCORES.FRESHNESS_AT.asc())
                        .limit(1)
                        .fetchOptional();
            }
            if (maybe.isEmpty()) {
                return null;
            }
            Record row = maybe.get();
            double value = row.get(SCORES.VALUE);
            String fidelityLabel = row.get("fidelity_label", String.class);
            Instant measuredAt = toInstant(row.get(SCORES.FRESHNESS_AT));
            return new ScoreAt(value, fidelityLabel, measuredAt);
        });
    }

    private static String jsonData(JSON json) {
        return json == null ? null : json.data();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
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
