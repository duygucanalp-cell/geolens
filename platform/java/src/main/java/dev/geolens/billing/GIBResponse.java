package dev.geolens.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/** GİB entegrasyon servisinin fatura gönderimine verdiği yanıt — Go {@code billing.GIBResponse} portu. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GIBResponse(
        @JsonProperty("status") GIBStatus status,
        @JsonProperty("response_id") String responseId,
        @JsonProperty("message") String message,
        @JsonProperty("submitted_at") OffsetDateTime submittedAt) {
}
