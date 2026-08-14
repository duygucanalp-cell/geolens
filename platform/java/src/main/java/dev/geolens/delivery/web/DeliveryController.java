package dev.geolens.delivery.web;

import dev.geolens.delivery.DeliveryService;
import dev.geolens.delivery.Notification;
import dev.geolens.delivery.NotificationSettings;
import dev.geolens.delivery.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;
import java.util.Map;

/**
 * Bildirim/teslimat REST controller'ı — Go {@code delivery.handler} portu.
 * <p>Route'lar (go cmd/api): GET/PUT /v1/workspaces/{ws}/notifications/settings,
 * POST /v1/workspaces/{ws}/notifications/test, GET /v1/workspaces/{ws}/notifications,
 * POST /v1/workspaces/{ws}/notifications/{notificationId}/read (FR-D10).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/notifications")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            return ResponseEntity.ok(service.getSettings(workspaceId, tenantId));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "ayarlar okunamadı");
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestBody NotificationSettings req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        NotificationSettings settings = withWorkspaceId(req, workspaceId);
        try {
            service.updateSettings(settings, tenantId);
        } catch (ValidationException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestEmail(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestBody(required = false) TestEmailRequest req) {
        String email = (req == null || req.email() == null || req.email().isBlank())
                ? tenantId + "@example.com"
                : req.email();
        String subject = (req == null || req.subject() == null || req.subject().isBlank())
                ? "GeoLens — Test Bildirimi"
                : req.subject();
        String body = (req == null || req.body() == null || req.body().isBlank())
                ? "<h2>Bu bir test bildirimidir</h2><p>E-posta altyapısı çalışıyor.</p>"
                : req.body();

        try {
            service.sendEmail(email, subject, body);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "sent", "to", email));
    }

    @GetMapping
    public ResponseEntity<?> listNotifications(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestParam(value = "unread", required = false) String unread,
                                               @RequestParam(value = "limit", required = false) String limitParam) {
        boolean unreadOnly = "true".equals(unread);
        int limit = 50;
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                limit = Integer.parseInt(limitParam);
            } catch (NumberFormatException ignored) {
                // geçersiz limit varsayılan kalır (Go ile aynı)
            }
        }
        List<Notification> notifs;
        try {
            notifs = service.listInAppNotifications(tenantId, workspaceId, unreadOnly, limit);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "bildirimler alınamadı");
        }
        return ResponseEntity.ok(notifs == null ? List.of() : notifs);
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markNotificationRead(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @PathVariable String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "notificationId gerekli");
        }
        try {
            service.markInAppNotificationRead(tenantId, notificationId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "işaretlenemedi");
        }
        return ResponseEntity.ok(Map.of("status", "read"));
    }

    private static NotificationSettings withWorkspaceId(NotificationSettings s, String workspaceId) {
        return new NotificationSettings(workspaceId, s.emailAddress(), s.digestEnabled(), s.digestDay(),
                s.digestTime(), s.digestFormat(), s.notifyOnDrop(), s.dropThreshold(),
                s.webhookUrl(), s.webhookKind(), s.webhookActive());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
