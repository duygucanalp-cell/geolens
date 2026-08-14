package dev.geolens.version.web;

/** Versiyon kaydı isteği — Go {@code version.RecordVersion} input portu. */
public record VersionEntryRequest(String entityType, String entityId, String entityName,
                                  String oldVersion, String newVersion, String changeNotes, String changedBy) {
}
