package dev.geolens.measure.service;

import org.springframework.http.HttpStatus;

/** Measure iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class MeasureServiceException extends RuntimeException {
    private final HttpStatus status;

    public MeasureServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
