package dev.geolens.alert.service;

import org.springframework.http.HttpStatus;

/** Alert iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class AlertServiceException extends RuntimeException {
    private final HttpStatus status;

    public AlertServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
