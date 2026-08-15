package dev.geolens.sentiment.service;

import org.springframework.http.HttpStatus;

/** Sentiment iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class SentimentServiceException extends RuntimeException {
    private final HttpStatus status;

    public SentimentServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
