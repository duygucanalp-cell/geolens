package dev.geolens.billing;

/** Faturalama (Stripe/e-Fatura) hata istisnası — Go {@code fmt.Errorf} hata dönüşleri karşılığı. */
public class BillingException extends RuntimeException {

    public BillingException(String message) {
        super(message);
    }

    public BillingException(String message, Throwable cause) {
        super(message, cause);
    }
}
