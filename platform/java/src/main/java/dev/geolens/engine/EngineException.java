package dev.geolens.engine;

/** AI motoru çağrı/ayrıştırma hatası — Go hata sarmalayıcılarının portu. */
public class EngineException extends RuntimeException {

    public EngineException(String message) {
        super(message);
    }

    public EngineException(String message, Throwable cause) {
        super(message, cause);
    }
}