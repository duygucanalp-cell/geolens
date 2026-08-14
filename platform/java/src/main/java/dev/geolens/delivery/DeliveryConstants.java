package dev.geolens.delivery;

/**
 * Teslimat sabitleri — Go {@code delivery.engine.go} const bloğunun portu.
 * Kanallar, webhook türleri, bildirim tipleri ve teslimat durumları.
 */
public final class DeliveryConstants {

    private DeliveryConstants() {
    }

    // ---- Kanallar ----
    public static final String CHANNEL_EMAIL = "email";
    public static final String CHANNEL_IN_APP = "in_app";
    public static final String CHANNEL_WEBHOOK = "webhook";

    // ---- Webhook türleri (HT2 webhook çeşitlendirme) ----
    public static final String WEBHOOK_KIND_GENERIC = "generic";
    public static final String WEBHOOK_KIND_SLACK = "slack";
    public static final String WEBHOOK_KIND_TEAMS = "teams";
    public static final String WEBHOOK_KIND_DISCORD = "discord";
    public static final String WEBHOOK_KIND_PAGERDUTY = "pagerduty";

    // ---- Bildirim tipleri ----
    public static final String NOTIFICATION_SCORE_DROP = "score_drop";
    public static final String NOTIFICATION_WEEKLY_DIGEST = "weekly_digest";
    public static final String NOTIFICATION_NEW_SUGGESTION = "new_suggestion";
    public static final String NOTIFICATION_AUDIT_COMPLETE = "audit_complete";

    // ---- Teslimat durumları ----
    public static final String DELIVERY_PENDING = "pending";
    public static final String DELIVERY_SENT = "sent";
    public static final String DELIVERY_FAILED = "failed";
    public static final String DELIVERY_DELIVERED = "delivered";
}