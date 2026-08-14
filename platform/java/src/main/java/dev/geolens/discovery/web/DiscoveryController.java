package dev.geolens.discovery.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.discovery.ShadowFinding;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shadow AI Discovery REST controller'ı — Go {@code discovery.handler} portu (R2).
 * <p>Route'lar (go cmd/api): POST /v1/discovery/scan, GET /v1/discovery/scans/{scanId}.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir. Tarama arka planda çalışır (Go'daki
 * goroutine karşılığı, 30sn timeout); bulunan kaynaklar registry.entities'e de yazılır.
 */
@RestController
@RequestMapping("/v1/discovery")
public class DiscoveryController {

    private final DSLContext dsl;
    private final ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    public DiscoveryController(DSLContext dsl) {
        this.dsl = dsl;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // ---------- StartScan ----------

    @PostMapping("/scan")
    public ResponseEntity<?> startScan(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestBody StartScanRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        String scanType = req.scanType() == null || req.scanType().isBlank() ? "api" : req.scanType();
        String provider = req.provider() == null || req.provider().isBlank() ? "all" : req.provider();

        String scanId = Ulid.generate();
        try {
            dsl.execute("""
                    INSERT INTO discovery.scans (id, tenant_id, scan_type, provider, status, started_at)
                    VALUES (?, ?, ?, ?, 'running', now())
                    """, scanId, tenantId, scanType, provider);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "tarama başlatılamadı");
        }

        // Arka planda tarama başlat (30sn timeout) — Go'daki goroutine karşılığı
        executor.execute(() -> runScan(scanId, tenantId, scanType, provider));

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "scan_id", scanId,
                "status", "running"));
    }

    /** Go {@code runScan} portu — simüle tarama çalıştırır, finding'leri ve registry kaydını yazar. */
    private void runScan(String scanId, String tenantId, String scanType, String provider) {
        Instant deadline = Instant.now().plusSeconds(30);

        List<ShadowFinding> findings = simulateScan();

        for (ShadowFinding f : findings) {
            if (Instant.now().isAfter(deadline)) {
                try {
                    dsl.execute("""
                            UPDATE discovery.scans SET status = 'failed', error_message = 'timeout', completed_at = now()
                            WHERE id = ?
                            """, scanId);
                } catch (RuntimeException e) {
                    // uyarı — Go: slog.Warn("scan iptal durumu güncellenemedi")
                }
                return;
            }

            try {
                dsl.execute("""
                        INSERT INTO discovery.findings (scan_id, tenant_id, resource_type, resource_name, resource_id,
                                                       provider, region, risk_level, details)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """, scanId, tenantId, f.resourceType(), f.resourceName(), f.resourceId(),
                        f.provider(), f.region(), f.riskLevel(), f.details());
            } catch (RuntimeException e) {
                // hata — Go: slog.Error("finding kayıt hatası")
            }

            // Bulunan kaynakları registry'e de ekle
            registerFinding(tenantId, f);
        }

        try {
            dsl.execute("""
                    UPDATE discovery.scans SET status = 'completed', total_found = ?, completed_at = now()
                    WHERE id = ?
                    """, findings.size(), scanId);
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("scan durum güncelleme hatası")
        }
    }

    /** Go {@code simulateScan} portu — demo/simülasyon finding'leri (sabit üç kaynak). */
    static List<ShadowFinding> simulateScan() {
        return List.of(
                new ShadowFinding("lambda", "ai-inference-fn",
                        "arn:aws:lambda:us-east-1:123456789012:function:ai-inference-fn",
                        "aws", "us-east-1", "high",
                        "{\"runtime\":\"python3.12\",\"memory\":1024,\"timeout\":300,\"has_ai_deps\":true}"),
                new ShadowFinding("sagemaker", "prod-llm-endpoint",
                        "arn:aws:sagemaker:us-west-2:123456789012:endpoint/prod-llm",
                        "aws", "us-west-2", "critical",
                        "{\"instance_type\":\"ml.g5.12xlarge\",\"model\":\"llama-3-70b\",\"no_guardrails\":true}"),
                new ShadowFinding("vertex_ai", "customer-chat-model",
                        "projects/geolens-test/locations/us-central1/endpoints/12345",
                        "gcp", "us-central1", "medium",
                        "{\"framework\":\"tensorflow\",\"accelerator\":\"tpu\",\"has_logging\":true}"));
    }

    /** Go {@code registerFinding} portu — bulunan kaynağı registry.entities'e ekler (çakışmada yoksay). */
    private void registerFinding(String tenantId, ShadowFinding f) {
        String entityName = f.resourceName() == null || f.resourceName().isBlank() ? f.resourceId() : f.resourceName();

        String provider = switch (f.provider()) {
            case "aws" -> "AWS SageMaker";
            case "gcp" -> "Google Vertex AI";
            case "azure" -> "Azure AI";
            default -> f.provider();
        };

        String metaJson;
        try {
            metaJson = mapper.writeValueAsString(Map.of(
                    "discovered_by", "shadow_ai_scan",
                    "resource_type", f.resourceType(),
                    "region", f.region()));
        } catch (Exception e) {
            metaJson = "{}";
        }

        try {
            dsl.execute("""
                    INSERT INTO registry.entities (tenant_id, entity_type, name, provider, description,
                                                   lifecycle_state, risk_class, metadata)
                    VALUES (?, 'model', ?, ?, ?, 'production', ?, ?::jsonb)
                    ON CONFLICT DO NOTHING
                    """, tenantId, entityName, provider, "Shadow AI Discovery ile bulundu", f.riskLevel(), metaJson);
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("registry'e kayıt hatası")
        }
    }

    // ---------- GetScanResults ----------

    @GetMapping("/scans/{scanId}")
    public ResponseEntity<?> getScanResults(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String scanId) {
        Map<String, Object> scanRow;
        try {
            scanRow = map("""
                    SELECT id, status, provider, total_found, started_at, completed_at, created_at
                    FROM discovery.scans WHERE id = ? AND tenant_id = ?
                    """, scanId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "tarama bulunamadı");
        }
        if (scanRow == null) {
            return error(HttpStatus.NOT_FOUND, "tarama bulunamadı");
        }

        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("id", str(scanRow.get("id")));
        scan.put("status", str(scanRow.get("status")));
        scan.put("provider", str(scanRow.get("provider")));
        scan.put("total_found", scanRow.get("total_found") == null ? 0 : ((Number) scanRow.get("total_found")).intValue());
        if (scanRow.get("started_at") != null) {
            scan.put("started_at", str(scanRow.get("started_at")));
        }
        if (scanRow.get("completed_at") != null) {
            scan.put("completed_at", str(scanRow.get("completed_at")));
        }
        scan.put("created_at", str(scanRow.get("created_at")));

        List<Map<String, Object>> findings;
        try {
            findings = list("""
                    SELECT resource_type, resource_name, resource_id, provider, region, risk_level, details, discovered_at
                    FROM discovery.findings WHERE scan_id = ? ORDER BY risk_level DESC
                    """, scanId);
        } catch (RuntimeException e) {
            // Go: finding sorgusu hatalıysa scan bilgisiyle 200 döner
            return ResponseEntity.ok(Map.of("scan", scan));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : findings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource_type", str(r.get("resource_type")));
            item.put("resource_name", str(r.get("resource_name")));
            item.put("resource_id", str(r.get("resource_id")));
            item.put("provider", str(r.get("provider")));
            item.put("region", str(r.get("region")));
            item.put("risk_level", str(r.get("risk_level")));
            item.put("details", str(r.get("details")));
            item.put("discovered_at", str(r.get("discovered_at")));
            out.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "scan", scan,
                "findings", out));
    }

    // ---------- yardımcılar ----------

    private static String str(Object o) {
        if (o == null) {
            return "";
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
