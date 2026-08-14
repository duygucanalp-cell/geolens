package dev.geolens.delivery;

import java.util.List;

/**
 * Workspace bildirim ayarları — Go {@code delivery.NotificationSettings} portu.
 * webhookUrl/webhookKind/webhookActive set edildiğinde bildirimler webhook üzerinden gider.
 */
public record NotificationSettings(
        String workspaceId,
        String emailAddress,
        boolean digestEnabled,
        String digestDay,
        String digestTime,
        String digestFormat,
        boolean notifyOnDrop,
        int dropThreshold,
        String webhookUrl,
        String webhookKind,
        boolean webhookActive) {

    /** Kabul edilen digest_day değerleri — Go {@code ValidDays} portu. */
    public static final List<String> VALID_DAYS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    /** Kabul edilen digest_format değerleri — Go {@code ValidDigestFormats} portu. */
    public static final List<String> VALID_DIGEST_FORMATS = List.of("email", "pdf", "both");

    /** Kabul edilen webhook türleri — Go sabit listesinin portu. */
    public static final List<String> VALID_WEBHOOK_KINDS = List.of(
            "generic", "slack", "teams", "discord", "pagerduty");

    /** Kayıt yoksa dönen varsayılan ayarlar — Go {@code GetSettings} fallback portu. */
    public static NotificationSettings defaults(String workspaceId) {
        return new NotificationSettings(workspaceId, "", true, "monday", "09:00", "email",
                true, 10, "", "", false);
    }
}