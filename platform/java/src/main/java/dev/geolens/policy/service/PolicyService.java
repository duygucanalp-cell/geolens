package dev.geolens.policy.service;

import dev.geolens.policy.Control;
import dev.geolens.policy.ControlDef;
import dev.geolens.policy.Pack;
import dev.geolens.policy.PolicySeeder;
import dev.geolens.policy.web.UpdateControlRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Policy Packs iş mantığı — Go {@code policy.handler} portu (R4).
 * <p>Pack listeleme/seed/uygulama, kontrol listeleme/güncelleme ve uyumluluk
 * hesabını yapar. Controller yalnızca HTTP katmanıdır; bu sınıf DB erişimini içerir.
 */
@Service
public class PolicyService {

    private final DSLContext dsl;

    public PolicyService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- ListPacks ----------

    public Map<String, Object> listPacks(String tenantId) {
        List<Pack> packs;
        try {
            packs = queryPacks(tenantId);
        } catch (RuntimeException e) {
            return Map.of("packs", List.of());
        }

        // Auto-seed: tenant hiç pack oluşturmamışsa default pack'leri yükle
        if (packs.isEmpty()) {
            seedDefaultPacks(tenantId);
            packs = queryPacks(tenantId);
        }

        return Map.of("packs", packs);
    }

    // ---------- ListControls ----------

    public Map<String, Object> listControls(String tenantId, String packId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, pack_id, tenant_id, control_id, title, description, category, status, evidence, due_date, created_at, updated_at
                    FROM policy.controls WHERE pack_id = ? AND tenant_id = ? ORDER BY control_id
                    """, packId, tenantId);
        } catch (RuntimeException e) {
            return Map.of("controls", List.of());
        }

        List<Control> controls = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            controls.add(toControl(r));
        }
        return Map.of("controls", controls);
    }

    // ---------- ApplyPack ----------

    public Pack applyPack(String tenantId, String packId) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    UPDATE policy.packs SET enabled = true, applied_at = now(), updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    RETURNING id, tenant_id, name, framework, description, version, enabled, applied_at, created_at, updated_at
                    """, packId, tenantId);
        } catch (RuntimeException e) {
            throw new PolicyServiceException(HttpStatus.NOT_FOUND, "pack bulunamadı");
        }
        if (rec == null) {
            throw new PolicyServiceException(HttpStatus.NOT_FOUND, "pack bulunamadı");
        }
        Pack p = toPack(rec.intoMap());

        // Seed default controls if not exist
        seedControls(p.id(), tenantId, p.framework());

        return p;
    }

    // ---------- GetCompliance ----------

    public Map<String, Object> getCompliance(String tenantId, String entityId) {
        int total = 0, passed = 0, failed = 0, notApplicable = 0;
        try {
            Record agg = dsl.fetchOne("""
                    SELECT
                        COUNT(*)::int,
                        COALESCE(SUM(CASE WHEN status = 'passed' THEN 1 ELSE 0 END), 0)::int,
                        COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0)::int,
                        COALESCE(SUM(CASE WHEN status = 'not_applicable' THEN 1 ELSE 0 END), 0)::int
                    FROM policy.controls WHERE tenant_id = ?
                    """, tenantId);
            if (agg != null) {
                total = ((Number) agg.get(0)).intValue();
                passed = ((Number) agg.get(1)).intValue();
                failed = ((Number) agg.get(2)).intValue();
                notApplicable = ((Number) agg.get(3)).intValue();
            }
        } catch (RuntimeException e) {
            // Go'da hata yok sayılır (0 kalır)
        }

        // entity_id varsa registry risk assessment ile ilişkilendir
        String riskLevel = "";
        if (entityId != null && !entityId.isEmpty() && !"undefined".equals(entityId) && !"null".equals(entityId)) {
            try {
                Record riskRec = dsl.fetchOne("""
                        SELECT COALESCE(risk_class, '') FROM registry.entities WHERE id = ? AND tenant_id = ?
                        """, entityId, tenantId);
                if (riskRec != null) {
                    riskLevel = str(riskRec.get(0));
                }
            } catch (RuntimeException e) {
                // Go'da hata yok sayılır
            }
        }

        double compliancePct = 0;
        if (total > 0) {
            compliancePct = (double) passed / total * 100;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entity_id", entityId);
        result.put("compliance_pct", compliancePct);
        result.put("total_controls", total);
        result.put("passed", passed);
        result.put("failed", failed);
        result.put("not_applicable", notApplicable);
        if (!riskLevel.isEmpty()) {
            result.put("entity_risk_class", riskLevel);
        }

        return result;
    }

    // ---------- SeedPacks ----------

    public Map<String, Object> seedPacks(String tenantId) {
        seedDefaultPacks(tenantId);
        return Map.of("status", "policy packs seeded");
    }

    // ---------- UpdateControl ----------

    public Map<String, Object> updateControl(String tenantId, String controlId, UpdateControlRequest req) {
        try {
            dsl.execute("""
                    UPDATE policy.controls SET status = ?, evidence = ?, updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    """, nz(req.status()), nz(req.evidence()), controlId, tenantId);
        } catch (RuntimeException e) {
            throw new PolicyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "control güncellenemedi");
        }
        return Map.of("status", "güncellendi");
    }

    // ---------- yardımcılar ----------

    /** Go {@code SeedDefaultPacks} karşılığı: 4 framework pack + kontrol seed. */
    private void seedDefaultPacks(String tenantId) {
        for (PolicySeeder.Framework f : PolicySeeder.defaultFrameworks()) {
            String packId = null;
            try {
                Record r = dsl.fetchOne("""
                        INSERT INTO policy.packs (tenant_id, name, framework, description, enabled)
                        VALUES (?, ?, ?, ?, true)
                        ON CONFLICT (tenant_id, framework) DO UPDATE SET updated_at = now()
                        RETURNING id
                        """, tenantId, f.name(), f.framework(), f.description());
                if (r != null) {
                    packId = str(r.get(0));
                }
            } catch (RuntimeException e) {
                continue;
            }
            if (packId != null) {
                seedControls(packId, tenantId, f.framework());
            }
        }
    }

    private void seedControls(String packId, String tenantId, String framework) {
        for (ControlDef c : PolicySeeder.frameworkControls(framework)) {
            try {
                dsl.execute("""
                        INSERT INTO policy.controls (pack_id, tenant_id, control_id, title, description, category)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (pack_id, control_id) DO NOTHING
                        """, packId, tenantId, c.id(), c.title(), c.description(), c.category());
            } catch (RuntimeException e) {
                // Go'da hata loglanıp geçilir
            }
        }
    }

    private List<Pack> queryPacks(String tenantId) {
        List<Map<String, Object>> rows = list("""
                SELECT id, tenant_id, name, framework, description, version, enabled, applied_at, created_at, updated_at
                FROM policy.packs WHERE tenant_id = ? ORDER BY framework
                """, tenantId);
        List<Pack> packs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            packs.add(toPack(r));
        }
        return packs;
    }

    private static Pack toPack(Map<String, Object> r) {
        return new Pack(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("name")), str(r.get("framework")),
                str(r.get("description")), str(r.get("version")),
                r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                r.get("applied_at") == null ? null : str(r.get("applied_at")),
                str(r.get("created_at")), str(r.get("updated_at")));
    }

    private static Control toControl(Map<String, Object> r) {
        return new Control(
                str(r.get("id")), str(r.get("pack_id")), str(r.get("tenant_id")), str(r.get("control_id")),
                str(r.get("title")), str(r.get("description")), str(r.get("category")),
                str(r.get("status")), str(r.get("evidence")),
                r.get("due_date") == null ? null : str(r.get("due_date")),
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
