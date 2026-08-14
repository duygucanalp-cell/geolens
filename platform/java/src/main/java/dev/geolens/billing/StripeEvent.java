package dev.geolens.billing;

import com.fasterxml.jackson.databind.JsonNode;

/** Stripe webhook olayı — Go {@code billing.StripeEvent} portu. */
public record StripeEvent(
        String id,
        String type,
        String apiVersion,
        JsonNode object,
        long created) {

    /** Olay nesnesinin metadata.tenant_id değerini döndürür (yoksa boş). */
    public String metadataTenantId() {
        JsonNode metadata = object == null ? null : object.path("metadata");
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return "";
        }
        return metadata.path("tenant_id").asText("");
    }
}
