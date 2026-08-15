package dev.geolens.prompt.service;

import dev.geolens.common.ServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.prompt.PromptAuditResult;
import dev.geolens.prompt.PromptAuditor;
import dev.geolens.prompt.web.RunAuditRequest;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prompt denetimi iş mantığı — Go {@code prompt.handler} portu (R9).
 * <p>Denetim çalıştırma, geçmiş listeleme ve tek kayıt sorgulamasını yapar;
 * DB (plain SQL) ve engine çağrılarını içerir. Controller yalnızca HTTP katmanıdır.
 */
@Service
public class PromptService {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public PromptService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code RunAudit} karşılığı — denetimi çalıştırır, kaydı DB'ye yazar, yanıtı döner. */
    public Map<String, Object> runAudit(String tenantId, RunAuditRequest req) {
        String promptText = nz(req.promptText());
        String engineName = req.engineName() == null || req.engineName().isBlank() ? "generic" : req.engineName();

        String auditId = Ulid.generate();
        Instant now = Instant.now();

        // Prompt denetimini gerçekleştir
        PromptAuditResult result = PromptAuditor.audit(promptText);

        // Token ve latency: engine registry yoksa simülasyon (Go fallback)
        int tokenCount = promptText.length() / 4;
        if (tokenCount < 1) {
            tokenCount = 1;
        }
        int latencyMs = 100 + ThreadLocalRandom.current().nextInt(900);

        // Sonucu DB'ye yaz
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("engine", engineName);
            metadata.put("score", result.score());
            metadata.put("issue_count", result.issues().size());
            metadata.put("audited_at", DateTimeFormatter.ISO_INSTANT.format(now));

            dsl.execute("""
                    INSERT INTO prompt.audits (id, tenant_id, prompt_id, prompt_text, engine_name, status, score, token_count, latency_ms, issues, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                    """, auditId, tenantId, nz(req.promptId()), promptText, engineName,
                    result.status(), result.score(), tokenCount, latencyMs,
                    toJson(result.issues()), toJson(metadata));
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim kaydedilemedi");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("audit_id", auditId);
        resp.put("prompt_id", nz(req.promptId()));
        resp.put("prompt_text", promptText);
        resp.put("engine_name", engineName);
        resp.put("status", result.status());
        resp.put("score", result.score());
        resp.put("token_count", tokenCount);
        resp.put("latency_ms", latencyMs);
        resp.put("issues", result.issues());
        return resp;
    }

    /** Go {@code ListAudits} karşılığı — denetim geçmişini döner; sorgu hatasında 500. */
    public List<Map<String, Object>> listAudits(String tenantId, int limit, int offset,
                                                String statusFilter, String engineFilter) {
        StringBuilder query = new StringBuilder("""
                SELECT id, COALESCE(prompt_id, ''), prompt_text, engine_name, status, score, token_count, latency_ms, issues, created_at
                FROM prompt.audits WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        int paramIdx = 2;

        if ("passed".equals(statusFilter) || "flagged".equals(statusFilter) || "failed".equals(statusFilter)) {
            query.append(" AND status = $").append(paramIdx);
            args.add(statusFilter);
            paramIdx++;
        }
        if (engineFilter != null && !engineFilter.isBlank()) {
            query.append(" AND engine_name = $").append(paramIdx);
            args.add(engineFilter);
            paramIdx++;
        }

        query.append(" ORDER BY created_at DESC LIMIT $").append(paramIdx)
                .append(" OFFSET $").append(paramIdx + 1);
        args.add(limit);
        args.add(offset);

        List<Map<String, Object>> rows;
        try {
            rows = list(query.toString(), args.toArray());
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim geçmişi alınamadı");
        }

        List<Map<String, Object>> audits = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("prompt_id", str(r.get("prompt_id")));
            item.put("prompt_text", str(r.get("prompt_text")));
            item.put("engine_name", str(r.get("engine_name")));
            item.put("status", str(r.get("status")));
            item.put("score", num(r.get("score")));
            item.put("token_count", intNum(r.get("token_count")));
            item.put("latency_ms", intNum(r.get("latency_ms")));
            item.put("issues", parseJson(r.get("issues")));
            item.put("created_at", str(r.get("created_at")));
            audits.add(item);
        }
        return audits;
    }

    /** Go {@code GetAudit} karşılığı — tek denetim kaydını döner; bulunamazsa 404. */
    public Map<String, Object> getAudit(String tenantId, String auditId) {
        Map<String, Object> r;
        try {
            r = map("""
                    SELECT COALESCE(prompt_id, ''), prompt_text, engine_name, status, score, token_count, latency_ms, issues, metadata, created_at
                    FROM prompt.audits WHERE id = ? AND tenant_id = ?
                    """, auditId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "denetim bulunamadı");
        }
        if (r == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "denetim bulunamadı");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("audit_id", auditId);
        resp.put("prompt_id", str(r.get("prompt_id")));
        resp.put("prompt_text", str(r.get("prompt_text")));
        resp.put("engine_name", str(r.get("engine_name")));
        resp.put("status", str(r.get("status")));
        resp.put("score", num(r.get("score")));
        resp.put("token_count", intNum(r.get("token_count")));
        resp.put("latency_ms", intNum(r.get("latency_ms")));
        resp.put("issues", parseJson(r.get("issues")));
        resp.put("metadata", parseJson(r.get("metadata")));
        resp.put("created_at", str(r.get("created_at")));
        return resp;
    }

    // ---------- yardımcılar ----------

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return o instanceof List ? "[]" : "{}";
        }
    }

    private Object parseJson(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            try {
                return mapper.readValue(s, Object.class);
            } catch (Exception e) {
                return o;
            }
        }
        return o;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int intNum(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
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

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }
}
