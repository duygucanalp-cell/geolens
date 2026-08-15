package dev.geolens.policy.service;

import org.springframework.http.HttpStatus;

/** Policy iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class PolicyServiceException extends RuntimeException {
    private final HttpStatus status;

    public PolicyServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
