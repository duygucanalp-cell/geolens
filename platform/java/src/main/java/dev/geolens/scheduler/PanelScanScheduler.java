package dev.geolens.scheduler;

import dev.geolens.engine.Registry;
import dev.geolens.measure.web.MeasureJob;
import dev.geolens.queue.OutboxWriter;
import dev.geolens.queue.QueueProperties;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Panel cron tarayıcı — Go {@code scheduler.scanAndEnqueue} portu (scheduler profilinde çalışır).
 * <p>Aktif panelleri tarar; ölçüm zamanı gelenlerin ({@code isDue}) markaları için her motorda
 * n=3 job'ı outbox'a kuyruğa alır ve {@code last_measured_at} günceller.
 */
@Component
@Profile("scheduler")
public class PanelScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(PanelScanScheduler.class);

    private static final String DEFAULT_PROMPT = "{brand_name} markası hakkında ne biliyorsun? Kaynak göstererek anlat.";

    private final DSLContext dsl;
    private final Registry engines;
    private final OutboxWriter outbox;

    public PanelScanScheduler(DSLContext dsl, Registry engines, OutboxWriter outbox) {
        this.dsl = dsl;
        this.engines = engines;
        this.outbox = outbox;
    }

    /** Go {@code runScheduler} ticker karşılığı — {@code queue.panel-scan-ms} aralığıyla tarar. */
    @Scheduled(fixedDelayString = "${queue.panel-scan-ms:60000}")
    public void scanAndEnqueue() {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT p.id, p.workspace_id, p.tenant_id,
                           COALESCE(ps.prompt_text, '') AS prompt_text,
                           p.schedule_cron,
                           COALESCE(p.last_measured_at, '1970-01-01'::timestamptz) AS last_measured_at
                    FROM config.panels p
                    LEFT JOIN config.prompt_sets ps ON ps.id = p.prompt_set_id
                    WHERE p.is_active = true
                    """).intoMaps();
        } catch (RuntimeException e) {
            log.error("zamanlayıcı tarama hatası", "error", e.getMessage());
            return;
        }

        List<String> engineNames = engines.list();
        if (engineNames.isEmpty()) {
            return;
        }

        int enqueued = 0;
        for (Map<String, Object> row : rows) {
            String panelId = String.valueOf(row.get("id"));
            String workspaceId = String.valueOf(row.get("workspace_id"));
            String tenantId = String.valueOf(row.get("tenant_id"));
            String promptText = row.get("prompt_text") == null ? "" : String.valueOf(row.get("prompt_text"));
            String scheduleCron = row.get("schedule_cron") == null ? "" : String.valueOf(row.get("schedule_cron"));
            OffsetDateTime lastMeasuredAt = toOffsetDateTime(row.get("last_measured_at"));

            // Zamanlama kontrolü: panel her zaman ölçülecek mi?
            if (!scheduleCron.isEmpty() && !CronDue.isDue(scheduleCron, lastMeasuredAt)) {
                continue;
            }

            // Panel'in markalarını getir
            List<PanelBrand> brands = getPanelBrands(panelId, workspaceId, tenantId);
            if (brands.isEmpty()) {
                continue;
            }

            // Varsayılan prompt
            if (promptText.isBlank()) {
                promptText = DEFAULT_PROMPT;
            }

            // Her marka için n=3 job oluştur
            for (PanelBrand brand : brands) {
                String actualPrompt = promptText.replace("{brand_name}", brand.name());
                for (String engineName : engineNames) {
                    for (int i = 0; i < 3; i++) {
                        String idempotencyKey = String.format("schedule:%s:%s:%s:%d", panelId, brand.id(), engineName, i);
                        MeasureJob job = new MeasureJob(brand.id(), brand.name(), brand.websiteUrl(),
                                panelId, workspaceId, tenantId, engineName, actualPrompt, i);
                        try {
                            outbox.enqueue("measurement.requested", QueueProperties.STREAM_MEASURE,
                                    job.toJson(), tenantId, idempotencyKey);
                            enqueued++;
                        } catch (RuntimeException e) {
                            log.warn("zamanlayıcı job ekleme hatası", "brand", brand.name(),
                                    "engine", engineName, "error", e.getMessage());
                        }
                    }
                }
            }

            // last_measured_at güncelle
            try {
                dsl.execute("UPDATE config.panels SET last_measured_at = now() WHERE id = ?", panelId);
            } catch (RuntimeException e) {
                log.warn("panel last_measured_at güncelleme hatası", "panel", panelId, "error", e.getMessage());
            }
        }

        if (enqueued > 0) {
            log.info("zamanlayıcı job'ları kuyruğa ekledi", "count", enqueued);
        }
    }

    /** Go {@code getPanelBrands} karşılığı — panele bağlı aktif markalar. */
    private List<PanelBrand> getPanelBrands(String panelId, String workspaceId, String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT b.id, b.name, b.website_url
                    FROM config.brands b
                    JOIN config.panel_brands pb ON pb.brand_id = b.id
                    WHERE pb.panel_id = ? AND pb.workspace_id = ? AND pb.tenant_id = ? AND b.is_active = true
                    """, panelId, workspaceId, tenantId).intoMaps();
        } catch (RuntimeException e) {
            log.error("panel marka sorgu hatası", "panel", panelId, "error", e.getMessage());
            return List.of();
        }
        List<PanelBrand> brands = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            brands.add(new PanelBrand(String.valueOf(row.get("id")),
                    String.valueOf(row.get("name")),
                    row.get("website_url") == null ? "" : String.valueOf(row.get("website_url"))));
        }
        return brands;
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt;
        }
        return OffsetDateTime.parse("1970-01-01T00:00:00Z");
    }

    /** Go {@code panelBrand} karşılığı. */
    private record PanelBrand(String id, String name, String websiteUrl) {
    }
}
