package dev.geolens.seo.service;

import org.springframework.http.HttpStatus;

/** SEO iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class SeoServiceException extends RuntimeException {
    private final HttpStatus status;

    public SeoServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
