package dev.geolens.gate.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.gate.CheckResult;
import dev.geolens.gate.GateText;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CI/CD Governance Gate REST controller'ı — Go {@code gate.handler} portu (R6).
 * <p>Route'lar (go cmd/api): POST /v1/gate/check, GET /v1/gate/history/{entityId}.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; karar sonrası {@code gate.check.decision}
 * olayı outbox üzerinden {@code q:governance} stream'ine taşınır (O-6).
 */
@RestController
@RequestMapping("/v1/gate")
public class GateController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public GateController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- Check ----------

    @PostMapping("/check")
    public ResponseEntity<?> check(@RequestHeader("X-Tenant-ID") String tenantId,
                                   @RequestBody CheckRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        String checkId = Ulid.generate();
        String entityId = req.entityId() == null ? "" : req.entityId();

        List<CheckResult> checks = new ArrayList<>(List.of(
                new CheckResult("Registry Entry", false, "AI Registry'de kayıtlı değil"),
                new CheckResult("Risk Assessment", false, "Risk değerlendirmesi yapılmamış"),
                new CheckResult("Policy Compliance", false, "Uygunluk politikası kontrol edilmedi"),
                new CheckResult("Documentation", false, "Teknik dokümantasyon kontrol edilmedi"),
                new CheckResult("Guardrails", false, "Guardrail kuralı kontrol edilmedi"),
                new CheckResult("Bias Test", true, "Bias testi gerekli değil (varsayılan)")));

        // 1. Registry check — entity kayıtlı mı?
        Map<String, Object> reg = map("""
                SELECT id, entity_type, lifecycle_state FROM registry.entities
                WHERE (id = ? OR name = ?) AND tenant_id = ?
                """, entityId, entityId, tenantId);
        String registryId = reg == null ? "" : str(reg.get("id"));
        if (!registryId.isEmpty()) {
            String entityType = str(reg.get("entity_type"));
            String lifecycleState = str(reg.get("lifecycle_state"));
            checks.set(0, new CheckResult("Registry Entry", true,
                    "AI Registry'de kayıtlı (" + entityType + ", " + lifecycleState + ")"));

            // 2. Risk assessment check
            int riskAssessCount = count("""
                    SELECT COUNT(*) FROM registry.risk_assessments WHERE entity_id = ? AND tenant_id = ?
                    """, registryId, tenantId);
            if (riskAssessCount > 0) {
                checks.set(1, new CheckResult("Risk Assessment", true,
                        "Risk değerlendirmesi mevcut (" + riskAssessCount + " adet)"));
            }

            // 3. Documentation check — entity documentation_url dolu mu?
            String docUrl = value("""
                    SELECT COALESCE(documentation_url, '') FROM registry.entities WHERE id = ?
                    """, registryId);
            if (!docUrl.isEmpty()) {
                checks.set(3, new CheckResult("Documentation", true, "Teknik dokümantasyon mevcut"));
            }
        }

        // 4. Policy compliance check — tenant'ın passed control'ları var mı?
        int activePacks = count("""
                SELECT COUNT(*) FROM policy.packs WHERE tenant_id = ? AND enabled = true
                """, tenantId);
        int totalControls = 0;
        int passedControls = 0;
        Map<String, Object> ctrl = map("""
                SELECT COUNT(*)::int AS total,
                       COALESCE(SUM(CASE WHEN status = 'passed' THEN 1 ELSE 0 END), 0)::int AS passed
                FROM policy.controls WHERE tenant_id = ?
                """, tenantId);
        if (ctrl != null) {
            totalControls = ctrl.get("total") == null ? 0 : ((Number) ctrl.get("total")).intValue();
            passedControls = ctrl.get("passed") == null ? 0 : ((Number) ctrl.get("passed")).intValue();
        }
        if (activePacks > 0 && totalControls > 0) {
            boolean passed = passedControls >= totalControls / 2;
            if (passed) {
                checks.set(2, new CheckResult("Policy Compliance", true,
                        GateText.packCount(activePacks) + " aktif, " + GateText.controlPct(passedControls, totalControls)));
            } else {
                checks.set(2, new CheckResult("Policy Compliance", false,
                        GateText.controlPct(passedControls, totalControls) + " — yarıdan az kontrol geçti"));
            }
        } else if (activePacks > 0) {
            checks.set(2, new CheckResult("Policy Compliance", false,
                    GateText.packCount(activePacks) + ", henüz kontrol geçilmemiş"));
        }

        // 5. Guardrails check — tenant'ın guardrail kuralı var mı?
        int guardrailCount = count("""
                SELECT COUNT(*) FROM guardrail.rules WHERE tenant_id = ? AND enabled = true
                """, tenantId);
        if (guardrailCount > 0) {
            checks.set(4, new CheckResult("Guardrails", true,
                    GateText.guardrailCount(guardrailCount) + " aktif"));
        }

        int passed = 0;
        for (CheckResult c : checks) {
            if (c.passed()) {
                passed++;
            }
        }
        boolean allPassed = passed == checks.size();

        String decision = "blocked";
        if (allPassed) {
            decision = "approved";
        } else if ((double) passed / checks.size() >= 0.5) {
            decision = "flagged";
        }

        // Persist check result to gate.checks
        String checkDetailsJson;
        try {
            checkDetailsJson = mapper.writeValueAsString(checks);
        } catch (Exception e) {
            checkDetailsJson = "[]";
        }
        boolean persisted = false;
        try {
            dsl.execute("""
                    INSERT INTO gate.checks (id, tenant_id, entity_id, entity_type, target_env, version,
                                             decision, passed_checks, total_checks, check_details, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                    """, checkId, tenantId, entityId, nz(req.entityType()), nz(req.targetEnvironment()),
                    nz(req.version()), decision, passed, checks.size(), checkDetailsJson);
            persisted = true;
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("gate check persistence hatası"); akış devam eder
        }
        if (persisted) {
            // O-6: GateCheckDecision olayını outbox üzerinden taşı (doğrudan DB yazımı yerine)
            try {
                enqueueDecision(tenantId, checkId, entityId, req, decision, passed, checks.size());
            } catch (RuntimeException e) {
                // uyarı — Go: slog.Warn("gate karar olayı outbox'a yazılamadı")
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("check_id", checkId);
        body.put("entity_id", entityId);
        body.put("entity_type", nz(req.entityType()));
        body.put("target_env", nz(req.targetEnvironment()));
        body.put("decision", decision);
        body.put("passed", passed);
        body.put("total", checks.size());
        body.put("checks", checks);
        body.put("checked_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return ResponseEntity.ok(body);
    }

    // ---------- History ----------

    @GetMapping("/history/{entityId}")
    public ResponseEntity<?> history(@RequestHeader("X-Tenant-ID") String tenantId,
                                     @PathVariable String entityId) {
        int limit = 50;
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, entity_id, entity_type, target_env, version, decision, passed_checks, total_checks, created_at
                    FROM gate.checks
                    WHERE tenant_id = ? AND (? = '' OR entity_id = ?)
                    ORDER BY created_at DESC
                    LIMIT ?
                    """, tenantId, entityId, entityId, limit + 1);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("history", List.of(), "has_more", false, "total", 0));
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("entity_id", str(r.get("entity_id")));
            item.put("entity_type", str(r.get("entity_type")));
            item.put("target_env", str(r.get("target_env")));
            item.put("version", str(r.get("version")));
            item.put("decision", str(r.get("decision")));
            item.put("passed_checks", r.get("passed_checks") == null ? 0 : ((Number) r.get("passed_checks")).intValue());
            item.put("total_checks", r.get("total_checks") == null ? 0 : ((Number) r.get("total_checks")).intValue());
            item.put("checked_at", ts(r.get("created_at")));
            history.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entity_id", entityId);
        body.put("tenant_id", tenantId);
        body.put("history", history);
        body.put("has_more", hasMore);
        body.put("total", history.size());
        return ResponseEntity.ok(body);
    }

    // ---------- yardımcılar ----------

    private void enqueueDecision(String tenantId, String checkId, String entityId, CheckRequest req,
                                 String decision, int passed, int total) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("check_id", checkId);
        payload.put("entity_id", entityId);
        payload.put("entity_type", nz(req.entityType()));
        payload.put("target_env", nz(req.targetEnvironment()));
        payload.put("version", nz(req.version()));
        payload.put("decision", decision);
        payload.put("passed", passed);
        payload.put("total", total);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        dsl.execute("""
                INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                VALUES (?, 'gate.check.decision', 'q:governance', ?::jsonb, ?, ?, now())
                """, Ulid.generate(), json, tenantId, "gate:check:" + checkId);
    }

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

    private String value(String sql, Object... args) {
        try {
            Record r = dsl.fetchOne(sql, args);
            return r == null ? "" : String.valueOf(r.get(0));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String ts(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }

    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
