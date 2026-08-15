package dev.geolens.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Outbox dağıtıcı — Go {@code queue.Dispatcher} karşılığı (scheduler profilinde çalışır).
 * <p>PostgreSQL'deki bekleyen {@code public.event_outbox} kayıtlarını Redis Stream'lerine
 * iletir (FOR UPDATE SKIP LOCKED) ve {@code dispatched_at} işaretler. Go ile aynı mesaj
 * biçimini üretir: {@code {event, tenant_id, data:{event_id, event_type, tenant_id, payload, timestamp}}}.
 */
@Component
@Profile("scheduler")
@ConditionalOnProperty(prefix = "queue", name = "dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final DSLContext dsl;
    private final RedisStreamClient redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public OutboxDispatcher(DSLContext dsl, RedisStreamClient redis) {
        this.dsl = dsl;
        this.redis = redis;
    }

    /** Go {@code dispatchPending} karşılığı — bekleyen kayıtları batch halinde dağıtır. */
    @Scheduled(fixedDelayString = "${queue.poll-ms:30000}")
    public void dispatchPending() {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT id, event_type, stream, payload, tenant_id
                    FROM public.event_outbox
                    WHERE dispatched_at IS NULL
                    ORDER BY created_at ASC
                    LIMIT 100
                    FOR UPDATE SKIP LOCKED
                    """).intoMaps();
        } catch (RuntimeException e) {
            log.error("outbox: pending sorgu hatası", e);
            return;
        }

        int count = 0;
        for (Map<String, Object> row : rows) {
            try {
                dispatchOne(row);
                count++;
            } catch (Exception e) {
                log.error("outbox: gönderme hatası", "event_id", row.get("id"), "error", e.getMessage());
            }
        }
        if (count > 0) {
            log.debug("outbox: mesajlar dağıtıldı", "count", count);
        }
    }

    /** Go {@code dispatchOne} karşılığı — tek kaydı stream'e yazar ve dispatched işaretler. */
    private void dispatchOne(Map<String, Object> row) throws Exception {
        String id = String.valueOf(row.get("id"));
        String eventType = String.valueOf(row.get("event_type"));
        String tenantId = row.get("tenant_id") == null ? "" : String.valueOf(row.get("tenant_id"));
        String stream = row.get("stream") == null ? "" : String.valueOf(row.get("stream"));
        if (stream.isEmpty()) {
            stream = QueueProperties.STREAM_MEASURE; // varsayılan (Go ile aynı)
        }

        // payload (JSONB) ham JSON metni olarak alınır — Go json.RawMessage karşılığı
        String payloadText = toJsonString(row.get("payload"));

        ObjectNode data = mapper.createObjectNode();
        data.put("event_id", id);
        data.put("event_type", eventType);
        data.put("tenant_id", tenantId);
        data.set("payload", mapper.readTree(payloadText));
        data.put("timestamp", Instant.now().toString());

        redis.add(stream, Map.of(
                "event", eventType,
                "tenant_id", tenantId,
                "data", mapper.writeValueAsString(data)));

        dsl.execute("UPDATE public.event_outbox SET dispatched_at = now() WHERE id = ?", id);
    }

    private static String toJsonString(Object payload) {
        if (payload == null) {
            return "{}";
        }
        // jOOQ JSONB sütunlarını org.jooq.JSONB olarak döner
        if (payload instanceof org.jooq.JSONB jsonb) {
            return jsonb.data();
        }
        if (payload instanceof String s) {
            return s;
        }
        return String.valueOf(payload);
    }
}
