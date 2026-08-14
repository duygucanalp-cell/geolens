package dev.geolens.technicalgeo;

/**
 * LLM bot erişim analiz sonucu — Go {@code BotAnalysisResult} struct portu (FR-B6).
 */
public record BotAnalysisResult(
        String id,
        String brandId,
        String botName,
        String url,
        boolean isBlocked,
        String robotsTxtRule,
        double gesScore,
        String analyzedAt) {
}
