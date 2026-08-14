package dev.geolens.alert.web;

/** Alert kuralı güncelleme isteği — Go {@code alert.Update} input portu (kısmi alanlar). */
public record UpdateAlertRuleRequest(String name, String metric, String condition, Double threshold,
                                     String channel, String channelConfig, Boolean enabled, Integer cooldownMin) {
}
