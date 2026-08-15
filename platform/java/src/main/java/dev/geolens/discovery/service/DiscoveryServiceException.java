package dev.geolens.discovery.service;

import org.springframework.http.HttpStatus;

/** Discovery iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class DiscoveryServiceException extends RuntimeException {
    private final HttpStatus status;

    public DiscoveryServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
