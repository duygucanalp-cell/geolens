package dev.geolens.auth;

/** Kimlik doğrulama hataları — Go {@code auth} hata deseni portu. */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
