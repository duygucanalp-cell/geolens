package dev.geolens.explain.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Explain iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class ExplainServiceException extends RuntimeException {

    private final HttpStatus status;

    public ExplainServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}