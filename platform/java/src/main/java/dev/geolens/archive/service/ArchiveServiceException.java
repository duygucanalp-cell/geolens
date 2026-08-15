package dev.geolens.archive.service;

import org.springframework.http.HttpStatus;

/** Response Archive iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class ArchiveServiceException extends RuntimeException {
    private final HttpStatus status;

    public ArchiveServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
