package dev.geolens.billing.service;

import org.springframework.http.HttpStatus;

/** Faturalama iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class BillingServiceException extends RuntimeException {
    private final HttpStatus status;

    public BillingServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
