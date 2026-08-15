package dev.geolens.guardrail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.guardrail.GuardRule;
import dev.geolens.guardrail.GuardrailMatcher;
import dev.geolens.guardrail.Rule;
import dev.geolens.guardrail.web.CreateRuleRequest;
import dev.geolens.guardrail.web.EvaluateRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime Guardrails iş mantığı — Go {@code guardrail.handler} portu (R3).
 * <p>Kural yönetimi (listele/oluştur/sil/toggle), varsayılan kuralların yüklenmesi ve
 * değerlendirme karar motoru burada yaşar; controller yalnızca HTTP/transport'tur.
 * Eşleşen ihlallerde {@code guardrail.violation} olayı outbox üzerinden taşınır (O-6).
 */
@Service
public class GuardrailService {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public GuardrailService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- ListRules ----------

    /** Tenant kurallarını listeler; hiç kural yoksa varsayılanları yükler. */
    public List<Rule> listRules(String tenantId) {
        List<Rule> rules;
        try {
            rules = queryRules(tenantId);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (rules.isEmpty()) {
            seedDefaults(tenantId);
            rules = queryRules(tenantId);
        }
        return rules;
    }

    // ---------- CreateRule ----------

    public Rule createRule(String tenantId, CreateRuleRequest req) {
        String action = req.action() == null || req.action().isBlank() ? "block" : req.action();
        String severity = req.severity() == null || req.severity().isBlank() ? "high" : req.severity();

        try {
            Record r = dsl.fetchOne("""
                    INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
                    """, tenantId, nz(req.name()), nz(req.category()), nz(req.pattern()), action, severity);
            return toRule(r == null ? Map.of() : r.intoMap());
        } catch (RuntimeException e) {
            throw new GuardrailServiceException("kural oluşturulamadı");
        }
    }

    // ---------- Evaluate ----------

    public GuardrailEvaluateResult evaluate(String tenantId, EvaluateRequest req) {
        long start = System.currentTimeMillis();

        List<GuardRule> rules;
        try {
            rules = queryGuardRules(tenantId);
        } catch (RuntimeException e) {
            throw new GuardrailServiceException("kural sorgu hatası");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        boolean blocked = false;

        for (GuardRule rule : rules) {
            boolean matched = GuardrailMatcher.evaluateRule(rule, req.prompt(), req.response());

            String actionTaken = "none";
            if (matched) {
                actionTaken = rule.action();
                if ("block".equals(rule.action())) {
                    blocked = true;
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rule_id", rule.id());
            item.put("rule_name", rule.name());
            item.put("category", rule.category());
            item.put("matched", matched);
            item.put("action_taken", actionTaken);
            results.add(item);

            logEvaluation(tenantId, rule.id(), req.prompt(), req.response(), matched,
                    actionTaken, System.currentTimeMillis() - start);

            // O-6: guardrail ihlali olayını outbox üzerinden taşı — içerik türevli
            // deterministik anahtar tekrarlanan evaluate çağrılarını tek olaya indirger.
            if (matched && !"none".equals(actionTaken)) {
                enqueueViolation(tenantId, rule, actionTaken, req.prompt(), req.response());
            }
        }

        return new GuardrailEvaluateResult(results, blocked);
    }

    // ---------- DeleteRule ----------

    public void deleteRule(String tenantId, String ruleId) {
        try {
            dsl.execute("DELETE FROM guardrail.rules WHERE id = ? AND tenant_id = ?", ruleId, tenantId);
        } catch (RuntimeException e) {
            throw new GuardrailServiceException("silme hatası");
        }
    }

    // ---------- ToggleRule ----------

    public void toggleRule(String tenantId, String ruleId, boolean enabled) {
        try {
            dsl.execute("UPDATE guardrail.rules SET enabled = ?, updated_at = now() WHERE id = ? AND tenant_id = ?",
                    enabled, ruleId, tenantId);
        } catch (RuntimeException e) {
            throw new GuardrailServiceException("kural güncellenemedi");
        }
    }

    // ---------- SeedDefaults ----------

    public void seedDefaults(String tenantId) {
        List<Rule> defaults = List.of(
                new Rule(null, null, "SQL Injection", "prompt_injection", "/ignore previous instructions/", "block", "critical", true, null, null),
                new Rule(null, null, "Prompt Leak", "prompt_injection", "/reveal your prompt/", "block", "critical", true, null, null),
                new Rule(null, null, "Email Leak", "pii_leakage", "/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}/", "block", "high", true, null, null),
                new Rule(null, null, "Phone Leak", "pii_leakage", "/\\\\+?[0-9]{10,15}/", "block", "high", true, null, null),
                new Rule(null, null, "Toxic Content", "toxic_output", "/\\\\b(hate|discriminate|violent|threat)\\\\b/i", "flag", "medium", true, null, null),
                new Rule(null, null, "Credit Card", "pii_leakage", "/\\\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\\\\b/", "block", "critical", true, null, null),
                new Rule(null, null, "API Key Leak", "pii_leakage", "/\\\\b(sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{36,})\\\\b/", "block", "critical", true, null, null),
                new Rule(null, null, "Hallucination Pattern", "hallucination", "/\\\\bI am not aware\\\\b|\\\\bI cannot confirm\\\\b/i", "flag", "medium", true, null, null));

        for (Rule d : defaults) {
            try {
                dsl.execute("""
                        INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                        ON CONFLICT DO NOTHING
                        """, tenantId, d.name(), d.category(), d.pattern(), d.action(), d.severity(), d.enabled());
            } catch (RuntimeException e) {
                // Go'da loglanıp geçilir
            }
        }
    }

    // ---------- yardımcılar ----------

    private void logEvaluation(String tenantId, String ruleId, String prompt, String response,
                               boolean matched, String actionTaken, long durationMs) {
        try {
            dsl.execute("""
                    INSERT INTO guardrail.evaluations (tenant_id, rule_id, prompt, response, matched, action_taken, duration_ms)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, tenantId, ruleId, nz(prompt), nz(response), matched, actionTaken, durationMs);
        } catch (RuntimeException e) {
            // Go'da debug loglanıp geçilir
        }
    }

    private void enqueueViolation(String tenantId, GuardRule rule, String actionTaken,
                                  String prompt, String response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rule_id", rule.id());
        payload.put("rule_name", rule.name());
        payload.put("category", rule.category());
        payload.put("severity", rule.severity());
        payload.put("action_taken", actionTaken);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        try {
            dsl.execute("""
                    INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                    VALUES (?, 'guardrail.violation', 'q:governance', ?::jsonb, ?, ?, now())
                    """, Ulid.generate(), json, tenantId,
                    GuardrailMatcher.idempotencyKey(tenantId, rule.id(), prompt, response));
        } catch (RuntimeException e) {
            // Go'da warn loglanıp geçilir
        }
    }

    private List<Rule> queryRules(String tenantId) {
        List<Map<String, Object>> rows = dsl.fetch("""
                SELECT id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
                FROM guardrail.rules WHERE tenant_id = ? ORDER BY category, name
                """, tenantId).intoMaps();
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            rules.add(toRule(r));
        }
        return rules;
    }

    private List<GuardRule> queryGuardRules(String tenantId) {
        List<Map<String, Object>> rows = dsl.fetch("""
                SELECT id, name, category, pattern, action, severity
                FROM guardrail.rules WHERE tenant_id = ? AND enabled = true
                """, tenantId).intoMaps();
        List<GuardRule> rules = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            rules.add(new GuardRule(
                    str(r.get("id")), str(r.get("name")), str(r.get("category")),
                    str(r.get("pattern")), str(r.get("action")), str(r.get("severity"))));
        }
        return rules;
    }

    private static Rule toRule(Map<String, Object> r) {
        return new Rule(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("name")), str(r.get("category")),
                str(r.get("pattern")), str(r.get("action")), str(r.get("severity")),
                r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                str(r.get("created_at")), str(r.get("updated_at")));
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
}