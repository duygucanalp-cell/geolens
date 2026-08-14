package dev.geolens.auth.web;

/** Self-serve kayıt isteği — Go {@code registerRequest} portu (D-07). */
public record RegisterRequest(String email, String password, String name) {
}