package dev.geolens.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.competitive.CompetitiveEngine;
import dev.geolens.delivery.DeliveryConstants;
import dev.geolens.delivery.DeliveryService;
import dev.geolens.delivery.Notification;
import dev.geolens.delivery.NotificationSettings;
import dev.geolens.engine.Adapter;
import dev.geolens.engine.EngineException;
import dev.geolens.engine.EngineMeta;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.RawSaver;
import dev.geolens.engine.Registry;
import dev.geolens.measure.ComponentWeights;
import dev.geolens.measure.MeasurementResult;
import dev.geolens.measure.Score;
import dev.geolens.measure.web.MeasureJob;
import dev.geolens.queue.QueueProperties;
import dev.geolens.queue.RedisStreamClient;
import dev.geolens.queue.RedisStreamClient.StreamMessage;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Severity;
import dev.geolens.recommendation.service.RecommendationService;
import dev.geolens.sentiment.engine.SentimentEngine;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker ölçüm pipeline'ı — Go {@code worker.processMessage} + {@code computeAndEvaluate} portu.
 * <p>q:measure stream'inden gelen {@code measurement.requested} olaylarını işler: motor çağrısı,
 * S3 ham kayıt, measurement_jobs/raw_responses persistance, skor hesaplama, tavsiye değerlendirme,
 * sentiment/hallüsinasyon/competitive gap analizi ve kritik bildirim gönderimi.
 */
@Component
@Profile("worker")
public class MeasurementPipeline {

    private static final Logger log = LoggerFactory.getLogger(MeasurementPipeline.class);

    private final DSLContext dsl;
    private final Registry engines;
    private final ObjectProvider<RawSaver> storage;
    private final dev.geolens.measure.MeasureService measureEngine;
    private final RecommendationService recommendationService;
    private final SentimentEngine sentimentEngine;
    private final CompetitiveEngine competitiveEngine;
    private final DeliveryService deliveryService;
    private final RedisStreamClient redis;
    private final QueueProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public MeasurementPipeline(DSLContext dsl, Registry engines, ObjectProvider<RawSaver> storage,
                               dev.geolens.measure.MeasureService measureEngine,
                               RecommendationService recommendationService,
                               SentimentEngine sentimentEngine,
                               CompetitiveEngine competitiveEngine,
                               DeliveryService deliveryService,
                               RedisStreamClient redis, QueueProperties props) {
        this.dsl = dsl;
        this.engines = engines;
        this.storage = storage;
        this.measureEngine = measureEngine;
        this.recommendationService = recommendationService;
        this.sentimentEngine = sentimentEngine;
        this.competitiveEngine = competitiveEngine;
        this.deliveryService = deliveryService;
        this.redis = redis;
        this.props = props;
    }

    /**
     * Tek bir q:measure mesajını işler — Go {@code processMessage} karşılığı.
     * Her yolda mesaj ACK'lenir (tanınmayan olay/eksik alan/motor hatası dahil);
     * motor hatası dead letter queue'ya yönlendirilir.
     */
    public void processMessage(StreamMessage message) {
        String stream = message.stream();
        String msgId = message.id();
        Map<String, String> values = message.values();

        String dataStr = values.get("data");
        if (dataStr == null) {
            log.warn("worker: data alanı eksik", "msg_id", msgId);
            redis.ack(stream, props.getConsumerGroup(), msgId);
            return;
        }

        String eventType = values.get("event");
        if (!"measurement.requested".equals(eventType)) {
            // Tanınmayan event tipi — yine de ACK'le (Go ile aynı)
            redis.ack(stream, props.getConsumerGroup(), msgId);
            return;
        }

        JsonNode msgData;
        MeasureJob job;
        try {
            msgData = mapper.readTree(dataStr);
            JsonNode payload = msgData.get("payload");
            if (payload == null || payload.isNull()) {
                log.warn("worker: payload alanı eksik", "msg_id", msgId);
                redis.ack(stream, props.getConsumerGroup(), msgId);
                return;
            }
            job = mapper.treeToValue(payload, MeasureJob.class);
        } catch (Exception e) {
            log.warn("worker: data ayrıştırma hatası", "error", e.getMessage(), "msg_id", msgId);
            redis.ack(stream, props.getConsumerGroup(), msgId);
            return;
        }

        log.info("worker: işleniyor", "brand", job.brandName(), "engine", job.engineName(),
                "sample", job.sampleIndex(), "msg_id", msgId);

        Adapter adapter = engines.get(job.engineName());
        if (adapter == null) {
            log.warn("worker: motor bulunamadı", "engine", job.engineName());
            sendToDeadLetter(msgId, job, "engine " + job.engineName() + " not found");
            redis.ack(stream, props.getConsumerGroup(), msgId);
            return;
        }
        adapter = adapter.withContext(job.tenantId(), job.workspaceId());

        RawResponse result;
        try {
            result = adapter.execute(job.promptText());
        } catch (EngineException e) {
            log.error("worker: engine çağrı hatası", "engine", job.engineName(), "error", e.getMessage());
            sendToDeadLetter(msgId, job, e.getMessage());
            redis.ack(stream, props.getConsumerGroup(), msgId);
            return;
        }

        // Ham yanıtı S3'e kaydet (storage varsa) — Go RawSaver karşılığı
        String s3Ref = "";
        RawSaver saver = storage.getIfAvailable();
        if (saver != null) {
            try {
                s3Ref = saver.saveRawResponse(job.tenantId(), job.workspaceId(), job.engineName(),
                        mapper.writeValueAsBytes(result));
            } catch (Exception e) {
                log.warn("worker: S3 kaydetme hatası", "error", e.getMessage());
            }
        }

        // measurement_jobs tablosuna kaydet (idempotent: conflict'te güncelle, her durumda id döner)
        String idempotencyKey = String.format("worker:%s:%s:%d:%s",
                job.brandId(), job.engineName(), job.sampleIndex(), msgId);
        String jobId = null;
        try {
            jobId = dsl.fetchOne("""
                    INSERT INTO measure.measurement_jobs (id, brand_id, panel_id, engine_name, status, tenant_id, workspace_id, prompt_text, sample_count, idempotency_key, created_at)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, 'completed', ?, ?, ?, 3, ?, now())
                    ON CONFLICT (idempotency_key) DO UPDATE SET status = 'completed', updated_at = now()
                    RETURNING id
                    """, job.brandId(), job.panelId(), job.engineName(), job.tenantId(), job.workspaceId(),
                    job.promptText(), idempotencyKey).get(0, String.class);
        } catch (RuntimeException e) {
            log.error("worker: measurement_job kaydetme hatası", "error", e.getMessage());
        }

        // Ham yanıtı raw_responses tablosuna kaydet (sadece job kaydı başarılıysa)
        if (jobId != null) {
            try {
                dsl.execute("""
                        INSERT INTO measure.raw_responses (id, job_id, engine_name, raw_body, content_text, s3_ref, tenant_id, brand_id, workspace_id, prompt_text, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                        """, Ulid.generate(), jobId, job.engineName(), result.content(), result.content(),
                        s3Ref, job.tenantId(), job.brandId(), job.workspaceId(), job.promptText());
            } catch (RuntimeException e) {
                log.error("worker: raw_response kaydetme hatası", "error", e.getMessage());
            }
        }

        redis.ack(stream, props.getConsumerGroup(), msgId);

        // Skor hesaplama + AI analizleri (arka planda, hata worker'ı durdurmaz)
        try {
            computeAndEvaluate(job.tenantId(), job.workspaceId(), job.panelId(), job.brandId(), job.promptText());
        } catch (RuntimeException e) {
            log.warn("worker: skor/analiz hatası", "error", e.getMessage());
        }

        log.info("worker: iş tamamlandı", "msg_id", msgId);
    }

    /**
     * Skor hesaplama, tavsiye değerlendirme, AI analizleri ve kritik bildirimler —
     * Go {@code computeAndEvaluate} karşılığı.
     */
    public void computeAndEvaluate(String tenantId, String workspaceId, String panelId, String brandId, String promptText) {
        // 1. Ham yanıtları yükle (son 1 saat)
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT engine_name, content_text, COALESCE(raw_body, content_text) AS body
                    FROM measure.raw_responses
                    WHERE tenant_id = ? AND workspace_id = ? AND brand_id = ?
                    AND created_at > now() - interval '1 hour'
                    ORDER BY engine_name, created_at
                    """, tenantId, workspaceId, brandId).intoMaps();
        } catch (RuntimeException e) {
            log.warn("compute: raw_response sorgu hatası", "error", e.getMessage());
            return;
        }

        // 2. Motor bazında grupla -> MeasurementResult
        Map<String, List<RawResponse>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String engineName = String.valueOf(row.get("engine_name"));
            String contentText = String.valueOf(row.get("content_text"));
            grouped.computeIfAbsent(engineName, k -> new ArrayList<>())
                    .add(new RawResponse(engineName, "", contentText, List.of(), false, null, "", ""));
        }
        if (grouped.isEmpty()) {
            return;
        }

        List<MeasurementResult> results = new ArrayList<>();
        for (Map.Entry<String, List<RawResponse>> entry : grouped.entrySet()) {
            results.add(new MeasurementResult(entry.getValue(), List.of(),
                    new EngineMeta(entry.getKey(), "", null, 0),
                    brandId, "", panelId, workspaceId, tenantId, promptText));
        }

        // 3. Skor hesapla
        Score score;
        try {
            score = measureEngine.calculateScore(panelId, results, ComponentWeights.EMPTY);
        } catch (RuntimeException e) {
            log.warn("compute: skor hesaplama hatası: {}", e.getMessage(), e);
            return;
        }
        log.info("compute: skor hesaplandı", "value", score.value());

        // 4. Tavsiyeleri değerlendir
        List<Recommendation> recs;
        try {
            recs = recommendationService.evaluate(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            log.warn("compute: tavsiye değerlendirme hatası", "error", e.getMessage());
            recs = List.of();
        }

        // 5. AI analizleri (best-effort — hata worker'ı durdurmaz)
        try {
            sentimentEngine.analyzeSentiment(tenantId, workspaceId, brandId, "");
        } catch (RuntimeException e) {
            log.warn("compute: sentiment analiz hatası", "error", e.getMessage());
        }
        try {
            sentimentEngine.detectHallucinations(tenantId, workspaceId, brandId);
        } catch (RuntimeException e) {
            log.warn("compute: hallüsinasyon tespit hatası", "error", e.getMessage());
        }
        try {
            competitiveEngine.analyzeAllGaps(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            log.warn("compute: competitive gap analiz hatası", "error", e.getMessage());
        }

        // 6. Kritik bildirimleri kontrol et
        List<Recommendation> critical = recs.stream()
                .filter(r -> r.severity() == Severity.CRITICAL || r.severity() == Severity.HIGH)
                .toList();
        if (critical.isEmpty()) {
            return;
        }

        NotificationSettings settings;
        try {
            settings = deliveryService.getSettings(workspaceId, tenantId);
        } catch (RuntimeException e) {
            log.warn("compute: bildirim ayarı sorgu hatası", "error", e.getMessage());
            return;
        }
        if (settings == null || !settings.notifyOnDrop()) {
            return;
        }

        for (Recommendation r : critical) {
            Notification notif = new Notification("", tenantId, "", workspaceId,
                    DeliveryConstants.NOTIFICATION_SCORE_DROP, DeliveryConstants.CHANNEL_EMAIL,
                    r.title(), r.detail(), null,
                    Map.of("brand_id", brandId, "severity", r.severity().json(),
                            "score", score.value(), "threshold", settings.dropThreshold()),
                    DeliveryConstants.DELIVERY_PENDING, null, null, false, "", "");
            try {
                deliveryService.sendNotification(notif);
            } catch (RuntimeException e) {
                log.warn("compute: bildirim gönderme hatası", "error", e.getMessage());
            }
        }
    }

    /** Başarısız mesajı dead letter queue'ya yönlendirir — Go {@code sendToDeadLetter} karşılığı. */
    public void sendToDeadLetter(String msgId, MeasureJob job, String reason) {
        try {
            String data = mapper.writeValueAsString(Map.of(
                    "original_msg_id", msgId,
                    "job", job,
                    "reason", reason,
                    "timestamp", java.time.Instant.now().toString()));
            redis.add(QueueProperties.STREAM_DEAD, Map.of(
                    "event", "measurement.failed",
                    "data", data));
        } catch (Exception e) {
            log.error("worker: dead letter gönderme hatası", "error", e.getMessage());
        }
    }
}
