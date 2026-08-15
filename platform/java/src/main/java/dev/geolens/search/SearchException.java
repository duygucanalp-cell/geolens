package dev.geolens.search;

/**
 * Elasticsearch istemci hatası — Go {@code fmt.Errorf("es ...")} dönüşlerinin
 * Java karşılığı (BillingException/ServingException deseni).
 */
public class SearchException extends RuntimeException {

    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
