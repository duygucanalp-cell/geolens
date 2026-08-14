package dev.geolens.pdf;

/** Rapor bulunamadı hatası — Go {@code pdf} rapor bulunamadı karşılığı. */
public class PdfReportNotFoundException extends RuntimeException {
    public PdfReportNotFoundException(String message) {
        super(message);
    }
}
