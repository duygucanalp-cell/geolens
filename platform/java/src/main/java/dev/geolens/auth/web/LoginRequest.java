package dev.geolens.auth.web;

/** Giriş isteği — Go {@code loginRequest} portu. */
public record LoginRequest(String email, String password) {
}