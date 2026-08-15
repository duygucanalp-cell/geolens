package dev.geolens.technicalgeo.service;

import org.springframework.http.HttpStatus;

/** Technical GEO iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class TechnicalgeoServiceException extends RuntimeException {
    private final HttpStatus status;

    public TechnicalgeoServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
