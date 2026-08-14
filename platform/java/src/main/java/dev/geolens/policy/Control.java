package dev.geolens.policy;

/**
 * Policy control — Go {@code Control} struct portu (R4).
 */
public record Control(
        String id,
        String packId,
        String tenantId,
        String controlId,
        String title,
        String description,
        String category,
        String status,
        String evidence,
        String dueDate,
        String createdAt,
        String updatedAt) {
}
