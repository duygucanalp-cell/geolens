package dev.geolens.pilot.web;

import dev.geolens.pilot.PilotTenant;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kurumsal Pilot REST controller'ı — Go {@code pilot.handler} portu (K4).
 * <p>Route'lar (go cmd/api): GET /v1/pilot/status, POST /v1/pilot/enroll,
 * POST /v1/pilot/extend, POST /v1/pilot/cancel, GET /v1/pilot/tenants.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; 90 günlük deneme süresi,
 * pilot standartlarının üzerinde limitler (10 workspace / 5 engine) ve
 * otomatik ücretliye geçiş (auto_convert) sunar.
 */
@RestController
@RequestMapping("/v1/pilot")
public class PilotController {

    private final DSLContext dsl;

    public PilotController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- GetStatus ----------

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestHeader("X-Tenant-ID") String tenantId) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    SELECT pt.id, pt.tenant_id, pt.program_name, pt.trial_ends_at,
                        pt.max_workspaces, pt.max_engines, pt.support_level,
                        pt.contact_email, pt.notes, pt.auto_convert, pt.status,
                        pt.created_at
                    FROM identity.tenants t
                    LEFT JOIN identity.pilot_tenants pt ON pt.tenant_id = t.id
                    WHERE t.id = ?
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("enrolled", false, "message", "Bu tenant pilot programına kayıtlı değil"));
        }
        if (rec == null) {
            return ResponseEntity.ok(Map.of("enrolled", false, "message", "Bu tenant pilot programına kayıtlı değil"));
        }
        Map<String, Object> r = rec.intoMap();
        PilotTenant p = toPilotTenant(r);

        int daysLeft = 0;
        if (p.trialEndsAt() != null && !p.trialEndsAt().isBlank()) {
            try {
                Instant endTime = Instant.parse(p.trialEndsAt());
                long millis = endTime.toEpochMilli() - System.currentTimeMillis();
                daysLeft = (int) Math.floor(millis / (1000.0 * 3600 * 24));
                if (daysLeft < 0) {
                    daysLeft = 0;
                }
            } catch (Exception e) {
                // Go'da parse hatası yok sayılır (0 kalır)
            }
        }

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("max_workspaces", p.maxWorkspaces());
        features.put("max_engines", p.maxEngines());
        features.put("support_level", p.supportLevel());
        features.put("premium_support", "premium".equals(p.supportLevel()));
        features.put("extended_trial", true);
        features.put("priority_onboarding", true);
        features.put("dedicated_slack_channel", "premium".equals(p.supportLevel()));
        features.put("monthly_business_review", true);
        features.put("early_access_features", true);
        features.put("custom_integration_support", "premium".equals(p.supportLevel()));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("enrolled", true);
        resp.put("program", p);
        resp.put("days_remaining", daysLeft);
        resp.put("features", features);
        return ResponseEntity.ok(resp);
    }

    // ---------- Enroll ----------

    @PostMapping("/enroll")
    public ResponseEntity<?> enroll(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody EnrollRequest req) {
        String programName = req.programName() == null || req.programName().isBlank() ? "Kurumsal Pilot Programı" : req.programName();
        String supportLevel = req.supportLevel() == null || req.supportLevel().isBlank() ? "standard" : req.supportLevel();
        String contactEmail = req.contactEmail() == null || req.contactEmail().isBlank() ? "" : req.contactEmail();

        OffsetDateTime trialEnd = OffsetDateTime.now(ZoneOffset.UTC).plusDays(90);

        try {
            dsl.execute("""
                    INSERT INTO identity.pilot_tenants (tenant_id, program_name, trial_ends_at,
                        max_workspaces, max_engines, support_level, contact_email, notes, auto_convert, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id) DO UPDATE SET
                        program_name = EXCLUDED.program_name,
                        support_level = EXCLUDED.support_level,
                        contact_email = EXCLUDED.contact_email,
                        notes = EXCLUDED.notes,
                        status = 'active'
                    """, tenantId, programName, trialEnd,
                    10, // max_workspaces — pilot için standartın üzerinde
                    5,  // max_engines — pilot için standartın üzerinde
                    supportLevel, contactEmail, nz(req.notes()),
                    true, // auto_convert — pilot bitince otomatik ücretliye geç
                    "active");
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "pilot programa kayıt başarısız");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "kayıtlı");
        resp.put("program", programName);
        resp.put("trial_ends_at", trialEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        resp.put("support_level", supportLevel);
        resp.put("auto_convert", true);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ---------- ExtendTrial ----------

    @PostMapping("/extend")
    public ResponseEntity<?> extendTrial(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody ExtendTrialRequest req) {
        if (req.extraDays() < 1 || req.extraDays() > 365) {
            return error(HttpStatus.BAD_REQUEST, "ek süre 1-365 gün arasında olmalıdır");
        }

        try {
            dsl.execute("""
                    UPDATE identity.pilot_tenants
                    SET trial_ends_at = GREATEST(trial_ends_at, now()) + (? || ' days')::INTERVAL,
                        updated_at = now()
                    WHERE tenant_id = ?
                    """, req.extraDays(), tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "süre uzatılamadı");
        }

        return ResponseEntity.ok(Map.of("status", "pilot süresi uzatıldı"));
    }

    // ---------- Cancel ----------

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            dsl.execute("""
                    UPDATE identity.pilot_tenants SET status = 'cancelled', auto_convert = false, updated_at = now()
                    WHERE tenant_id = ?
                    """, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "pilot iptal edilemedi");
        }

        return ResponseEntity.ok(Map.of("status", "pilot iptal edildi"));
    }

    // ---------- ListAll ----------

    @GetMapping("/tenants")
    public ResponseEntity<?> listAll() {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT pt.id, pt.tenant_id, t.name, pt.program_name, pt.trial_ends_at,
                        pt.support_level, pt.status, pt.created_at
                    FROM identity.pilot_tenants pt
                    JOIN identity.tenants t ON t.id = pt.tenant_id
                    ORDER BY pt.created_at DESC
                    """).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("pilots", List.of()));
        }

        List<Map<String, Object>> pilots = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("tenant_id", str(r.get("tenant_id")));
            item.put("tenant_name", str(r.get("name")));
            item.put("program_name", str(r.get("program_name")));
            item.put("trial_ends_at", str(r.get("trial_ends_at")));
            item.put("support_level", str(r.get("support_level")));
            item.put("status", str(r.get("status")));
            item.put("created_at", str(r.get("created_at")));
            pilots.add(item);
        }
        return ResponseEntity.ok(Map.of("pilots", pilots));
    }

    // ---------- yardımcılar ----------

    private static PilotTenant toPilotTenant(Map<String, Object> r) {
        return new PilotTenant(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("program_name")),
                str(r.get("trial_ends_at")), intNum(r.get("max_workspaces")), intNum(r.get("max_engines")),
                str(r.get("support_level")), str(r.get("contact_email")), str(r.get("notes")),
                r.get("auto_convert") != null && Boolean.TRUE.equals(r.get("auto_convert")),
                str(r.get("status")), str(r.get("created_at")));
    }

    private static int intNum(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
