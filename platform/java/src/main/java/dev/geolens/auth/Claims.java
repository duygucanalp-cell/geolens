package dev.geolens.auth;

import java.time.Instant;

/**
 * GeoLens platform token'larının JWT claim'leri — Go {@code auth.Claims} portu.
 * Registered claims (jti/iat/exp/sub) + user_id/tenant_id/role.
 */
public record Claims(
        String id,
        Instant issuedAt,
        Instant expiresAt,
        String subject,
        String userId,
        String tenantId,
        String role) {
}
