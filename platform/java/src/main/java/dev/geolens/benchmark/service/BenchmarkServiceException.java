package dev.geolens.benchmark.service;

import org.springframework.http.HttpStatus;

/** Benchmark iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class BenchmarkServiceException extends RuntimeException {
    private final HttpStatus status;

    public BenchmarkServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
