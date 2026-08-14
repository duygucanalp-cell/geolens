package dev.geolens.measure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Outbox'ta saklanan ölçüm job'u — Go {@code measure.JobPayload} portu. */
public record MeasureJob(
        String brandId,
        String brandName,
        String websiteUrl,
        String panelId,
        String workspaceId,
        String tenantId,
        String engineName,
        String promptText,
        int sampleIndex) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("job payload serileştirme", e);
        }
    }
}
