package dev.geolens.delivery;

/** E-posta teslimatı yapılandırması — Go {@code delivery.EmailConfig} portu. */
public record EmailConfig(String fromName, String fromEmail, String sendGridKey) {

    public static EmailConfig mock() {
        return new EmailConfig("GeoLens", "noreply@geolens.ai", "mock");
    }
}