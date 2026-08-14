package dev.geolens.sentiment.ml;

import java.util.List;

/**
 * Kural tabanlı fallback ML istemcisi — ML serving yokken (ML_SERVING_URL boş)
 * Go'da {@code ml == nil} durumunun karşılığı. {@link Engine}'de bu istemci hiç
 * enjekte edilmez; arayüz boşluğu kural tabanlı analizi tetikler.
 */
public final class RuleBasedMlClient implements MlClient {

    @Override
    public SentimentPrediction predictSentiment(String text) {
        throw new UnsupportedOperationException("kural tabanlı modda serving çağrısı yapılmaz");
    }

    @Override
    public List<HallucinationFinding> detectHallucinations(List<HallucinationResponse> responses) {
        return List.of();
    }
}