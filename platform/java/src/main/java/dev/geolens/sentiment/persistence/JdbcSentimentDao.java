package dev.geolens.sentiment.persistence;

import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.util.Ulid;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * PostgreSQL JDBC implementasyonu — Go {@code sentiment.Engine} sorgularının birebir karşılığı
 * (measure.raw_responses / analysis.sentiment_scores + hallucination_flags).
 * <p>RLS (ADR-004): her işlem {@code set_config('app.tenant_id', ?, true)} ile transaction-scoped
 * tenant bağlamında çalışır; WHERE kısıtları da açıkça korunur. RLS yalnızca tenant filtrelemesi
 * yaptığından lay-below için workspace sahibi sorguları çalışma alanıyla eşleşir.
 */
@Repository
public class JdbcSentimentDao implements SentimentDao {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcSentimentDao(JdbcTemplate jdbc, TransactionTemplate tx) {
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
    public List<RawResponse> loadRawResponses(String tenantId, String brandId) {
        return inTenant(tenantId, () -> jdbc.query("""
                SELECT rr.id, rr.engine_name, rr.content_text, rr.created_at
                FROM measure.raw_responses rr
                WHERE rr.tenant_id = ? AND rr.brand_id = ?
                ORDER BY rr.created_at DESC
                LIMIT 50
                """, (rs, n) -> new RawResponse(
                rs.getString("id"),
                rs.getString("engine_name"),
                rs.getString("content_text"),
                rs.getTimestamp("created_at").toInstant()),
                tenantId, brandId));
    }

    @Override
    public List<CheckTarget> loadCheckTargets(String tenantId, String brandId) {
        return inTenant(tenantId, () -> jdbc.query("""
                SELECT rr.id, rr.engine_name, rr.content_text, COALESCE(rr.prompt_text, ''), COALESCE(b.name, ''), COALESCE(b.website_url, '')
                FROM measure.raw_responses rr
                JOIN config.brands b ON b.id = rr.brand_id
                WHERE rr.tenant_id = ? AND rr.brand_id = ?
                ORDER BY rr.created_at DESC
                LIMIT 20
                """, (rs, n) -> new CheckTarget(
                rs.getString("id"),
                rs.getString("engine_name"),
                rs.getString("content_text"),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6)),
                tenantId, brandId));
    }

    @Override
    public void saveSentiment(String tenantId, String workspaceId, SentimentResult result) {
        runInTenant(tenantId, () -> jdbc.update("""
                INSERT INTO analysis.sentiment_scores
                    (id, brand_id, engine_name, overall_sentiment, positive_score, neutral_score, negative_score,
                     mention_count, tenant_id, workspace_id, created_at, analyzed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)
                ON CONFLICT (id) DO NOTHING
                """,
                newId(), result.brandId(), result.engineName(),
                result.overallSentiment(), result.positiveScore(), result.neutralScore(), result.negativeScore(),
                result.mentionCount(), tenantId, workspaceId, Timestamp.from(result.analyzedAt())));
    }

    @Override
    public void saveHallucination(String tenantId, String workspaceId, HallucinationResult result) {
        runInTenant(tenantId, () -> jdbc.update("""
                INSERT INTO analysis.hallucination_flags
                    (id, brand_id, engine_name, hallucination_type, severity, description, confidence,
                     tenant_id, workspace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO NOTHING
                """,
                newId(), result.brandId(), result.engineName(), result.hallucinationType(),
                result.severity(), result.description(), result.confidence(), tenantId, workspaceId));
    }

    @Override
    public SentimentResult loadLatest(String brandId, String tenantId) {
        return inTenant(tenantId, () -> {
            try {
                return jdbc.queryForObject("""
                        SELECT engine_name, overall_sentiment, positive_score, neutral_score,
                               negative_score, mention_count, analyzed_at
                        FROM analysis.sentiment_scores
                        WHERE brand_id = ? AND tenant_id = ?
                        ORDER BY analyzed_at DESC LIMIT 1
                        """, (rs, n) -> {
                    Instant at = rs.getTimestamp("analyzed_at").toInstant();
                    return new SentimentResult(null, brandId, rs.getString("engine_name"),
                            rs.getDouble("overall_sentiment"), rs.getDouble("positive_score"),
                            rs.getDouble("neutral_score"), rs.getDouble("negative_score"),
                            rs.getInt("mention_count"), null, at);
                }, brandId, tenantId);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
        });
    }

    private static String newId() {
        return Ulid.generate();
    }
}