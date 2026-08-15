package dev.geolens.redteam.service;

import org.springframework.http.HttpStatus;

/** Red team iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class RedteamServiceException extends RuntimeException {
    private final HttpStatus status;

    public RedteamServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
