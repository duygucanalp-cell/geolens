package dev.geolens.privacy.web;

/** KVKK veri silme talebi isteği — Go {@code privacy.deletionRequest} portu. */
public record DeletionRequest(String reason) {
}
