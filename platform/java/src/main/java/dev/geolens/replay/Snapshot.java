package dev.geolens.replay;

/**
 * Conversation snapshot — Go {@code Snapshot} struct portu (FR-D12).
 */
public record Snapshot(
        String id,
        String brandId,
        String promptText,
        String engineName,
        String responsePreview,
        String responseFull,
        String contentHash,
        String s3Ref,
        String replayId,
        String createdAt) {
}
