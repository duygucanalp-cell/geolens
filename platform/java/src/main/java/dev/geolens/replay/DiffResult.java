package dev.geolens.replay;

/**
 * İki snapshot arasındaki fark — Go {@code DiffResult} struct portu (FR-D12).
 */
public record DiffResult(
        String snapshotA,
        String snapshotB,
        String brandId,
        String engineName,
        String promptText,
        boolean hasChanged,
        String changes,
        String analyzedAt) {
}
