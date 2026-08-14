package dev.geolens.sentiment.persistence;

import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;

import java.util.List;

/** DB bağlantısı olmayan noop — engine mantığını DB'siz test etmek için. */
public final class NoopSentimentDao implements SentimentDao {

    @Override
    public List<RawResponse> loadRawResponses(String tenantId, String brandId) {
        return List.of();
    }

    @Override
    public List<CheckTarget> loadCheckTargets(String tenantId, String brandId) {
        return List.of();
    }

    @Override
    public void saveSentiment(String tenantId, String workspaceId, SentimentResult result) {
        // DB bağlantısı yok — atla.
    }

    @Override
    public void saveHallucination(String tenantId, String workspaceId, HallucinationResult result) {
        // DB bağlantısı yok — atla.
    }

    @Override
    public SentimentResult loadLatest(String brandId, String tenantId) {
        return null;
    }
}