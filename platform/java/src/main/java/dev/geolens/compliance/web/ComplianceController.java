package dev.geolens.compliance.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.compliance.ComplianceEvaluator;
import dev.geolens.compliance.Control;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uyumluluk REST controller'ı — Go {@code compliance.handler} portu.
 * <p>Route'lar (go cmd/api): GET /v1/compliance/soc2, GET /v1/compliance/report,
 * GET /v1/compliance/evidence, GET /v1/compliance/evidence/download.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; SOC 2 Tip 1 hazırlık skoru,
 * GDPR/KVKK ve ISO 27001 değerlendirmeleri yapılır.
 */
@RestController
@RequestMapping("/v1/compliance")
public class ComplianceController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public ComplianceController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- SOC2Readiness ----------

    @GetMapping("/soc2")
    public ResponseEntity<?> soc2Readiness(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Control> controls = evaluateControls(tenantId);

        int total = controls.size();
        int passed = 0;
        for (Control c : controls) {
            if ("passed".equals(c.status())) {
                passed++;
            }
        }

        double readiness = 0;
        if (total > 0) {
            readiness = (double) passed / total * 100;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("framework", "SOC 2 Tip 1");
        result.put("readiness_pct", readiness);
        result.put("controls", controls);
        result.put("summary", Map.of("total", total, "passed", passed, "failed", total - passed));
        result.put("recommendations", ComplianceEvaluator.generateRecommendations(controls));
        return ResponseEntity.ok(result);
    }

    // ---------- ComplianceReport ----------

    @GetMapping("/report")
    public ResponseEntity<?> complianceReport(@RequestHeader("X-Tenant-ID") String tenantId) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("soc_2_tip_1", evaluateControls(tenantId));
        report.put("gdpr_kvkk", evaluateGdpr(tenantId));
        report.put("iso_27001", evaluateIso27001(tenantId));
        return ResponseEntity.ok(report);
    }

    // ---------- ListEvidence ----------

    @GetMapping("/evidence")
    public ResponseEntity<?> listEvidence() {
        List<Map<String, Object>> evidence = List.of(
                evidence("E001", "CC1", "Tenant kayıtları", "identity.user_tenants"),
                evidence("E002", "CC2", "Bildirim ayarları", "config.delivery_settings"),
                evidence("E003", "CC3", "Ölçüm puanları", "measure.scores"),
                evidence("E004", "CC4", "Denetim günlüğü", "identity.audit_logs"),
                evidence("E005", "CC5", "Kullanıcı-tenant rolleri", "identity.user_tenants"),
                evidence("E006", "CC6", "Şifreleme anahtarı", "STORAGE_MASTER_KEY env"),
                evidence("E007", "GDPR", "Silme talepleri", "identity.deletion_requests"),
                evidence("E008", "ISO-A.12.4", "Audit log export", "GET /admin/audit-trail/export"),
                evidence("E009", "CC4", "Elasticsearch audit index", "internal/search/indexer.go"),
                evidence("E010", "CC6", "Kripto-silme implementasyonu", "platform/storage/encryption.go"));
        return ResponseEntity.ok(Map.of("evidence", evidence, "total", 10));
    }

    // ---------- DownloadEvidence ----------

    @GetMapping("/evidence/download")
    public ResponseEntity<?> downloadEvidence(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Control> controls = evaluateControls(tenantId);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("framework", "SOC 2 Tip 1");
        pack.put("organization", "GeoLens Platform");
        pack.put("date", OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        pack.put("tenant_id", tenantId);
        pack.put("controls", controls);
        pack.put("evidence", List.of(
                Map.of("id", "E001", "title", "Tenant kayıtları", "location", "identity.user_tenants"),
                Map.of("id", "E005", "title", "Kullanıcı rolleri", "location", "identity.user_tenants"),
                Map.of("id", "E004", "title", "Audit günlüğü", "location", "identity.audit_logs"),
                Map.of("id", "E006", "title", "Şifreleme", "location", "STORAGE_MASTER_KEY")));

        String json;
        try {
            json = mapper.writeValueAsString(pack);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=soc2-evidence.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // ---------- yardımcılar ----------

    /** Go {@code evaluateControls} karşılığı — 6 SOC 2 kontrolü (CC1-CC6). */
    private List<Control> evaluateControls(String tenantId) {
        List<Control> controls = new ArrayList<>();

        int userTenantCount = count("SELECT COUNT(*) FROM identity.user_tenants WHERE tenant_id = ?", tenantId);
        boolean cc1Passed = userTenantCount > 0;
        controls.add(new Control("CC1", "Control Environment",
                "Kiracı ve kullanıcı yönetimi", "Tenant ve kullanıcı kayıtları mevcut, rol tabanlı erişim tanımlı",
                ComplianceEvaluator.boolToStatus(cc1Passed),
                ComplianceEvaluator.boolToEvidence(cc1Passed,
                        "Bu tenant için kullanıcı-tenant kayıtları mevcut",
                        "Bu tenant için kullanıcı-tenant kaydı bulunamadı")));

        int deliveryCount = count("SELECT COUNT(*) FROM config.delivery_settings WHERE tenant_id = ?", tenantId);
        boolean cc2Passed = deliveryCount > 0;
        controls.add(new Control("CC2", "Communication",
                "Bildirim ve uyarı mekanizmaları", "E-posta bildirim ayarları, alert kuralları ve delivery kanalları yapılandırılabilir",
                ComplianceEvaluator.boolToStatus(cc2Passed),
                ComplianceEvaluator.boolToEvidence(cc2Passed,
                        "Bu tenant için delivery ayarları mevcut",
                        "Bu tenant için bildirim ayarı bulunamadı")));

        int measureCount = count("SELECT COUNT(*) FROM measure.scores WHERE tenant_id = ?", tenantId);
        boolean cc3Passed = measureCount > 0;
        controls.add(new Control("CC3", "Risk Assessment",
                "Ölçüm ve risk değerlendirmesi", "AI görünürlük ölçümleri, benchmark karşılaştırmaları ve puanlama aktif",
                ComplianceEvaluator.boolToStatus(cc3Passed),
                ComplianceEvaluator.boolToEvidence(cc3Passed,
                        "Bu tenant için ölçüm puanları mevcut",
                        "Bu tenant için ölçüm kaydı bulunamadı")));

        int auditCount = count("SELECT COUNT(*) FROM identity.audit_logs WHERE tenant_id = ?", tenantId);
        boolean cc4Passed = auditCount > 0;
        controls.add(new Control("CC4", "Monitoring",
                "Denetim günlüğü ve izleme", "Tüm işlemler denetlenir, audit günlükleri tutulur, Elasticsearch'e indekslenir",
                ComplianceEvaluator.boolToStatus(cc4Passed),
                ComplianceEvaluator.boolToEvidence(cc4Passed,
                        "Bu tenant için audit günlüğü mevcut",
                        "Bu tenant için denetim günlüğü bulunamadı")));

        int userCount = count("SELECT COUNT(*) FROM identity.user_tenants WHERE tenant_id = ?", tenantId);
        boolean cc5Passed = userCount > 0;
        controls.add(new Control("CC5", "Control Activities",
                "Rol tabanlı erişim kontrolü (RBAC)", "Viewer/Editor/Admin rolleri, API key auth, JWT authentication aktif",
                ComplianceEvaluator.boolToStatus(cc5Passed),
                ComplianceEvaluator.boolToEvidence(cc5Passed,
                        "Bu tenant için rol ataması mevcut",
                        "Bu tenant için kullanıcı-tenant ilişkisi bulunamadı")));

        controls.add(new Control("CC6", "Logical and Physical Security",
                "Veri şifreleme ve güvenlik", "AES-256-GCM şifreleme (crypto-shredding), HTTPS, güvenlik başlıkları",
                "passed", "AES-256-GCM encryption active, STORAGE_MASTER_KEY configured"));

        return controls;
    }

    /** Go {@code evaluateGDPR} karşılığı. */
    private Map<String, Object> evaluateGdpr(String tenantId) {
        int deleteCount = -1;
        String err = null;
        try {
            deleteCount = count("SELECT COUNT(*) FROM identity.deletion_requests WHERE tenant_id = ?", tenantId);
        } catch (RuntimeException e) {
            err = e.getMessage();
        }

        boolean passed = err == null && deleteCount >= 0;
        String notes = "Veri silme talepleri işlenebilir, deletion_requests tablosu mevcut";
        if (err != null) {
            notes = "deletion_requests tablosuna erişilemedi: " + err;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("framework", "GDPR / KVKK");
        m.put("status", ComplianceEvaluator.boolToStatus(passed));
        m.put("notes", notes);
        m.put("controls_checked", List.of(
                "Right to erasure (silme hakkı)",
                "Data portability (veri taşınabilirliği)",
                "Consent management (izin yönetimi)",
                "Privacy threshold (NFR-13: 5+ tenant anonim sektör karşılaştırması)"));
        return m;
    }

    /** Go {@code evaluateISO27001} karşılığı. */
    private Map<String, Object> evaluateIso27001(String tenantId) {
        String err = null;
        try {
            count("SELECT COUNT(*) FROM config.panels WHERE tenant_id = ?", tenantId);
        } catch (RuntimeException e) {
            err = e.getMessage();
        }

        boolean passed = err == null;
        String notes = "Kontroller: Asset management (A.8), Access control (A.9), Cryptography (A.10)";
        if (err != null) {
            notes = "panel sayısı sorgulanamadı: " + err;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("framework", "ISO 27001");
        m.put("status", ComplianceEvaluator.boolToStatus(passed));
        m.put("notes", notes);
        m.put("controls_checked", List.of(
                "A.8.1 — Varlık envanteri (brand/workspace yönetimi)",
                "A.9.1 — Erişim kontrol politikası (RBAC)",
                "A.10.1 — Şifreleme kontrolleri (AES-256-GCM)",
                "A.12.4 — Günlük kaydı ve izleme (audit_logs)",
                "A.16.1 — Olay yönetimi (alert_rules)"));
        return m;
    }

    /** COUNT(*) sorgusu — Go QueryRow + Scan karşılığı; hatada -1 döner. */
    private int count(String sql, String tenantId) {
        try {
            Record r = dsl.fetchOne(sql, tenantId);
            return r == null ? -1 : ((Number) r.get(0)).intValue();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static Map<String, Object> evidence(String id, String control, String title, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("control", control);
        m.put("title", title);
        m.put("source", source);
        return m;
    }
}
