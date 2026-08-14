package dev.geolens.seo;

/** SEO/Google entegrasyon hata tipi — Go {@code fmt.Errorf} karşılığı. */
public class SeoException extends RuntimeException {

    public SeoException(String message) {
        super(message);
    }

    public SeoException(String message, Throwable cause) {
        super(message, cause);
    }
}
