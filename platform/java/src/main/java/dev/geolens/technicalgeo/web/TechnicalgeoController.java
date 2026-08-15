package dev.geolens.technicalgeo.web;

import dev.geolens.common.ApiError;

import dev.geolens.technicalgeo.service.TechnicalgeoService;
import dev.geolens.common.ServiceException;
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

/**
 * Technical GEO REST controller'ı — Go {@code technicalgeo.handler} portu (FR-B6/B7/E7).
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/technical-geo/bots,
 * GET /technical-geo/bots, POST /technical-geo/schema, GET /technical-geo/schema,
 * GET /technical-geo/score.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir; LLM bot
 * erişimi ve Schema.org kullanımı analiz edilir.
 * <p>İş mantığı {@link TechnicalgeoService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/technical-geo")
public class TechnicalgeoController {

    private final TechnicalgeoService service;

    public TechnicalgeoController(TechnicalgeoService service) {
        this.service = service;
    }

    // ---------- AnalyzeBots ----------

    @PostMapping("/bots")
    public ResponseEntity<?> analyzeBots(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody AnalyzeBotsRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        return ResponseEntity.ok(service.analyzeBots(req.brandId(), req.url(), workspaceId, tenantId));
    }

    // ---------- ListBotAnalyses ----------

    @GetMapping("/bots")
    public ResponseEntity<?> listBotAnalyses(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listBotAnalyses(workspaceId, tenantId, brandId));
    }

    // ---------- AnalyzeSchema ----------

    @PostMapping("/schema")
    public ResponseEntity<?> analyzeSchema(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestBody AnalyzeSchemaRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        return ResponseEntity.ok(service.analyzeSchema(req.brandId(), workspaceId, tenantId));
    }

    // ---------- ListSchemaAnalyses ----------

    @GetMapping("/schema")
    public ResponseEntity<?> listSchemaAnalyses(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listSchemaAnalyses(workspaceId, tenantId, brandId));
    }

    // ---------- GetTechnicalGEOScore ----------

    @GetMapping("/score")
    public ResponseEntity<?> getTechnicalGeoScore(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getTechnicalGeoScore(brandId, workspaceId, tenantId));
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleService(ServiceException ex) {
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
