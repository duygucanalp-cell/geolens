package dev.geolens.auth.web;

import dev.geolens.auth.service.AuthService;
import dev.geolens.auth.service.AuthServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Kimlik doğrulama ve yetkilendirme REST controller'ı — Go {@code auth.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/auth/register, POST /v1/auth/login,
 * POST /v1/auth/refresh, POST /v1/auth/logout, POST /v1/auth/accept-invitation,
 * GET /v1/tenant, GET/POST /v1/tenant/members, PATCH /v1/tenant/members/{userId}/role,
 * GET/POST /v1/tenant/invitations.
 * <p>İş mantığı {@link AuthService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
public class AuthController {

    private static final Set<String> VALID_ROLES = Set.of("admin", "editor", "viewer");

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/v1/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().isBlank()
                || req.name() == null || req.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "e-posta, şifre ve isim zorunludur");
        }
        if (req.password().length() < 8) {
            return error(HttpStatus.BAD_REQUEST, "şifre en az 8 karakter olmalıdır");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(req));
    }

    @PostMapping("/v1/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "e-posta ve şifre zorunludur");
        }

        return ResponseEntity.ok(service.login(req));
    }

    @PostMapping("/v1/auth/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tokenStr = bearer(authHeader);
        if (tokenStr.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "kimlik doğrulama gerekli");
        }
        return ResponseEntity.ok(service.refresh(tokenStr));
    }

    @PostMapping("/v1/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        service.logout(bearer(authHeader));
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    @GetMapping("/v1/tenant")
    public ResponseEntity<Map<String, Object>> getTenant(@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new AuthServiceException(HttpStatus.UNAUTHORIZED, "kimlik doğrulama gerekli");
        }
        return ResponseEntity.ok(service.getTenant(tenantId));
    }

    @GetMapping("/v1/tenant/members")
    public ResponseEntity<Map<String, Object>> listMembers(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listMembers(tenantId));
    }

    @PatchMapping("/v1/tenant/members/{userId}/role")
    public ResponseEntity<?> updateMemberRole(@RequestHeader("X-Tenant-ID") String tenantId,
                                              @PathVariable String userId,
                                              @RequestBody UpdateRoleRequest req) {
        if (userId == null || userId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "kullanıcı ID gerekli");
        }
        if (req == null || req.role() == null || !VALID_ROLES.contains(req.role())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz rol (admin, editor, viewer)");
        }
        return ResponseEntity.ok(service.updateMemberRole(tenantId, userId, req));
    }

    @PostMapping("/v1/tenant/invitations")
    public ResponseEntity<?> inviteMember(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestHeader(value = "X-User-ID", required = false) String userId,
                                          @RequestBody InviteRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()
                || req.workspaceId() == null || req.workspaceId().isBlank()
                || req.role() == null || req.role().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "e-posta, çalışma alanı ve rol zorunludur");
        }
        if (!VALID_ROLES.contains(req.role())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz rol (admin, editor, viewer)");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inviteMember(tenantId, userId, req));
    }

    @PostMapping("/v1/auth/accept-invitation")
    public ResponseEntity<?> acceptInvitation(@RequestBody AcceptInvitationRequest req) {
        if (req == null || req.token() == null || req.token().isBlank()
                || req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().isBlank()
                || req.name() == null || req.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "token, e-posta, şifre ve isim zorunludur");
        }
        return ResponseEntity.ok(service.acceptInvitation(req));
    }

    @GetMapping("/v1/tenant/invitations")
    public ResponseEntity<Map<String, Object>> listInvitations(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listInvitations(tenantId));
    }

    private static String bearer(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length());
        }
        return "";
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    @ExceptionHandler(AuthServiceException.class)
    public ResponseEntity<ApiError> handleService(AuthServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
