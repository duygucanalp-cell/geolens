package dev.geolens.bias.service;

import org.springframework.http.HttpStatus;

/** Bias/Fairness iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class BiasServiceException extends RuntimeException {
    private final HttpStatus status;

    public BiasServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
