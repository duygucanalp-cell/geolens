package dev.geolens.recommendation.service;

import dev.geolens.recommendation.domain.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Toplu değerlendirme işi — Go worker'ında ölçüm tamamlanınca tetiklenen
 * {@code recSvc.Evaluate(...)} akışının, planlı batch olarak spike karşılığı.
 * <p>Varsayılan olarak kapalıdır; {@code recommendation.batch.enabled=true} ile açılır.
 * Çalışma alanlarını {@code workspaceId:tenantId} çiftleri olarak kabul eder
 * (örn. {@code WS01:T01,WS02:T02}).
 */
@Component
public class RecommendationBatchJob {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationBatchJob.class);

    private final RecommendationService service;
    private final boolean enabled;
    private final List<String> workspaces;

    public RecommendationBatchJob(RecommendationService service,
                                  @Value("${recommendation.batch.enabled:false}") boolean enabled,
                                  @Value("${recommendation.batch.workspaces:}") List<String> workspaces) {
        this.service = service;
        this.enabled = enabled;
        this.workspaces = workspaces;
    }

    @Scheduled(fixedDelayString = "${recommendation.batch.interval-ms:900000}",
               initialDelayString = "${recommendation.batch.initial-delay-ms:60000}")
    public void runBatch() {
        if (!enabled || service == null) {
            LOG.debug("recommendation.batch.enabled=false; batch çalıştırılmadı");
            return;
        }
        for (String spec : workspaces) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            String[] parts = spec.split(":", 2);
            if (parts.length != 2) {
                LOG.warn("batch: geçersiz workspace belirtimi: {}", spec);
                continue;
            }
            String workspaceId = parts[0];
            String tenantId = parts[1];
            try {
                List<Recommendation> recs = service.evaluateAll(workspaceId, tenantId);
                LOG.info("batch evaluateAll tamamlandı workspace={} recCount={}", workspaceId, recs == null ? 0 : recs.size());
            } catch (RuntimeException e) {
                LOG.error("batch evaluateAll hatası workspace={}", workspaceId, e);
            }
        }
    }
}