package dev.geolens.optimize.service;

import org.springframework.http.HttpStatus;

/** Optimization iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class OptimizeServiceException extends RuntimeException {
    private final HttpStatus status;

    public OptimizeServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
