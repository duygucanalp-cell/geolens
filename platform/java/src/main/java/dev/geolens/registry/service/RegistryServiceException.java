package dev.geolens.registry.service;

import org.springframework.http.HttpStatus;

/** Registry iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class RegistryServiceException extends RuntimeException {
    private final HttpStatus status;

    public RegistryServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
