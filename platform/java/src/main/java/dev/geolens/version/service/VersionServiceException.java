package dev.geolens.version.service;

import org.springframework.http.HttpStatus;

/** Versiyon takibi iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class VersionServiceException extends RuntimeException {
    private final HttpStatus status;

    public VersionServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
