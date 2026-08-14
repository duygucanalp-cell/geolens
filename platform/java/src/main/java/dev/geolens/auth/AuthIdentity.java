package dev.geolens.auth;

/** Doğrulanmış token'dan çözülen kimlik bağlamı — Go {@code TokenValidator} dönüşü. */
public record AuthIdentity(String userId, String tenantId, String role) {
}
