package dev.geolens.incident.service;

import org.springframework.http.HttpStatus;

/** Incident iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class IncidentServiceException extends RuntimeException {
    private final HttpStatus status;

    public IncidentServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
