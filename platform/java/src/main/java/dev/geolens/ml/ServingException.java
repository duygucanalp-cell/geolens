package dev.geolens.ml;

/** ML serving hatası — Go {@code ml} paketi hata sarmalayıcısı portu (0421 M-4 fallback). */
public class ServingException extends RuntimeException {

    public ServingException(String message) {
        super(message);
    }

    public ServingException(String message, Throwable cause) {
        super(message, cause);
    }
}