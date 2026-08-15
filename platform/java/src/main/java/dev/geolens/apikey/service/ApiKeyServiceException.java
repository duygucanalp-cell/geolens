package dev.geolens.apikey.service;

import org.springframework.http.HttpStatus;

/** API anahtarı iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class ApiKeyServiceException extends RuntimeException {
    private final HttpStatus status;

    public ApiKeyServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
