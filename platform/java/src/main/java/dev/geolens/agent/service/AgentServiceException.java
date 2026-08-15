package dev.geolens.agent.service;

import org.springframework.http.HttpStatus;

/** Agent iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
public class AgentServiceException extends RuntimeException {
    private final HttpStatus status;

    public AgentServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
