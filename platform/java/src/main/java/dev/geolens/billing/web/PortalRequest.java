package dev.geolens.billing.web;

/** Billing Portal oturumu isteği — Go {@code CreatePortalSession} istek gövdesi. */
public record PortalRequest(
        String returnUrl) {
}
