package dev.geolens.auth.web;

/** Kimlik doğrulama yanıtı — Go {@code authResponse} portu. */
public record AuthResponse(
        String token,
        String expiresAt,
        String userId,
        String tenantId,
        String workspaceId,
        String role) {
}