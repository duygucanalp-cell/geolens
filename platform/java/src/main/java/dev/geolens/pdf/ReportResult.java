package dev.geolens.pdf;

import java.time.Instant;

/** Üretilen PDF sonucu — Go {@code pdf.ReportResult} portu. */
public record ReportResult(
        String id,
        ReportType type,
        byte[] data,
        String fileName,
        int pageCount,
        Instant generatedAt,
        String s3Ref) {
}
