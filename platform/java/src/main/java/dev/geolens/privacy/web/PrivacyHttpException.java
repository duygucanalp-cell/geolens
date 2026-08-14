package dev.geolens.privacy.web;

import org.springframework.http.HttpStatus;

/** Transaction içinde tespit edilen geçersiz istekleri dışarı taşımak için — Go erken rollback + WriteJSON karşılığı. */
public class PrivacyHttpException extends RuntimeException {
    private final HttpStatus status;

    public PrivacyHttpException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
