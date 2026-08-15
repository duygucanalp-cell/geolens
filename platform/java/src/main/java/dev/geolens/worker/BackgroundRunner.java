package dev.geolens.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import dev.geolens.delivery.DeliveryService;
import dev.geolens.queue.QueueProperties;
import dev.geolens.queue.RedisStreamClient;
import dev.geolens.queue.RedisStreamClient.StreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Worker profil giriş noktası — Go {@code cmd/worker} karşılığı.
 * <p>Consumer group'ları oluşturur ve iki tüketici döngüsünü virtual thread'lerde çalıştırır:
 * <ul>
 *   <li><b>q:measure</b> — ölçüm job'ları (Go {@code runWorker})</li>
 *   <li><b>q:governance</b> — Faz 4 olayları: guardrail/gate/incident/drift/redteam webhook
 *       iletimi + ACK (Go {@code runGovernanceWorker})</li>
 * </ul>
 * Uygulama, {@link CountDownLatch} ile açık tutulur; {@code @PreDestroy} ile kapanışta serbest bırakılır.
 */
@Component
@Profile("worker")
public class BackgroundRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackgroundRunner.class);

    private final RedisStreamClient redis;
    private final QueueProperties props;
    private final MeasurementPipeline pipeline;
    private final DeliveryService deliveryService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CountDownLatch stop = new CountDownLatch(1);

    @Value("${queue.workers-enabled:true}")
    private boolean workersEnabled;

    public BackgroundRunner(RedisStreamClient redis, QueueProperties props,
                            MeasurementPipeline pipeline, DeliveryService deliveryService) {
        this.redis = redis;
        this.props = props;
        this.pipeline = pipeline;
        this.deliveryService = deliveryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String group = props.getConsumerGroup();
        redis.ensureAllGroups(group);
        log.info("worker başlatılıyor", "consumer_group", group);

        if (workersEnabled) {
            Thread.ofVirtual().name("measure-consumer").start(this::measureLoop);
            Thread.ofVirtual().name("governance-consumer").start(this::governanceLoop);
        } else {
            log.info("worker tüketicileri kapalı (queue.workers-enabled=false)");
        }

        // Go signal.Wait karşılığı — context kapanana kadar bekle
        try {
            stop.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("worker durduruldu");
    }

    /** Go {@code runWorker} karşılığı — q:measure stream'inden BLOCK ile mesaj okur ve işler. */
    private void measureLoop() {
        String group = props.getConsumerGroup();
        String consumer = props.getConsumerName();
        while (stop.getCount() > 0) {
            List<StreamMessage> records;
            try {
                records = redis.readGroup(QueueProperties.STREAM_MEASURE, group, consumer);
            } catch (RuntimeException e) {
                log.error("redis stream okuma hatası", "error", e.getMessage());
                sleepQuietly(1000);
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (StreamMessage record : records) {
                try {
                    pipeline.processMessage(record);
                } catch (RuntimeException e) {
                    log.error("worker: mesaj işleme hatası", "msg_id", record.id(), "error", e.getMessage());
                }
            }
        }
    }

    /** Go {@code runGovernanceWorker} karşılığı — q:governance olaylarını iletir ve ACK'ler. */
    private void governanceLoop() {
        String group = props.getConsumerGroup();
        String consumer = props.getConsumerName() + "-governance";
        while (stop.getCount() > 0) {
            List<StreamMessage> records;
            try {
                records = redis.readGroup(QueueProperties.STREAM_GOVERNANCE, group, consumer);
            } catch (RuntimeException e) {
                log.error("governance: stream okuma hatası", "error", e.getMessage());
                sleepQuietly(1000);
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            for (StreamMessage record : records) {
                try {
                    processGovernanceMessage(record, group);
                } catch (RuntimeException e) {
                    log.error("governance: mesaj işleme hatası", "msg_id", record.id(), "error", e.getMessage());
                }
            }
        }
    }

    /** Go {@code processGovernanceMessage} karşılığı — webhook iletimi (best-effort) + ACK. */
    private void processGovernanceMessage(StreamMessage record, String group) {
        Map<String, String> values = record.values();
        String eventType = values.get("event");
        String tenantId = values.get("tenant_id") == null ? "" : values.get("tenant_id");

        if (eventType == null || eventType.isEmpty()) {
            log.warn("governance: event tipi eksik", "msg_id", record.id());
            redis.ack(QueueProperties.STREAM_GOVERNANCE, group, record.id());
            return;
        }

        // Webhook iletimi (best-effort): dispatcher payload'ı "data" alanında taşır
        Map<String, Object> payload = Map.of();
        String dataStr = values.get("data");
        if (dataStr != null) {
            try {
                JsonNode data = mapper.readTree(dataStr);
                JsonNode p = data.get("payload");
                if (p != null && !p.isNull()) {
                    payload = mapper.convertValue(p, Map.class);
                }
            } catch (Exception e) {
                log.warn("governance: data ayrıştırma hatası", "error", e.getMessage());
            }
        }

        try {
            deliveryService.sendGovernanceEvent(tenantId, eventType, payload);
        } catch (RuntimeException e) {
            log.warn("governance webhook iletim hatası", "event_type", eventType, "error", e.getMessage());
        }

        log.debug("governance olayı işlendi", "event_type", eventType, "tenant_id", tenantId);
        redis.ack(QueueProperties.STREAM_GOVERNANCE, group, record.id());
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Kapanış — context iptali (Go cancel) karşılığı. */
    @PreDestroy
    public void shutdown() {
        stop.countDown();
    }
}
