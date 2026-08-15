package dev.geolens.seo.web;

import dev.geolens.common.ApiError;

import dev.geolens.seo.service.SeoService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * SEO platform entegrasyonu REST controller'ı — Go {@code seo.handler} portu (FR-B8).
 * <p>Route'lar (go cmd/api): GET /v1/workspaces/{ws}/seo/connections, GET .../auth-url,
 * GET .../callback, GET .../search-console, GET .../ga4, DELETE .../connections/{platform}.
 * <p>İş mantığı {@link SeoService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/seo")
public class SeoController {

    private final SeoService service;

    public SeoController(SeoService service) {
        this.service = service;
    }

    // ---------- ListConnections ----------

    @GetMapping("/connections")
    public ResponseEntity<List<Map<String, Object>>> listConnections(@PathVariable String workspaceId,
                                                                     @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listConnections(workspaceId, tenantId));
    }

    // ---------- GetAuthURL ----------

    @GetMapping("/auth-url")
    public ResponseEntity<?> getAuthUrl(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "platform", required = false) String platform) {
        return ResponseEntity.ok(service.getAuthUrl(workspaceId, tenantId, platform));
    }

    // ---------- HandleCallback ----------

    @GetMapping("/callback")
    public ResponseEntity<?> handleCallback(@PathVariable String workspaceId,
                                            @RequestParam(value = "code", required = false) String code,
                                            @RequestParam(value = "state", required = false) String state) {
        String redirectUrl = service.handleCallback(workspaceId, code, state);
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(URI.create(redirectUrl))
                .build();
    }

    // ---------- Disconnect ----------

    @DeleteMapping("/connections/{platform}")
    public ResponseEntity<?> disconnect(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String platform) {
        return ResponseEntity.ok(service.disconnect(workspaceId, tenantId, platform));
    }

    // ---------- GetSearchConsoleData ----------

    @GetMapping("/search-console")
    public ResponseEntity<List<Map<String, Object>>> getSearchConsoleData(@RequestHeader("X-Tenant-ID") String tenantId,
                                                                          @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.getSearchConsoleData(tenantId, brandId));
    }

    // ---------- GetGA4Data ----------

    @GetMapping("/ga4")
    public ResponseEntity<List<Map<String, Object>>> getGa4Data(@RequestHeader("X-Tenant-ID") String tenantId,
                                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.getGa4Data(tenantId, brandId));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleSeoError(ServiceException ex) {
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
