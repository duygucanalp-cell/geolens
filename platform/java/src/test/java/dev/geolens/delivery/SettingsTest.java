package dev.geolens.delivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go {@code ValidateSettings} portunun doğrudan testi (handler 400 davranışını yansıtır). */
class SettingsTest {

    private static NotificationSettings valid() {
        return new NotificationSettings("ws-1", "user@example.com", true, "monday", "09:00", "email",
                true, 10, "", "", false);
    }

    @Test
    void validSettingsPass() {
        DeliveryService.validateSettings(valid());
    }

    @Test
    void emptyEmailFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "emailAddress", "")));
    }

    @Test
    void emptyDayFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestDay", "")));
    }

    @Test
    void invalidDayFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestDay", "funday")));
    }

    @Test
    void emptyTimeFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestTime", "")));
    }

    @Test
    void badTimeFormatFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestTime", "9:00")));
    }

    @Test
    void outOfRangeTimeFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestTime", "25:00")));
    }

    @Test
    void emptyFormatFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestFormat", "")));
    }

    @Test
    void invalidFormatFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "digestFormat", "csv")));
    }

    @Test
    void dropThresholdOutOfRangeFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "dropThreshold", 0)));
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "dropThreshold", 101)));
    }

    @Test
    void webhookActiveWithoutUrlFails() {
        assertThrows(ValidationException.class,
                () -> DeliveryService.validateSettings(with(valid(), "webhookActive", true)));
    }

    @Test
    void webhookActiveWithoutKindFails() {
        NotificationSettings s = new NotificationSettings("ws-1", "user@example.com", true, "monday", "09:00", "email",
                true, 10, "https://hooks.example.com/x", "", true);
        assertThrows(ValidationException.class, () -> DeliveryService.validateSettings(s));
    }

    @Test
    void webhookInvalidKindFails() {
        NotificationSettings s = new NotificationSettings("ws-1", "user@example.com", true, "monday", "09:00", "email",
                true, 10, "https://hooks.example.com/x", "kafka", true);
        assertThrows(ValidationException.class, () -> DeliveryService.validateSettings(s));
    }

    @Test
    void webhookActiveValidPasses() {
        NotificationSettings s = new NotificationSettings("ws-1", "user@example.com", true, "monday", "09:00", "email",
                true, 10, "https://hooks.example.com/x", DeliveryConstants.WEBHOOK_KIND_SLACK, true);
        DeliveryService.validateSettings(s);
    }

    private static NotificationSettings with(NotificationSettings base, String field, Object value) {
        String ws = base.workspaceId();
        String email = base.emailAddress();
        boolean enabled = base.digestEnabled();
        String day = base.digestDay();
        String time = base.digestTime();
        String format = base.digestFormat();
        boolean notify = base.notifyOnDrop();
        int threshold = base.dropThreshold();
        String url = base.webhookUrl();
        String kind = base.webhookKind();
        boolean active = base.webhookActive();

        switch (field) {
            case "emailAddress" -> email = (String) value;
            case "digestDay" -> day = (String) value;
            case "digestTime" -> time = (String) value;
            case "digestFormat" -> format = (String) value;
            case "dropThreshold" -> threshold = (Integer) value;
            case "webhookActive" -> active = (Boolean) value;
            default -> throw new IllegalArgumentException(field);
        }
        return new NotificationSettings(ws, email, enabled, day, time, format, notify, threshold, url, kind, active);
    }
}