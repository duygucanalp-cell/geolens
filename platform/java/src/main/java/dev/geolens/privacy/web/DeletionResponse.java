package dev.geolens.privacy.web;

/** Silme talebi yanıtı — Go {@code privacy.deletionResponse} portu. */
public record DeletionResponse(String id, String status, String message) {
}
