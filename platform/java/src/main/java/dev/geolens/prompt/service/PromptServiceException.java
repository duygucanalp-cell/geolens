package dev.geolens.prompt.service;

import org.springframework.http.HttpStatus;

/** Prompt iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class PromptServiceException extends RuntimeException {
    private final HttpStatus status;

    public PromptServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
