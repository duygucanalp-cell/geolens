package dev.geolens.engine;

import java.util.List;

/** Tek AI motoru çağrısının normalleştirilmiş ham yanıtı — Go {@code engine.RawResponse} portu. */
public record RawResponse(
        String engineName,
        String requestId,
        String content,
        List<Citation> citations,
        boolean hasSearch,
        Tier tier,
        String fidelityLabel,
        String s3Ref) {

    public RawResponse {
        if (citations == null) {
            citations = List.of();
        }
        if (s3Ref == null) {
            s3Ref = "";
        }
    }
}