package dev.geolens.sentiment.web;

import dev.geolens.common.ApiError;

import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.sentiment.service.SentimentService;
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

import java.util.List;
import java.util.Map;

/**
 * Sentiment REST controller'ı (FR-D7) ve hallüsinasyon (FR-D8) — Go {@code sentiment.handler} portu.
 * <p>Route'lar (go cmd/api, /v1/workspaces/{ws} altında): POST /sentiment/analyze,
 * GET /sentiment, GET /sentiment/summary, POST /hallucination/detect, GET /hallucination,
 * POST /hallucination/{flagId}/verify (hallüsinasyon, sentiment'in kardeşi olarak ayrı köktedir).
 * <p>Tenant, gerçek geçit/middleware tarafından atılan {@code X-Tenant-ID} başlığından gelir
 * (Go {@code httpmw.GetTenantID} karşılığı); workspace URL path'ten.
 * <p>İş mantığı {@link SentimentService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}")
public class
SentimentController {

    private final SentimentService service;

    public SentimentController(SentimentService service) {
        this.service = service;
    }

    @PostMapping("/sentiment/analyze")
    public ResponseEntity<?> analyze(@PathVariable String workspaceId,
                                     @RequestHeader("X-Tenant-ID") String tenantId,
                                     @RequestBody AnalyzeRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        List<SentimentResult> results = service.analyze(tenantId, workspaceId, req.brandId(), req.prompt());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/sentiment/")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable String workspaceId,
                                                          @RequestHeader("X-Tenant-ID") String tenantId,
                                                          @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.list(workspaceId, tenantId, brandId));
    }

    @GetMapping("/sentiment/summary")
    public ResponseEntity<?> summary(@PathVariable String workspaceId,
                                     @RequestHeader("X-Tenant-ID") String tenantId,
                                     @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.summary(workspaceId, tenantId, brandId));
    }

    @PostMapping("/hallucination/detect")
    public ResponseEntity<?> detectHallucinations(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestBody DetectRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        List<HallucinationResult> results = service.detectHallucinations(tenantId, workspaceId, req.brandId());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/hallucination")
    public ResponseEntity<List<Map<String, Object>>> listHallucinations(@PathVariable String workspaceId,
                                                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                                                        @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listHallucinations(workspaceId, tenantId, brandId));
    }

    @PostMapping("/hallucination/{flagId}/verify")
    public ResponseEntity<?> verify(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String flagId,
                                    @RequestBody VerifyRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.ok(service.verify(tenantId, flagId, req.verified()));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleSentimentError(ServiceException ex) {
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
