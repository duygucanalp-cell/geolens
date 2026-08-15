package dev.geolens.measure.web;

import dev.geolens.measure.service.MeasureService;
import dev.geolens.measure.service.MeasureServiceException;
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

import java.util.Map;

/**
 * Ölçüm/skor REST controller'ı — Go {@code measure.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/measurements,
 * GET /v1/workspaces/{ws}/measurements/{runId}/status, GET /scores, GET /trends,
 * GET /brands/{brandID}/scores, GET /citations, GET /benchmark, GET /radar.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir (httpmw karşılığı).
 * <p>İş mantığı {@link MeasureService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}")
public class MeasureController {

    private final MeasureService service;

    public MeasureController(MeasureService service) {
        this.service = service;
    }

    @PostMapping("/measurements")
    public ResponseEntity<?> triggerMeasurement(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestBody MeasureRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }

        Map<String, Object> body = service.triggerMeasurement(workspaceId, tenantId, req);
        String runId = String.valueOf(body.get("run_id"));
        String location = String.format("/v1/workspaces/%s/measurements/%s/status", workspaceId, runId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", location)
                .body(body);
    }

    @GetMapping("/measurements/{runId}/status")
    public ResponseEntity<?> getMeasurementStatus(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @PathVariable String runId) {
        return ResponseEntity.ok(service.getMeasurementStatus(workspaceId, tenantId, runId));
    }

    @GetMapping("/scores")
    public ResponseEntity<?> listScores(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listScores(workspaceId, tenantId));
    }

    @GetMapping("/trends")
    public ResponseEntity<?> listTrends(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listTrends(workspaceId, tenantId, brandId));
    }

    @GetMapping("/brands/{brandId}/scores")
    public ResponseEntity<?> listBrandScores(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @PathVariable String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "marka ID gerekli");
        }
        return ResponseEntity.ok(service.listBrandScores(workspaceId, tenantId, brandId));
    }

    @GetMapping("/citations")
    public ResponseEntity<?> listCitations(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "brand_id", required = false) String brandId,
                                           @RequestParam(value = "job_id", required = false) String jobId) {
        if ((brandId == null || brandId.isBlank()) && (jobId == null || jobId.isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "brand_id veya job_id parametresi gerekli");
        }
        return ResponseEntity.ok(service.listCitations(workspaceId, tenantId, brandId, jobId));
    }

    @GetMapping("/benchmark")
    public ResponseEntity<?> listBenchmark(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listBenchmark(workspaceId, tenantId, brandId));
    }

    @GetMapping("/radar")
    public ResponseEntity<?> listRadarComparison(@PathVariable String workspaceId,
                                                 @RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listRadarComparison(workspaceId, tenantId, brandId));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(MeasureServiceException.class)
    public ResponseEntity<ApiError> handleMeasureError(MeasureServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
