package dev.geolens.audit;

import java.util.List;

/** Site'nin bilinen AI user agent'larına erişilebilirliği — Go {@code audit.BotAccessCheck} portu. */
public record BotAccessCheck(
        boolean accessible,
        int statusCode,
        long responseTimeMs,
        List<String> aiBotNamesTested) {

    public BotAccessCheck {
        if (aiBotNamesTested == null) {
            aiBotNamesTested = List.of();
        }
    }
}