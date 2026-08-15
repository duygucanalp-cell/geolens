package dev.geolens.audit.service;

import org.springframework.http.HttpStatus;

/** Audit web iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class AuditServiceException extends RuntimeException {
    private final HttpStatus status;

    public AuditServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
