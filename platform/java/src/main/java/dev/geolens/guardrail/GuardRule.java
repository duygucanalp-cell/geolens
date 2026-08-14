package dev.geolens.guardrail;

/**
 * Değerlendirmede kullanılan kural satırı — Go {@code guardRule} struct portu.
 *
 * @param id       kural ID'si
 * @param name     kural adı
 * @param category kural kategorisi (prompt_injection, pii_leakage, ...)
 * @param pattern  regex veya anahtar kelime deseni (Go sözdizimi: /.../i, /.../, düz metin)
 * @param action   aksiyon: block, flag, log
 * @param severity önem: low, medium, high, critical
 */
public record GuardRule(String id, String name, String category, String pattern, String action, String severity) {
}
