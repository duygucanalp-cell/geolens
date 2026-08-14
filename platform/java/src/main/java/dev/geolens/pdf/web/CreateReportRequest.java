package dev.geolens.pdf.web;

/** Async rapor talebi isteği — Go {@code pdf.createReportRequest} portu. */
public record CreateReportRequest(String reportType, String brandId, String brandName) {
}
