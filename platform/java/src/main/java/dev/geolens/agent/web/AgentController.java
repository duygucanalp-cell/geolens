package dev.geolens.agent.web;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent tracing REST controller'ı — Go {@code agent.handler} portu (R8).
 * <p>Route'lar (go cmd/api, /v1/workspaces/{ws} altında): POST /agents/traces,
 * GET /agents/traces/{traceId}, GET /agents/traces, POST /agents/traces/{traceId}/steps,
 * POST /agents/traces/{traceId}/complete.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; workspace yalnızca URL'de bulunur
 * (Go handler'ı workspace'i kullanmaz — birebir korundu).
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/agents")
public class AgentController {

    private final DSLContext dsl;

    public AgentController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- StartTrace ----------

    @PostMapping("/traces")
    public ResponseEntity<?> startTrace(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody StartTraceRequest req) {
        if (req.agentName() == null || req.agentName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "agent_name gerekli");
        }

        String traceId = Ulid.generate();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            dsl.execute("""
                    INSERT INTO agent.traces (id, tenant_id, agent_name, workflow_name, status, started_at)
                    VALUES (?, ?, ?, ?, 'running', ?)
                    """, traceId, tenantId, req.agentName(),
                    req.workflowName() == null ? "" : req.workflowName(), now);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "trace başlatılamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", traceId);
        body.put("agent_name", req.agentName());
        body.put("workflow_name", req.workflowName() == null ? "" : req.workflowName());
        body.put("status", "running");
        body.put("started_at", now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- GetTrace ----------

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<?> getTrace(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String traceId) {
        Record t;
        try {
            t = dsl.fetchOne("""
                    SELECT agent_name, workflow_name, status, total_steps, completed_steps,
                           total_duration_ms, started_at, completed_at
                    FROM agent.traces WHERE id = ? AND tenant_id = ?
                    """, traceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "trace bulunamadı");
        }
        if (t == null) {
            return error(HttpStatus.NOT_FOUND, "trace bulunamadı");
        }

        List<Map<String, Object>> steps;
        try {
            steps = dsl.fetch("""
                    SELECT id, step_name, agent_name, input, output, status, duration_ms,
                           COALESCE(error_message, '') AS error_message, started_at, completed_at
                    FROM agent.steps WHERE trace_id = ? ORDER BY started_at NULLS LAST
                    """, traceId).intoMaps();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "adımlar alınamadı");
        }

        List<Map<String, Object>> stepResults = new ArrayList<>();
        for (Map<String, Object> r : steps) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("step_id", str(r.get("id")));
            s.put("step_name", str(r.get("step_name")));
            s.put("agent", str(r.get("agent_name")));
            s.put("input", str(r.get("input")));
            s.put("output", str(r.get("output")));
            s.put("status", str(r.get("status")));
            s.put("duration_ms", num(r.get("duration_ms")));
            putOmitEmpty(s, "error_message", r.get("error_message"));
            putOmitEmpty(s, "started_at", tsStr(r.get("started_at")));
            putOmitEmpty(s, "completed_at", tsStr(r.get("completed_at")));
            stepResults.add(s);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", traceId);
        body.put("agent_name", str(t.get("agent_name")));
        body.put("workflow_name", str(t.get("workflow_name")));
        body.put("status", str(t.get("status")));
        body.put("total_steps", num(t.get("total_steps")));
        body.put("completed_steps", num(t.get("completed_steps")));
        body.put("total_duration_ms", num(t.get("total_duration_ms")));
        body.put("started_at", tsStr(t.get("started_at")));
        body.put("steps", stepResults);
        return ResponseEntity.ok(body);
    }

    // ---------- RecordStep ----------

    @PostMapping("/traces/{traceId}/steps")
    public ResponseEntity<?> recordStep(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String traceId,
                                        @RequestBody RecordStepRequest req) {
        // Trace varlığı + tenant kontrolü
        String traceStatus;
        try {
            Record r = dsl.fetchOne("""
                    SELECT status FROM agent.traces WHERE id = ? AND tenant_id = ?
                    """, traceId, tenantId);
            if (r == null) {
                return error(HttpStatus.NOT_FOUND, "trace bulunamadı");
            }
            traceStatus = str(r.get(0));
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "trace bulunamadı");
        }

        if ("completed".equals(traceStatus) || "cancelled".equals(traceStatus)) {
            return error(HttpStatus.CONFLICT, "tamamlanmış trace'e adım eklenemez");
        }

        // Geçerli step_status
        String stepStatus = req.status();
        if (stepStatus == null || (!"running".equals(stepStatus) && !"completed".equals(stepStatus)
                && !"failed".equals(stepStatus))) {
            stepStatus = "running";
        }

        String stepId = Ulid.generate();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int durationMs = req.durationMs() == null ? 0 : req.durationMs();

        try {
            dsl.execute("""
                    INSERT INTO agent.steps (id, trace_id, tenant_id, step_name, agent_name, input, output,
                                             status, duration_ms, error_message, started_at, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            CASE WHEN ? IN ('completed','failed') THEN ? ELSE NULL END)
                    """, stepId, traceId, tenantId, nz(req.stepName()), nz(req.agentName()),
                    nz(req.input()), nz(req.output()), stepStatus, durationMs,
                    nz(req.errorMessage()), now, stepStatus, now);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "adım kaydedilemedi");
        }

        // Trace istatistiklerini güncelle (hata non-fatal — Go birebir)
        try {
            dsl.execute("""
                    UPDATE agent.traces
                    SET total_steps = total_steps + 1,
                        completed_steps = completed_steps + CASE WHEN ? IN ('completed','failed') THEN 1 ELSE 0 END,
                        total_duration_ms = total_duration_ms + ?,
                        status = CASE WHEN ? = 'failed' THEN 'failed' ELSE status END
                    WHERE id = ? AND tenant_id = ?
                    """, stepStatus, durationMs, stepStatus, traceId, tenantId);
        } catch (RuntimeException e) {
            // warn — yanıtı etkilemez
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("step_id", stepId);
        body.put("trace_id", traceId);
        body.put("step_name", nz(req.stepName()));
        body.put("status", stepStatus);
        body.put("duration_ms", durationMs);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- CompleteTrace ----------

    @PostMapping("/traces/{traceId}/complete")
    public ResponseEntity<?> completeTrace(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @PathVariable String traceId,
                                           @RequestBody CompleteTraceRequest req) {
        String finalStatus = req.status();
        if (finalStatus == null || (!"completed".equals(finalStatus) && !"failed".equals(finalStatus)
                && !"cancelled".equals(finalStatus))) {
            finalStatus = "completed";
        }

        int rows;
        try {
            rows = dsl.execute("""
                    UPDATE agent.traces
                    SET status = ?, completed_at = NOW()
                    WHERE id = ? AND tenant_id = ? AND status = 'running'
                    """, finalStatus, traceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "trace güncellenemedi");
        }

        if (rows == 0) {
            return error(HttpStatus.NOT_FOUND, "trace bulunamadı veya zaten tamamlanmış");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", traceId);
        body.put("status", finalStatus);
        return ResponseEntity.ok(body);
    }

    // ---------- ListTraces ----------

    @GetMapping("/traces")
    public ResponseEntity<?> listTraces(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "limit", required = false) String limitStr,
                                        @RequestParam(value = "status", required = false) String statusFilter,
                                        @RequestParam(value = "offset", required = false) String offsetStr) {
        int limit;
        try {
            limit = Integer.parseInt(limitStr);
        } catch (RuntimeException e) {
            limit = 20;
        }
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        int offset;
        try {
            offset = Integer.parseInt(offsetStr);
        } catch (RuntimeException e) {
            offset = 0;
        }
        if (offset < 0) {
            offset = 0;
        }

        boolean validStatus = "running".equals(statusFilter) || "completed".equals(statusFilter)
                || "failed".equals(statusFilter) || "cancelled".equals(statusFilter);

        StringBuilder query = new StringBuilder("""
                SELECT id, agent_name, workflow_name, status, total_steps, completed_steps,
                       total_duration_ms, started_at, completed_at
                FROM agent.traces WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (validStatus) {
            query.append(" AND status = ?");
            args.add(statusFilter);
        }
        query.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "trace listesi alınamadı");
        }

        List<Map<String, Object>> traces = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("trace_id", str(r.get("id")));
            t.put("agent_name", str(r.get("agent_name")));
            t.put("workflow_name", str(r.get("workflow_name")));
            t.put("status", str(r.get("status")));
            t.put("total_steps", num(r.get("total_steps")));
            t.put("completed_steps", num(r.get("completed_steps")));
            t.put("total_duration_ms", num(r.get("total_duration_ms")));
            t.put("started_at", tsStr(r.get("started_at")));
            putOmitEmpty(t, "completed_at", tsStr(r.get("completed_at")));
            traces.add(t);
        }

        // Toplam sayı (hata yok sayılır — Go birebir)
        int total = 0;
        StringBuilder countQuery = new StringBuilder("SELECT COUNT(*) FROM agent.traces WHERE tenant_id = ?");
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(tenantId);
        if (validStatus) {
            countQuery.append(" AND status = ?");
            countArgs.add(statusFilter);
        }
        try {
            Record c = dsl.fetchOne(countQuery.toString(), countArgs.toArray());
            if (c != null) {
                total = num(c.get(0));
            }
        } catch (RuntimeException e) {
            // yok sayılır
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traces", traces);
        body.put("total", total);
        body.put("limit", limit);
        body.put("offset", offset);
        return ResponseEntity.ok(body);
    }

    // ---------- yardımcılar ----------

    /** Go {@code omitempty} — null/boş değerler JSON'da atlanır. */
    private static void putOmitEmpty(Map<String, Object> m, String key, Object v) {
        if (v != null && !String.valueOf(v).isEmpty()) {
            m.put(key, v);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String tsStr(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        return String.valueOf(o);
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
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
