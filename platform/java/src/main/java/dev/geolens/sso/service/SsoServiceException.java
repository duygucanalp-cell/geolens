package dev.geolens.sso.service;

import org.springframework.http.HttpStatus;

/** SSO iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class SsoServiceException extends RuntimeException {
    private final HttpStatus status;

    public SsoServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
