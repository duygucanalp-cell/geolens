package dev.geolens.delivery;

/** Ayarlar doğrulama hatası — Go {@code validationError} marker tipinin portu. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}