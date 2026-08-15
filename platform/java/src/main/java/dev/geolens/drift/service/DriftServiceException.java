package dev.geolens.drift.service;

import org.springframework.http.HttpStatus;

/** Drift iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class DriftServiceException extends RuntimeException {
    private final HttpStatus status;

    public DriftServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
