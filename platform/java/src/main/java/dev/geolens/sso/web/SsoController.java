package dev.geolens.sso.web;

import dev.geolens.sso.KeyPairGeneratorUtil;
import dev.geolens.sso.service.SsoService;
import dev.geolens.sso.service.SsoServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

import java.util.Map;

/**
 * SSO/SAML REST controller'ı — Go {@code sso.handler} portu (K1).
 * <p>Route'lar (go cmd/api): GET/PUT /v1/sso/config, GET /v1/sso/metadata,
 * POST /v1/sso/enable, POST /v1/sso/disable, POST /v1/sso/generate-keys,
 * POST /sso/acs/{tenantId} (JWT dışı — path tenant).
 * <p>İş mantığı {@link SsoService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 * Tenant {@code X-Tenant-ID} başlığından gelir (ACS dışında).
 */
@RestController
@RequestMapping("/v1/sso")
public class SsoController {

    private final SsoService service;

    public SsoController(SsoService service) {
        this.service = service;
    }

    // ---------- GetConfig ----------

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.getConfig(tenantId));
    }

    // ---------- UpdateConfig ----------

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody UpdateConfigRequest req) {
        return ResponseEntity.ok(service.updateConfig(tenantId, req));
    }

    // ---------- GetSPMetadata ----------

    @GetMapping("/metadata")
    public ResponseEntity<?> getSpMetadata(@RequestHeader("X-Tenant-ID") String tenantId) {
        String xml = service.getSpMetadata(tenantId);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(new MediaType("application", "samlmetadata+xml"))
                .body(xml);
    }

    // ---------- HandleACS ----------

    @PostMapping("/acs/{tenantId}")
    public ResponseEntity<?> handleAcs(@PathVariable String tenantId,
                                       @RequestParam(value = "SAMLResponse", required = false) String samlResponse) {
        return ResponseEntity.ok(service.handleAcs(tenantId, samlResponse));
    }

    // ---------- Enable ----------

    @PostMapping("/enable")
    public ResponseEntity<?> enable(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.enable(tenantId));
    }

    // ---------- Disable ----------

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.disable(tenantId));
    }

    // ---------- GenerateKeyPair ----------

    @PostMapping("/generate-keys")
    public ResponseEntity<?> generateKeyPair() {
        KeyPairGeneratorUtil.GeneratedKeys keys = service.generateKeyPair();
        return ResponseEntity.ok(Map.of("certificate", keys.certificatePem(), "private_key", keys.privateKeyPem()));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(SsoServiceException.class)
    public ResponseEntity<ApiError> handleService(SsoServiceException ex) {
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
