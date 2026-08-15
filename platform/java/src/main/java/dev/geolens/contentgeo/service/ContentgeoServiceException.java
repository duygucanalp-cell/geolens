package dev.geolens.contentgeo.service;

import org.springframework.http.HttpStatus;

/** Content GEO iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class ContentgeoServiceException extends RuntimeException {
    private final HttpStatus status;

    public ContentgeoServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
