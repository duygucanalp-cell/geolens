package dev.geolens.pdf;

/** PDF üretim hatası — Go {@code pdf} oluşturma hataları karşılığı. */
public class PdfGenerationException extends RuntimeException {
    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
