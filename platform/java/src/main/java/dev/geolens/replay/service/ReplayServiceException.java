package dev.geolens.replay.service;

import org.springframework.http.HttpStatus;

/** Conversation Replay iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class ReplayServiceException extends RuntimeException {
    private final HttpStatus status;

    public ReplayServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
