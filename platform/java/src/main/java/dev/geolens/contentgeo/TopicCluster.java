package dev.geolens.contentgeo;

/**
 * Topic cluster önerisi — Go {@code contentgeo.TopicCluster} struct portu (FR-E6).
 */
public record TopicCluster(
        String id,
        String brandId,
        String topicName,
        double opportunityScore,
        String relevance,
        String recommendation,
        String priority) {
}
