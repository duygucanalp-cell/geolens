package dev.geolens.sentiment.ml;

import java.util.List;

/**
 * ML serving istemcisi arayüzü — Go {@code *ml.Client} portu (0421 A0-3).
 * Serving ulaşılamazsa veya hata dönerse çağıran taraf kural tabanlı bileşene
 * fallback yapar (0421 M-4). Yapılandırılmadığında (ML_SERVING_URL boş) davranış
 * kural tabanlıdır.
 */
public interface MlClient {

    /** Sentiment modeli sonucu (0421 A2-1). Label sırası [negative, neutral, positive]. */
    record SentimentPrediction(String modelVersion, String label, double confidence, double[] probabilities) {
    }

    /** Cross-source tespiti için girdi yanıtı (0421 A2-4). */
    record HallucinationResponse(String id, String engine, String text) {
    }

    /** Serving cross-source tespit çıktısı. */
    record HallucinationFinding(String type, String severity, String description, double confidence, String engine) {
    }

    /** Tek metin sentiment tahmini. Serving hatası istisna fırlatır — çağıran kural tabanlıya düşer. */
    SentimentPrediction predictSentiment(String text);

    /** Cross-source hallüsinasyon tespiti — en az 2 yanıt gerekir. */
    List<HallucinationFinding> detectHallucinations(List<HallucinationResponse> responses);
}