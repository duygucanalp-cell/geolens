package dev.geolens.guardrail.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Guardrail iş mantığı hataları — controller tarafından HTTP hatasına çevrilir. */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class GuardrailServiceException extends RuntimeException {
    public GuardrailServiceException(String message) {
        super(message);
    }
}