package dev.geolens.retention.service;

import org.springframework.http.HttpStatus;

/** Veri Saklama iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class RetentionServiceException extends RuntimeException {
    private final HttpStatus status;

    public RetentionServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
