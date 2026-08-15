package dev.geolens.agent.service;

import dev.geolens.common.ServiceException;

import dev.geolens.agent.web.CompleteTraceRequest;
import dev.geolens.agent.web.RecordStepRequest;
import dev.geolens.agent.web.StartTraceRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent tracing iş mantığı — Go {@code agent.handler} portu (R8).
 * <p>Trace başlatma, trace ve adım sorgulama, adım kaydı, trace tamamlama ve trace
 * listeleme işlemlerini yapar. Controller yalnızca HTTP katmanıdır; bu sınıf DB
 * erişimini (DSLContext) ve iş doğrulamalarını içerir.
 */
@Service
public class AgentService {

    private final DSLContext dsl;

    public AgentService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code StartTrace} karşılığı — yeni trace oluşturur ve gövdeyi döner. */
    public Map<String, Object> startTrace(String tenantId, StartTraceRequest req) {
        String traceId = Ulid.generate();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            dsl.execute("""
                    INSERT INTO agent.traces (id, tenant_id, agent_name, workflow_name, status, started_at)
                    VALUES (?, ?, ?, ?, 'running', ?)
                    """, traceId, tenantId, req.agentName(),
                    req.workflowName() == null ? "" : req.workflowName(), now);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "trace başlatılamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", traceId);
        body.put("agent_name", req.agentName());
        body.put("workflow_name", req.workflowName() == null ? "" : req.workflowName());
        body.put("status", "running");
        body.put("started_at", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return body;
    }

    /** Go {@code GetTrace} karşılığı — trace ve adımlarını döner. */
    public Map<String, Object> getTrace(String tenantId, String traceId) {
        Record t;
        try {
            t = dsl.fetchOne("""
                    SELECT agent_name, workflow_name, status, total_steps, completed_steps,
                           total_duration_ms, started_at, completed_at
                    FROM agent.traces WHERE id = ? AND tenant_id = ?
                    """, traceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı");
        }
        if (t == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı");
        }

        List<Map<String, Object>> steps;
        try {
            steps = dsl.fetch("""
                    SELECT id, step_name, agent_name, input, output, status, duration_ms,
                           COALESCE(error_message, '') AS error_message, started_at, completed_at
                    FROM agent.steps WHERE trace_id = ? ORDER BY started_at NULLS LAST
                    """, traceId).intoMaps();
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "adımlar alınamadı");
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
        return body;
    }

    /** Go {@code RecordStep} karşılığı — adımı kaydeder, trace istatistiklerini günceller. */
    public Map<String, Object> recordStep(String tenantId, String traceId, RecordStepRequest req) {
        // Trace varlığı + tenant kontrolü
        String traceStatus;
        try {
            Record r = dsl.fetchOne("""
                    SELECT status FROM agent.traces WHERE id = ? AND tenant_id = ?
                    """, traceId, tenantId);
            if (r == null) {
                throw new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı");
            }
            traceStatus = str(r.get(0));
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı");
        }

        if ("completed".equals(traceStatus) || "cancelled".equals(traceStatus)) {
            throw new ServiceException(HttpStatus.CONFLICT, "tamamlanmış trace'e adım eklenemez");
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "adım kaydedilemedi");
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
        return body;
    }

    /** Go {@code CompleteTrace} karşılığı — running trace'i tamamlar. */
    public Map<String, Object> completeTrace(String tenantId, String traceId, CompleteTraceRequest req) {
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "trace güncellenemedi");
        }

        if (rows == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı veya zaten tamamlanmış");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", traceId);
        body.put("status", finalStatus);
        return body;
    }

    /** Go {@code ListTraces} karşılığı — trace listesini ve toplam sayıyı döner. */
    public Map<String, Object> listTraces(String tenantId, int limit, String statusFilter, int offset) {
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "trace listesi alınamadı");
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
        return body;
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
}
