package dev.geolens.cost.service;

import org.springframework.http.HttpStatus;

/** Maliyet iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class CostServiceException extends RuntimeException {
    private final HttpStatus status;

    public CostServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
