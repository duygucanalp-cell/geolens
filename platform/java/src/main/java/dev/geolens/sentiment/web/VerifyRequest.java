package dev.geolens.sentiment.web;

/** POST /hallucination/{flagId}/verify istek gövdesi — Go handler req (verified). */
public record VerifyRequest(boolean verified) {
}