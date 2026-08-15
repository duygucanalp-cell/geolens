package dev.geolens.incident.service;

import dev.geolens.common.ServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.incident.web.CreateIncidentRequest;
import dev.geolens.incident.web.UpdateIncidentRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incident yönetimi iş mantığı — Go {@code incident.handler} portu (R15).
 * <p>Listeleme, oluşturma ve güncelleme ile DB erişimini ve doğrulamayı içerir.
 * Oluşturma sonrası {@code incident.opened} olayı outbox üzerinden
 * {@code q:governance} stream'ine taşınır (O-6). Controller yalnızca HTTP katmanıdır.
 */
@Service
public class IncidentService {

    private static final Set<String> VALID_STATUS = Set.of("open", "investigating", "mitigated", "resolved", "closed");
    private static final Set<String> VALID_UPDATE_STATUS = Set.of("investigating", "mitigated", "resolved", "closed");
    private static final Set<String> VALID_SEVERITY = Set.of("critical", "high", "medium", "low", "info");
    private static final Set<String> VALID_CATEGORY = Set.of("outage", "degradation", "bias", "injection",
            "data_leak", "policy_violation", "other");

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public IncidentService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code listIncidents} karşılığı — sorgu hatasında boş yanıt döner. */
    public Map<String, Object> listIncidents(String tenantId, int limit, String status, String severity) {
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
            rows = list(query.toString(), args.toArray());
        } catch (RuntimeException e) {
            return Map.of("incidents", List.of(), "has_more", false, "count", 0);
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
        return body;
    }

    /** Go {@code createIncident} karşılığı — incident oluşturur, oluşturulan kaydı döner. */
    public Map<String, Object> createIncident(String tenantId, CreateIncidentRequest req) {
        if (req == null) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.title() == null || req.title().isBlank()) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "title gerekli");
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "incident kaydedilemedi");
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
        return body;
    }

    /** Go {@code updateIncident} karşılığı — incident durumunu günceller, sonucu döner. */
    public Map<String, Object> updateIncident(String tenantId, String incidentId, UpdateIncidentRequest req) {
        if (req == null) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        String status = nz(req.status());
        if (!status.isEmpty() && !VALID_UPDATE_STATUS.contains(status)) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz durum");
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "incident güncellenemedi");
        }
        if (updated == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "incident bulunamadı");
        }

        return Map.of(
                "incident_id", incidentId,
                "status", status);
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

    /** ADR-014: plain SQL satır erişimi — Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }
}
