package dev.geolens.alert.web;

/** Alert kuralı oluşturma isteği — Go {@code alert.Create} input portu. */
public record AlertRuleRequest(String brandId, String name, String metric, String condition,
                               double threshold, String channel, String channelConfig, int cooldownMin) {
}
