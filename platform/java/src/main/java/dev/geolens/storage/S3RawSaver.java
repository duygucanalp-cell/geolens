package dev.geolens.storage;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawSaver;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MinIO/S3 ham yanıt deposu — Go {@code storage.Client} portu.
 * <p>Ham motor yanıtları {@code raw/{tenant}/{workspace}/{engine}/{yyyy/MM/dd}/{HHmmss}-{hex}.json}
 * anahtar deseniyle kaydedilir (Go birebir). Bucket yoksa oluşturulur; endpoint
 * {@code http(s)://host:port} biçiminde kabul edilir (minio-go'dan farklı olarak
 * minio-java şemayı kabul eder, strip gerekmez).
 */
public final class S3RawSaver implements RawSaver {

    private static final Logger LOG = LoggerFactory.getLogger(S3RawSaver.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private final MinioClient client;
    private final String bucket;

    /**
     * @param endpoint  S3 endpoint'i ({@code http://localhost:9000})
     * @param accessKey S3 erişim anahtarı
     * @param secretKey S3 gizli anahtarı
     * @param bucket    hedef bucket (yoksa oluşturulur)
     * @param region    bölge (örn. {@code us-east-1})
     */
    public S3RawSaver(String endpoint, String accessKey, String secretKey, String bucket, String region) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region == null ? "" : region)
                .build();
        this.bucket = bucket;
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                LOG.info("s3 bucket oluşturuldu: {}", bucket);
            }
        } catch (Exception e) {
            throw new EngineException("s3 bucket kontrol/oluşturma hatası: " + e.getMessage(), e);
        }
    }

    @Override
    public String saveRawResponse(String tenantId, String workspaceId, String engineName, byte[] data) {
        String key = buildKey(tenantId, workspaceId, engineName, ZonedDateTime.now());

        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("application/json")
                    .build());
        } catch (Exception e) {
            throw new EngineException("s3 raw yanıt kaydetme: " + e.getMessage(), e);
        }
        return key;
    }

    /** Anahtar deseni: raw/{tenant}/{workspace}/{engine}/{yyyy/MM/dd}/{HHmmss}-{hex}.json (Go birebir). */
    static String buildKey(String tenantId, String workspaceId, String engineName, ZonedDateTime now) {
        // Go: now.Format("150405") + "-" + fmt.Sprintf("%x", now.UnixNano())[:8]
        long unixNano = now.toEpochSecond() * 1_000_000_000L + now.getNano();
        String hex = Long.toHexString(unixNano);
        return "raw/" + tenantId + "/" + workspaceId + "/" + engineName + "/"
                + DATE.format(now) + "/" + TIME.format(now) + "-"
                + hex.substring(0, Math.min(8, hex.length())) + ".json";
    }
}
