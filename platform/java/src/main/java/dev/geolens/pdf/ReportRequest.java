package dev.geolens.pdf;

/** PDF rapor isteği — Go {@code pdf.ReportRequest} portu. */
public record ReportRequest(
        ReportType type,
        String workspaceId,
        String tenantId,
        String brandId,
        String brandName) {
}
