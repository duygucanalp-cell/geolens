package dev.geolens.prompt;

import java.util.List;
import java.util.Map;

/**
 * Prompt denetim sonucu — Go {@code auditPrompt} dönüşü portu (R9).
 *
 * @param score  0-1 aralığında kalite/güvenlik skoru
 * @param issues tespit edilen sorunlar (type/severity/message)
 * @param status passed | flagged | failed
 */
public record PromptAuditResult(double score, List<Map<String, Object>> issues, String status) {
}
