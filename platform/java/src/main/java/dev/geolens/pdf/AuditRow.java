package dev.geolens.pdf;

/** PDF raporları için denetim satırı — Go {@code pdf.AuditRow} portu. */
public record AuditRow(
        String category,
        String status,
        double score,
        String recommendation) {
}
