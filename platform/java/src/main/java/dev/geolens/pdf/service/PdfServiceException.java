package dev.geolens.pdf.service;

import org.springframework.http.HttpStatus;

/** PDF iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class PdfServiceException extends RuntimeException {
    private final HttpStatus status;

    public PdfServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
