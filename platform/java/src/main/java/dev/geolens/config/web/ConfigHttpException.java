package dev.geolens.config.web;

import org.springframework.http.HttpStatus;

/** Transaction içinde tespit edilen geçersiz istekleri dışarı taşımak için — Go'daki erken rollback + WriteJSON karşılığı. */
public class ConfigHttpException extends RuntimeException {
    private final HttpStatus status;

    public ConfigHttpException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
