package dev.geolens.compliance.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.compliance.service.ComplianceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Uyumluluk REST controller'ı — Go {@code compliance.handler} portu.
 * <p>Route'lar (go cmd/api): GET /v1/compliance/soc2, GET /v1/compliance/report,
 * GET /v1/compliance/evidence, GET /v1/compliance/evidence/download.
 * <p>İş mantığı {@link ComplianceService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/compliance")
public class ComplianceController {

    private final ComplianceService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public ComplianceController(ComplianceService service) {
        this.service = service;
    }

    // ---------- SOC2Readiness ----------

    @GetMapping("/soc2")
    public ResponseEntity<Map<String, Object>> soc2Readiness(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.soc2Readiness(tenantId));
    }

    // ---------- ComplianceReport ----------

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> complianceReport(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.complianceReport(tenantId));
    }

    // ---------- ListEvidence ----------

    @GetMapping("/evidence")
    public ResponseEntity<Map<String, Object>> listEvidence() {
        return ResponseEntity.ok(service.listEvidence());
    }

    // ---------- DownloadEvidence ----------

    @GetMapping("/evidence/download")
    public ResponseEntity<?> downloadEvidence(@RequestHeader("X-Tenant-ID") String tenantId) {
        Map<String, Object> pack = service.buildEvidencePack(tenantId);

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
}
