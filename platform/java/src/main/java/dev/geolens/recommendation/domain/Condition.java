package dev.geolens.recommendation.domain;

/**
 * Bir öneri kuralındaki tek koşul (Go: {@code Condition}).
 * <p>Alanlar: örn. {@code "score.value"}, {@code "audit.robots_txt.disallowed_all"}.
 * Operatörler: {@code lt}, {@code gt}, {@code eq}, {@code contains}, {@code gte}, {@code lte}.
 */
public record Condition(String field, String operator, Object value) {
}