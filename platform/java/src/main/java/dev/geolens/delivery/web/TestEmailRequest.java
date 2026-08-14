package dev.geolens.delivery.web;

/** Test e-postası isteği — Go {@code delivery.handler} SendTestEmail body'si. */
public record TestEmailRequest(String email, String subject, String body) {
}
