package dev.geolens.scheduler;

import dev.geolens.delivery.DeliveryService;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Haftalık digest gönderimi — Go {@code runDigestScheduler}/{@code processDigests} portu
 * (scheduler profilinde çalışır). Her Pazartesi 09:00 UTC'de digest etkin çalışma alanlarına
 * haftalık özet e-postası gönderir.
 */
@Component
@Profile("scheduler")
public class WeeklyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestScheduler.class);

    private final DSLContext dsl;
    private final DeliveryService deliveryService;

    public WeeklyDigestScheduler(DSLContext dsl, DeliveryService deliveryService) {
        this.dsl = dsl;
        this.deliveryService = deliveryService;
    }

    /** Go cron "0 9 * * 1" (UTC) karşılığı — 6 alanlı Spring ifadesi. */
    @Scheduled(cron = "0 0 9 * * 1", zone = "UTC")
    public void processDigests() {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT workspace_id, tenant_id
                    FROM delivery.notification_settings
                    WHERE digest_enabled = true
                    """).intoMaps();
        } catch (RuntimeException e) {
            log.error("digest sorgu hatası", "error", e.getMessage());
            return;
        }

        int sent = 0;
        for (Map<String, Object> row : rows) {
            String workspaceId = String.valueOf(row.get("workspace_id"));
            String tenantId = String.valueOf(row.get("tenant_id"));
            try {
                deliveryService.sendWeeklyDigest(workspaceId, tenantId);
                sent++;
            } catch (RuntimeException e) {
                log.error("digest gönderme hatası", "workspace", workspaceId, "error", e.getMessage());
            }
        }

        if (sent > 0) {
            log.info("haftalık digest'ler gönderildi", "count", sent);
        }
    }
}
