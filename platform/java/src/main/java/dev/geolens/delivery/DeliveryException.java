package dev.geolens.delivery;

/** Teslimat (delivery) hatası — Go {@code fmt.Errorf("delivery: ...")} portu. */
public class DeliveryException extends RuntimeException {

    public DeliveryException(String message) {
        super(message);
    }

    public DeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}