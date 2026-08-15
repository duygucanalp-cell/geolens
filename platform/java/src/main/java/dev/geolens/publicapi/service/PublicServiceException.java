package dev.geolens.publicapi.service;

import org.springframework.http.HttpStatus;

/** Public API iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class PublicServiceException extends RuntimeException {
    private final HttpStatus status;

    public PublicServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
