package dev.geolens.recommendation.persistence;

/** Kayıt bulunamadı veya çalışma alanına ait değil — Go {@code MarkApplied} hata mesajının karşılığı. */
public class RecommendationNotFoundException extends RuntimeException {

    public RecommendationNotFoundException(String message) {
        super(message);
    }
}