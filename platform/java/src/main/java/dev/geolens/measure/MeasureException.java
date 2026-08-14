package dev.geolens.measure;

/** Ölçüm/skor hesaplama hatası — Go {@code fmt.Errorf} hata sarmalayıcılarının portu. */
public class MeasureException extends RuntimeException {

    public MeasureException(String message) {
        super(message);
    }

    public MeasureException(String message, Throwable cause) {
        super(message, cause);
    }
}