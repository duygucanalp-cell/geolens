package dev.geolens.delivery;

import java.time.Instant;
import java.util.Map;

/**
 * Teslim edilecek tek bir bildirim — Go {@code delivery.Notification} portu.
 * Webhook alanları (webhookUrl, webhookKind) channel=webhook iken zorunludur.
 */
public record Notification(
        String id,
        String tenantId,
        String userId,
        String workspaceId,
        String type,
        String channel,
        String title,
        String body,
        String htmlBody,
        Map<String, Object> data,
        String status,
        Instant sentAt,
        Instant createdAt,
        boolean isRead,
        String webhookUrl,
        String webhookKind) {

    public Notification {
        if (data == null) {
            data = Map.of();
        }
    }

    /** Go {@code Status: DeliverySent, SentAt: now} güncelleme karşılığı (immutable kopya). */
    public Notification sent() {
        return new Notification(id, tenantId, userId, workspaceId, type, channel, title, body, htmlBody,
                data, DeliveryConstants.DELIVERY_SENT, Instant.now(), createdAt, isRead, webhookUrl, webhookKind);
    }

    public Notification failed() {
        return new Notification(id, tenantId, userId, workspaceId, type, channel, title, body, htmlBody,
                data, DeliveryConstants.DELIVERY_FAILED, sentAt, createdAt, isRead, webhookUrl, webhookKind);
    }
}