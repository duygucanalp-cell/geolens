package dev.geolens.governance;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Kiracı işlemleri için hız limiti denetleyici — Go {@code governance.QuotaChecker} portu.
 * Token bucket modeli: her kiracının belirli periyotta belirli sayıda token'ı vardır.
 * <p>Veritabanı yoksa (null pool) denetim devre dışı kalır: {@link #ensureBuckets(String)} no-op,
 * {@link #checkAndConsume(String, String)} her zaman true döner (Go fallback davranışı).
 */
public final class QuotaChecker {

    /** Hız limiti kova yapılandırması — Go {@code BucketConfig} portu. */
    public record BucketConfig(String bucketName, long maxTokens) {
    }

    // Varsayılan kova yapılandırmaları (Go ile birebir)
    public static final List<BucketConfig> DEFAULT_BUCKETS = List.of(
            new BucketConfig("engine_calls_per_min", 30),
            new BucketConfig("engine_calls_per_hour", 500),
            new BucketConfig("api_requests_per_hour", 1000));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public QuotaChecker(JdbcTemplate jdbc, TransactionTemplate tx) {
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

    private void runInTenant(String tenantId, Runnable work) {
        if (tx == null) {
            setTenant(jdbc, tenantId);
            work.run();
            return;
        }
        tx.executeWithoutResult(status -> {
            setTenant(jdbc, tenantId);
            work.run();
        });
    }

    /** Kiracı için varsayılan kovaları yoksa oluşturur — Go {@code EnsureBuckets} portu. */
    public void ensureBuckets(String tenantId) {
        if (jdbc == null) {
            return;
        }

        Timestamp windowStart = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MINUTES));
        runInTenant(tenantId, () -> {
            for (BucketConfig bucket : DEFAULT_BUCKETS) {
                String id = tenantId + "-" + bucket.bucketName();
                jdbc.update("""
                        INSERT INTO governance.rate_limit_buckets (id, tenant_id, bucket_name, max_tokens, window_start)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, bucket_name, window_start) DO NOTHING
                        """, id, tenantId, bucket.bucketName(), bucket.maxTokens(), windowStart);
            }
        });
    }

    /**
     * Kiracının kullanılabilir token'ı varsa kontrol eder ve bir token tüketir.
     * Bucket yoksa veya sorgu hatası varsa varsayılan olarak izin verir (Go davranışı).
     */
    public boolean checkAndConsume(String tenantId, String bucketName) {
        if (jdbc == null) {
            return true;
        }

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT tokens_used, max_tokens
                    FROM governance.rate_limit_buckets
                    WHERE tenant_id = ? AND bucket_name = ?
                    ORDER BY window_start DESC
                    LIMIT 1
                    """, tenantId, bucketName);
        } catch (EmptyResultDataAccessException e) {
            // Bucket yoksa varsayılan olarak izin ver
            return true;
        }

        long tokensUsed = ((Number) row.get("tokens_used")).longValue();
        long maxTokens = ((Number) row.get("max_tokens")).longValue();
        if (tokensUsed >= maxTokens) {
            return false;
        }

        // Token tüket (son pencereye)
        runInTenant(tenantId, () -> jdbc.update("""
                UPDATE governance.rate_limit_buckets
                SET tokens_used = tokens_used + 1, updated_at = now()
                WHERE tenant_id = ? AND bucket_name = ?
                  AND window_start = (
                      SELECT window_start FROM governance.rate_limit_buckets
                      WHERE tenant_id = ? AND bucket_name = ?
                      ORDER BY window_start DESC LIMIT 1
                  )
                """, tenantId, bucketName, tenantId, bucketName));
        return true;
    }
}