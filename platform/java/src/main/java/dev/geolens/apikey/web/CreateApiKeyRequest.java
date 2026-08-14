package dev.geolens.apikey.web;

/** API anahtarı oluşturma isteği — Go {@code apikey.Create} input portu. */
public record CreateApiKeyRequest(String name, String role, String expiresAt) {
}
