package dev.geolens.queue;

import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox'a olay yazar — Go {@code queue.EnqueueEvent} / {@code measure.EnqueueMeasurement} karşılığı.
 * <p>Dispatcher ({@link OutboxDispatcher}) bekleyen kayıtları ilgili Redis Stream'ine iletir.
 * idempotency_key çakışması sessizce yok sayılır (ON CONFLICT DO NOTHING — Go'da loglanıp devam edilir).
 */
@Component
public class OutboxWriter {

    private final DSLContext dsl;

    public OutboxWriter(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Outbox'a tek olay ekler. idempotencyKey boşsa benzersizlik kısıtı uygulanmaz. */
    public void enqueue(String eventType, String stream, String payload, String tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            dsl.execute("""
                    INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                    VALUES (?, ?, ?, ?::jsonb, ?, NULL, now())
                    """, Ulid.generate(), eventType, stream, payload, tenantId);
            return;
        }
        dsl.execute("""
                INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, now())
                ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                """, Ulid.generate(), eventType, stream, payload, tenantId, idempotencyKey);
    }
}
