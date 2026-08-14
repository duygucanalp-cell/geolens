package dev.geolens.sentiment.ml;

/** Serving çağrı hatası — çağıran kural tabanlı bileşene düşmelidir (0421 M-4). */
public class ServingException extends RuntimeException {

    public ServingException(String message) {
        super(message);
    }

    public ServingException(String message, Throwable cause) {
        super(message, cause);
    }
}