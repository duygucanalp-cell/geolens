package dev.geolens.prompt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.prompt.PromptAuditResult;
import dev.geolens.prompt.PromptAuditor;
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prompt Audit REST controller'ı — Go {@code prompt.handler} portu (R9).
 * <p>Route'lar (go cmd/api): POST /v1/prompts/audit, GET /v1/prompts/audits,
 * GET /v1/prompts/audits/{auditId}.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; token/latency, engine registry
 * yoksa simülasyonla ölçülür (Go fallback davranışı birebir).
 */
@RestController
@RequestMapping("/v1/prompts")
public class PromptController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public PromptController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- RunAudit ----------

    @PostMapping("/audit")
    public ResponseEntity<?> runAudit(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody RunAuditRequest req) {
        String promptText = req.promptText() == null ? "" : req.promptText();
        if (promptText.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "prompt_text gerekli");
        }
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
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "denetim kaydedilemedi");
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
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // ---------- ListAudits ----------

    @GetMapping("/audits")
    public ResponseEntity<?> listAudits(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "limit", required = false) String limitStr,
                                        @RequestParam(value = "offset", required = false) String offsetStr,
                                        @RequestParam(value = "status", required = false) String statusFilter,
                                        @RequestParam(value = "engine", required = false) String engineFilter) {
        int limit;
        try {
            limit = limitStr == null || limitStr.isBlank() ? 0 : Integer.parseInt(limitStr);
        } catch (NumberFormatException e) {
            limit = 0;
        }
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        int offset;
        try {
            offset = offsetStr == null || offsetStr.isBlank() ? 0 : Integer.parseInt(offsetStr);
        } catch (NumberFormatException e) {
            offset = 0;
        }
        if (offset < 0) {
            offset = 0;
        }

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
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "denetim geçmişi alınamadı");
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

        return ResponseEntity.ok(audits);
    }

    // ---------- GetAudit ----------

    @GetMapping("/audits/{auditId}")
    public ResponseEntity<?> getAudit(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String auditId) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    SELECT COALESCE(prompt_id, ''), prompt_text, engine_name, status, score, token_count, latency_ms, issues, metadata, created_at
                    FROM prompt.audits WHERE id = ? AND tenant_id = ?
                    """, auditId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "denetim bulunamadı");
        }
        if (rec == null) {
            return error(HttpStatus.NOT_FOUND, "denetim bulunamadı");
        }
        Map<String, Object> r = rec.intoMap();

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
        return ResponseEntity.ok(resp);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
