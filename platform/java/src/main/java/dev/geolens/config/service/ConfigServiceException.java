package dev.geolens.config.service;

import org.springframework.http.HttpStatus;

/** Config iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class ConfigServiceException extends RuntimeException {
    private final HttpStatus status;

    public ConfigServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
