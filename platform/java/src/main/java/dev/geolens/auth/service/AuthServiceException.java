package dev.geolens.auth.service;

import org.springframework.http.HttpStatus;

/** Auth iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class AuthServiceException extends RuntimeException {
    private final HttpStatus status;

    public AuthServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
