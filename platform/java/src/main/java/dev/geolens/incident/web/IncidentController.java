package dev.geolens.incident.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incident yönetimi REST controller'ı — Go {@code incident.handler} portu (R15).
 * <p>Route'lar (go cmd/api): GET /v1/incidents/events, POST /v1/incidents/events,
 * PUT /v1/incidents/events/{incidentId}.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; oluşturma sonrası
 * {@code incident.opened} olayı outbox üzerinden {@code q:governance} stream'ine taşınır (O-6).
 */
@RestController
@RequestMapping("/v1/incidents")
public class IncidentController {

    private static final Set<String> VALID_STATUS = Set.of("open", "investigating", "mitigated", "resolved", "closed");
    private static final Set<String> VALID_UPDATE_STATUS = Set.of("investigating", "mitigated", "resolved", "closed");
    private static final Set<String> VALID_SEVERITY = Set.of("critical", "high", "medium", "low", "info");
    private static final Set<String> VALID_CATEGORY = Set.of("outage", "degradation", "bias", "injection",
            "data_leak", "policy_violation", "other");

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public IncidentController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- List ----------

    @GetMapping("/events")
    public ResponseEntity<?> listIncidents(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String severity) {
        if (limit < 1 || limit > 100) {
            limit = 20;
        }
        String statusFilter = status == null ? "" : status;
        String severityFilter = severity == null ? "" : severity;

        // LIMIT+1 deseni — has_more için (Go ile aynı)
        StringBuilder query = new StringBuilder("""
                SELECT id, severity, category, title, status, source, entity_id, assigned_to,
                       severity_score, occurred_at, resolved_at, created_at
                FROM incident.events WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (VALID_STATUS.contains(statusFilter)) {
            query.append(" AND status = ?");
            args.add(statusFilter);
        }
        if (VALID_SEVERITY.contains(severityFilter)) {
            query.append(" AND severity = ?");
            args.add(severityFilter);
        }
        query.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(limit + 1);

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of(
                    "incidents", List.of(), "has_more", false, "count", 0));
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<Map<String, Object>> incidents = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            incidents.add(toIncident(r));
        }

        int openCount = count("SELECT COUNT(*) FROM incident.events WHERE tenant_id = ? AND status NOT IN ('resolved','closed')", tenantId);
        int criticalCount = count("SELECT COUNT(*) FROM incident.events WHERE tenant_id = ? AND severity IN ('critical','high') AND status NOT IN ('resolved','closed')", tenantId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("incidents", incidents);
        body.put("count", incidents.size());
        body.put("has_more", hasMore);
        body.put("open_count", openCount);
        body.put("critical_count", criticalCount);
        return ResponseEntity.ok(body);
    }

    // ---------- Create ----------

    @PostMapping("/events")
    public ResponseEntity<?> createIncident(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestBody CreateIncidentRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.title() == null || req.title().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "title gerekli");
        }

        String severity = req.severity();
        if (!VALID_SEVERITY.contains(severity)) {
            severity = "medium";
        }
        String category = req.category();
        if (!VALID_CATEGORY.contains(category)) {
            category = "other";
        }

        String incidentId = Ulid.generate();
        Instant now = Instant.now();

        try {
            dsl.execute("""
                    INSERT INTO incident.events (id, tenant_id, severity, category, title, description,
                                                status, source, entity_id, assigned_to, severity_score, occurred_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'open', ?, ?, ?, ?, ?)
                    """, incidentId, tenantId, severity, category, req.title(), nz(req.description()),
                    nz(req.source()), nz(req.entityId()), nz(req.assignedTo()), req.severityScore(),
                    java.sql.Timestamp.from(now));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "incident kaydedilemedi");
        }

        // O-6: IncidentOpened olayını outbox üzerinden taşı (doğrudan DB yazımı yerine)
        try {
            enqueueOpened(tenantId, incidentId, severity, category, req.title(), nz(req.source()));
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("incident olayı outbox'a yazılamadı"); akış devam eder
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("incident_id", incidentId);
        body.put("severity", severity);
        body.put("title", req.title());
        body.put("status", "open");
        body.put("severity_score", req.severityScore());
        body.put("created_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- Update ----------

    @PutMapping("/events/{incidentId}")
    public ResponseEntity<?> updateIncident(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String incidentId,
                                            @RequestBody UpdateIncidentRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        String status = nz(req.status());
        if (!status.isEmpty() && !VALID_UPDATE_STATUS.contains(status)) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz durum");
        }

        boolean resolvedSet = "resolved".equals(status) || "closed".equals(status);

        int updated;
        try {
            updated = dsl.execute("""
                    UPDATE incident.events
                    SET status = CASE WHEN ? != '' THEN ? ELSE status END,
                        resolution = CASE WHEN ? != '' THEN ? ELSE resolution END,
                        assigned_to = CASE WHEN ? != '' THEN ? ELSE assigned_to END,
                        resolved_at = CASE WHEN ? THEN now() ELSE resolved_at END,
                        updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    """, status, status, nz(req.resolution()), nz(req.resolution()),
                    nz(req.assignedTo()), nz(req.assignedTo()), resolvedSet, incidentId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "incident güncellenemedi");
        }
        if (updated == 0) {
            return error(HttpStatus.NOT_FOUND, "incident bulunamadı");
        }

        return ResponseEntity.ok(Map.of(
                "incident_id", incidentId,
                "status", status));
    }

    // ---------- yardımcılar ----------

    private void enqueueOpened(String tenantId, String incidentId, String severity, String category,
                               String title, String source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incident_id", incidentId);
        payload.put("severity", severity);
        payload.put("category", category);
        payload.put("title", title);
        payload.put("status", "open");
        payload.put("source", source);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        dsl.execute("""
                INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                VALUES (?, 'incident.opened', 'q:governance', ?::jsonb, ?, ?, now())
                """, Ulid.generate(), json, tenantId, "incident:opened:" + incidentId);
    }

    /** Go {@code QueryRow.Scan(&count)} karşılığı — hata varsa 0 döner (Go'da hata yok sayılır). */
    private int count(String sql, Object... args) {
        try {
            Record r = dsl.fetchOne(sql, args);
            if (r == null) {
                return 0;
            }
            Object v = r.get(0);
            return v == null ? 0 : ((Number) v).intValue();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Map<String, Object> toIncident(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", str(r.get("id")));
        m.put("severity", str(r.get("severity")));
        m.put("category", str(r.get("category")));
        m.put("title", str(r.get("title")));
        m.put("status", str(r.get("status")));
        m.put("source", str(r.get("source")));
        m.put("entity_id", str(r.get("entity_id")));
        m.put("assigned_to", str(r.get("assigned_to")));
        m.put("severity_score", num(r.get("severity_score")));
        m.put("occurred_at", str(r.get("occurred_at")));
        if (r.get("resolved_at") != null) {
            m.put("resolved_at", str(r.get("resolved_at")));
        }
        m.put("created_at", str(r.get("created_at")));
        return m;
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

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
