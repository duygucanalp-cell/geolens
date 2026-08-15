package dev.geolens.config.web;

import dev.geolens.common.ApiError;

import dev.geolens.config.service.ConfigService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Marka ve rakip yapılandırması REST controller'ı — Go {@code config.handler} portu.
 * <p>Route'lar (go cmd/api): GET/POST /v1/workspaces/{ws}/brands,
 * GET /v1/workspaces/{ws}/brands/search, PUT/DELETE /v1/workspaces/{ws}/brands/{brandId},
 * GET/PUT /v1/workspaces/{ws}/brands/{brandId}/competitors,
 * DELETE /v1/workspaces/{ws}/brands/{brandId}/competitors/{competitorId},
 * GET /v1/workspaces/{ws}/setup-status, GET /v1/tenant/panorama (H5).
 * <p>İş mantığı {@link ConfigService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
public class ConfigController {

    private final ConfigService service;

    public ConfigController(ConfigService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{workspaceId}/brands/search")
    public ResponseEntity<?> searchBrands(@PathVariable String workspaceId,
                                          @RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestParam(value = "q", required = false) String query,
                                          @RequestParam(value = "exclude", required = false) String exclude,
                                          @RequestParam(value = "offset", required = false) String offsetParam,
                                          @RequestParam(value = "limit", required = false) String limitParam) {
        String q = query == null ? "" : query;
        if (q.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "q parametresi gerekli");
        }
        String excl = exclude == null ? "" : exclude;

        int offset = 0;
        if (offsetParam != null && !offsetParam.isBlank()) {
            try {
                int n = Integer.parseInt(offsetParam);
                if (n >= 0) {
                    offset = n;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        int limit = 20;
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                int n = Integer.parseInt(limitParam);
                if (n > 0) {
                    limit = Math.min(n, 100);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return ResponseEntity.ok(service.searchBrands(workspaceId, tenantId, q, excl, offset, limit));
    }

    @GetMapping("/v1/workspaces/{workspaceId}/brands")
    public ResponseEntity<?> listBrands(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listBrands(workspaceId, tenantId));
    }

    @PostMapping("/v1/workspaces/{workspaceId}/brands")
    public ResponseEntity<?> createBrand(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody BrandRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()
                || req.websiteUrl() == null || req.websiteUrl().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "marka adı ve web sitesi zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createBrand(workspaceId, tenantId, req));
    }

    @PutMapping("/v1/workspaces/{workspaceId}/brands/{brandId}")
    public ResponseEntity<?> updateBrand(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String brandId,
                                         @RequestBody UpdateBrandRequest req) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        if (req == null || (req.name() == null || req.name().isBlank())
                && (req.websiteUrl() == null || req.websiteUrl().isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "en az bir alan gerekli (name veya website_url)");
        }
        return ResponseEntity.ok(service.updateBrand(workspaceId, tenantId, brandId, req));
    }

    @DeleteMapping("/v1/workspaces/{workspaceId}/brands/{brandId}")
    public ResponseEntity<?> deleteBrand(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.deleteBrand(workspaceId, tenantId, brandId));
    }

    @DeleteMapping("/v1/workspaces/{workspaceId}/brands/{brandId}/competitors/{competitorId}")
    public ResponseEntity<?> deleteBrandCompetitor(@RequestHeader("X-Tenant-ID") String tenantId,
                                                   @PathVariable String brandId,
                                                   @PathVariable String competitorId) {
        if (brandId == null || brandId.isBlank() || competitorId == null || competitorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve competitor_id gerekli");
        }
        if (brandId.equals(competitorId)) {
            return error(HttpStatus.BAD_REQUEST, "kendi kendine rakip ilişkisi silinemez");
        }
        return ResponseEntity.ok(service.deleteBrandCompetitor(tenantId, brandId, competitorId));
    }

    @GetMapping("/v1/workspaces/{workspaceId}/brands/{brandId}/competitors")
    public ResponseEntity<?> listBrandCompetitors(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @PathVariable String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.listBrandCompetitors(workspaceId, tenantId, brandId));
    }

    @PutMapping("/v1/workspaces/{workspaceId}/brands/{brandId}/competitors")
    public ResponseEntity<?> updateBrandCompetitors(@PathVariable String workspaceId,
                                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                                    @PathVariable String brandId,
                                                    @RequestBody UpdateCompetitorsRequest req) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        List<String> competitors = req == null || req.competitors() == null
                ? List.of() : req.competitors();
        return ResponseEntity.ok(service.updateBrandCompetitors(workspaceId, tenantId, brandId, competitors));
    }

    @GetMapping("/v1/workspaces/{workspaceId}/setup-status")
    public ResponseEntity<?> getSetupStatus(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.getSetupStatus(workspaceId, tenantId));
    }

    @GetMapping("/v1/tenant/panorama")
    public ResponseEntity<?> listWorkspacePanorama(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listWorkspacePanorama(tenantId));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleConfigError(ServiceException ex) {
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
