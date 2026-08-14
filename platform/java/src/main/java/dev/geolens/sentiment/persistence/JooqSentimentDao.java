package dev.geolens.sentiment.persistence;

import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.geolens.jooq.analysis.tables.HallucinationFlags.HALLUCINATION_FLAGS;
import static dev.geolens.jooq.analysis.tables.SentimentScores.SENTIMENT_SCORES;
import static dev.geolens.jooq.config.tables.Brands.BRANDS;
import static dev.geolens.jooq.measure.tables.RawResponses.RAW_RESPONSES;

/**
 * PostgreSQL JOOQ implementasyonu — ADR-014: type-safe SQL, RLS dostu.
 * (measure.raw_responses / analysis.sentiment_scores + hallucination_flags).
 * <p>RLS (ADR-004): her işlem {@code set_config('app.tenant_id', ?, true)} ile transaction-scoped
 * tenant bağlamında çalışır; WHERE kısıtları da açıkça korunur.
 */
@Repository
public class JooqSentimentDao implements SentimentDao {

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public JooqSentimentDao(DSLContext dsl, TransactionTemplate tx) {
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
    public List<RawResponse> loadRawResponses(String tenantId, String brandId) {
        return inTenant(tenantId, () -> dsl.select(RAW_RESPONSES.ID, RAW_RESPONSES.ENGINE_NAME,
                        RAW_RESPONSES.CONTENT_TEXT, RAW_RESPONSES.CREATED_AT)
                .from(RAW_RESPONSES)
                .where(RAW_RESPONSES.TENANT_ID.eq(tenantId).and(RAW_RESPONSES.BRAND_ID.eq(brandId)))
                .orderBy(RAW_RESPONSES.CREATED_AT.desc())
                .limit(50)
                .fetch(r -> new RawResponse(
                        r.get(RAW_RESPONSES.ID),
                        r.get(RAW_RESPONSES.ENGINE_NAME),
                        r.get(RAW_RESPONSES.CONTENT_TEXT),
                        toInstant(r.get(RAW_RESPONSES.CREATED_AT)))));
    }

    @Override
    public List<CheckTarget> loadCheckTargets(String tenantId, String brandId) {
        return inTenant(tenantId, () -> {
            Field<String> prompt = DSL.coalesce(RAW_RESPONSES.PROMPT_TEXT, "").as("prompt");
            Field<String> name = DSL.coalesce(BRANDS.NAME, "").as("name");
            Field<String> url = DSL.coalesce(BRANDS.WEBSITE_URL, "").as("url");
            return dsl.select(RAW_RESPONSES.ID, RAW_RESPONSES.ENGINE_NAME, RAW_RESPONSES.CONTENT_TEXT,
                            prompt, name, url)
                    .from(RAW_RESPONSES)
                    .join(BRANDS).on(BRANDS.ID.eq(RAW_RESPONSES.BRAND_ID))
                    .where(RAW_RESPONSES.TENANT_ID.eq(tenantId).and(RAW_RESPONSES.BRAND_ID.eq(brandId)))
                    .orderBy(RAW_RESPONSES.CREATED_AT.desc())
                    .limit(20)
                    .fetch(r -> new CheckTarget(
                            r.get(RAW_RESPONSES.ID),
                            r.get(RAW_RESPONSES.ENGINE_NAME),
                            r.get(RAW_RESPONSES.CONTENT_TEXT),
                            r.get(prompt),
                            r.get(name),
                            r.get(url)));
        });
    }

    @Override
    public void saveSentiment(String tenantId, String workspaceId, SentimentResult result) {
        // created_at DB default'undan gelir (now()); REAL kolonlar Float bekler
        runInTenant(tenantId, () -> dsl.insertInto(SENTIMENT_SCORES)
                .columns(SENTIMENT_SCORES.ID, SENTIMENT_SCORES.BRAND_ID, SENTIMENT_SCORES.ENGINE_NAME,
                        SENTIMENT_SCORES.OVERALL_SENTIMENT, SENTIMENT_SCORES.POSITIVE_SCORE,
                        SENTIMENT_SCORES.NEUTRAL_SCORE, SENTIMENT_SCORES.NEGATIVE_SCORE,
                        SENTIMENT_SCORES.MENTION_COUNT, SENTIMENT_SCORES.TENANT_ID, SENTIMENT_SCORES.WORKSPACE_ID,
                        SENTIMENT_SCORES.ANALYZED_AT)
                .values(newId(), result.brandId(), result.engineName(),
                        (float) result.overallSentiment(), (float) result.positiveScore(),
                        (float) result.neutralScore(), (float) result.negativeScore(),
                        result.mentionCount(), tenantId, workspaceId,
                        toOffsetDateTime(result.analyzedAt()))
                .onConflictDoNothing()
                .execute());
    }

    @Override
    public void saveHallucination(String tenantId, String workspaceId, HallucinationResult result) {
        // created_at DB default'undan gelir (now()); REAL kolon Float bekler
        runInTenant(tenantId, () -> dsl.insertInto(HALLUCINATION_FLAGS)
                .columns(HALLUCINATION_FLAGS.ID, HALLUCINATION_FLAGS.BRAND_ID, HALLUCINATION_FLAGS.ENGINE_NAME,
                        HALLUCINATION_FLAGS.HALLUCINATION_TYPE, HALLUCINATION_FLAGS.SEVERITY,
                        HALLUCINATION_FLAGS.DESCRIPTION, HALLUCINATION_FLAGS.CONFIDENCE,
                        HALLUCINATION_FLAGS.TENANT_ID, HALLUCINATION_FLAGS.WORKSPACE_ID)
                .values(newId(), result.brandId(), result.engineName(), result.hallucinationType(),
                        result.severity(), result.description(), (float) result.confidence(), tenantId, workspaceId)
                .onConflictDoNothing()
                .execute());
    }

    @Override
    public SentimentResult loadLatest(String brandId, String tenantId) {
        return inTenant(tenantId, () -> {
            Optional<? extends Record> maybe = dsl.select(SENTIMENT_SCORES.ENGINE_NAME,
                            SENTIMENT_SCORES.OVERALL_SENTIMENT, SENTIMENT_SCORES.POSITIVE_SCORE,
                            SENTIMENT_SCORES.NEUTRAL_SCORE, SENTIMENT_SCORES.NEGATIVE_SCORE,
                            SENTIMENT_SCORES.MENTION_COUNT, SENTIMENT_SCORES.ANALYZED_AT)
                    .from(SENTIMENT_SCORES)
                    .where(SENTIMENT_SCORES.BRAND_ID.eq(brandId).and(SENTIMENT_SCORES.TENANT_ID.eq(tenantId)))
                    .orderBy(SENTIMENT_SCORES.ANALYZED_AT.desc())
                    .limit(1)
                    .fetchOptional();
            if (maybe.isEmpty()) {
                return null;
            }
            Record row = maybe.get();
            return SentimentResult.of(brandId, row.get(SENTIMENT_SCORES.ENGINE_NAME),
                    row.get(SENTIMENT_SCORES.OVERALL_SENTIMENT), row.get(SENTIMENT_SCORES.POSITIVE_SCORE),
                    row.get(SENTIMENT_SCORES.NEUTRAL_SCORE), row.get(SENTIMENT_SCORES.NEGATIVE_SCORE),
                    row.get(SENTIMENT_SCORES.MENTION_COUNT), toInstant(row.get(SENTIMENT_SCORES.ANALYZED_AT)));
        });
    }

    private static String newId() {
        return Ulid.generate();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }
}
