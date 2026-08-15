package dev.geolens.privacy.service;

import org.springframework.http.HttpStatus;

/** Privacy iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class PrivacyServiceException extends RuntimeException {
    private final HttpStatus status;

    public PrivacyServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
