package dev.geolens.billing.web;

/** Checkout oturumu isteği — Go {@code CreateCheckoutSession} istek gövdesi. */
public record CheckoutRequest(
        String tier,
        String currency,
        String successUrl,
        String cancelUrl) {
}
