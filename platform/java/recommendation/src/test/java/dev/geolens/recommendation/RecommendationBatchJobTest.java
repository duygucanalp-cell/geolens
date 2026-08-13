package dev.geolens.recommendation.service;

import dev.geolens.recommendation.domain.Category;
import dev.geolens.recommendation.domain.EvidenceLabel;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Batch işinin worker'ın Evaluate() çağrı desenini karşıladığını doğrular. */
class RecommendationBatchJobTest {

    private static final Recommendation SAMPLE = new Recommendation(
            "R1", "T01", "WS01", "B01", Category.VISIBILITY, Severity.HIGH,
            EvidenceLabel.CORRELATIONAL, "başlık", "detay", null, 85, false, false, Instant.now());

    @Test
    void disabledBatchDoesNothing() {
        RecommendationService service = mock(RecommendationService.class);
        RecommendationBatchJob job = new RecommendationBatchJob(service, false, List.of("WS01:T01"));

        job.runBatch();

        verifyNoInteractions(service);
    }

    @Test
    void emptyWorkspaceListDoesNothing() {
        RecommendationService service = mock(RecommendationService.class);
        RecommendationBatchJob job = new RecommendationBatchJob(service, true, List.of());

        job.runBatch();

        verify(service, never()).evaluateAll(null, null);
    }

    @Test
    void enabledBatchEvaluatesConfiguredWorkspaces() {
        RecommendationService service = mock(RecommendationService.class);
        RecommendationBatchJob job =
                new RecommendationBatchJob(service, true, List.of("WS01:T01", "WS02:T01", "bozuk"));

        when(service.evaluateAll("WS01", "T01")).thenReturn(List.of(SAMPLE));
        when(service.evaluateAll("WS02", "T01")).thenReturn(List.of());

        job.runBatch();

        verify(service).evaluateAll("WS01", "T01");
        verify(service).evaluateAll("WS02", "T01");
    }
}