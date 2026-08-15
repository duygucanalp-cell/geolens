package dev.geolens.usage.service;

import org.springframework.http.HttpStatus;

/** Kullanım analitiği iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class UsageServiceException extends RuntimeException {
    private final HttpStatus status;

    public UsageServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
