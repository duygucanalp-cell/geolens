package dev.geolens.governance;

import org.jooq.DSLContext;
import org.jooq.Record;
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

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public QuotaChecker(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    private static void setTenant(DSLContext dsl, String tenantId) {
        dsl.fetch("SELECT set_config('app.tenant_id', ?, true)", tenantId);
    }

    private void runInTenant(String tenantId, Runnable work) {
        if (tx == null) {
            setTenant(dsl, tenantId);
            work.run();
            return;
        }
        tx.executeWithoutResult(status -> {
            setTenant(dsl, tenantId);
            work.run();
        });
    }

    /** Kiracı için varsayılan kovaları yoksa oluşturur — Go {@code EnsureBuckets} portu. */
    public void ensureBuckets(String tenantId) {
        if (dsl == null) {
            return;
        }

        Timestamp windowStart = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MINUTES));
        runInTenant(tenantId, () -> {
            for (BucketConfig bucket : DEFAULT_BUCKETS) {
                String id = tenantId + "-" + bucket.bucketName();
                dsl.execute("""
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
        if (dsl == null) {
            return true;
        }

        // jOOQ fetchOne boş sonuçta null döner (EmptyResultDataAccessException fırlatmaz)
        Record r = dsl.fetchOne("""
                SELECT tokens_used, max_tokens
                FROM governance.rate_limit_buckets
                WHERE tenant_id = ? AND bucket_name = ?
                ORDER BY window_start DESC
                LIMIT 1
                """, tenantId, bucketName);
        if (r == null) {
            // Bucket yoksa varsayılan olarak izin ver
            return true;
        }
        Map<String, Object> row = r.intoMap();

        long tokensUsed = ((Number) row.get("tokens_used")).longValue();
        long maxTokens = ((Number) row.get("max_tokens")).longValue();
        if (tokensUsed >= maxTokens) {
            return false;
        }

        // Token tüket (son pencereye)
        runInTenant(tenantId, () -> dsl.execute("""
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