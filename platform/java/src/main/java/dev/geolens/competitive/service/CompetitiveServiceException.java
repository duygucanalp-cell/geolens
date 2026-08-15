package dev.geolens.competitive.service;

import org.springframework.http.HttpStatus;

/** Competitive Gap iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class CompetitiveServiceException extends RuntimeException {
    private final HttpStatus status;

    public CompetitiveServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
