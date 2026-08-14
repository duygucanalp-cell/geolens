package dev.geolens.auth;

import java.time.Instant;

/** {@code JWTService.generateToken} sonucu — imzalı token + son kullanma zamanı. */
public record TokenResult(String token, Instant expiresAt) {
}
