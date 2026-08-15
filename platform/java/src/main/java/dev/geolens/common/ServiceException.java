package dev.geolens.common;

import org.springframework.http.HttpStatus;

/**
 * Tüm bağlam servislerinin ortak iş mantığı hatası — controller {@code @ExceptionHandler}
 * ile HTTP hatasına çevirir. Tekrarlanan per-paket {@code *ServiceException} sınıfları
 * bu tipte birleştirildi; servis hatanın HTTP durumunu {@code status()} ile taşır.
 */
public class ServiceException extends RuntimeException {
    private final HttpStatus status;

    public ServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
