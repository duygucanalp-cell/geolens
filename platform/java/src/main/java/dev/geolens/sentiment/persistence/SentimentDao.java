package dev.geolens.sentiment.persistence;

import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;

import java.util.List;

/**
 * Sentiment veri erişimi — Go {@code *db.Pool} sorgularının arayüzü.
 * RLS satır düzeyi güvenlik: tüm sorgular tenant_id + workspace_id ile sınırlanır.
 * NoopSentimentDao DB'siz çalıştırır (Go {@code pool == nil} karşılığı değil;
 * JooqSentimentDao gerçek PostgreSQL — ADR-014).
 */
public interface SentimentDao {

    /** measure.raw_responses — markanın son 50 motor yanıtı. */
    List<RawResponse> loadRawResponses(String tenantId, String brandId);

    /** measure.raw_responses + config.brands — marka profili ile hallüsinasyon kontrol hedefleri. */
    List<CheckTarget> loadCheckTargets(String tenantId, String brandId);

    /** analysis.sentiment_scores kaydı. */
    void saveSentiment(String tenantId, String workspaceId, SentimentResult result);

    /** analysis.hallucination_flags kaydı. */
    void saveHallucination(String tenantId, String workspaceId, HallucinationResult result);

    /** son sentiment sonucu (yoksa null). */
    SentimentResult loadLatest(String brandId, String tenantId);
}