package dev.geolens.pilot.service;

import org.springframework.http.HttpStatus;

/** Pilot iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class PilotServiceException extends RuntimeException {
    private final HttpStatus status;

    public PilotServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
