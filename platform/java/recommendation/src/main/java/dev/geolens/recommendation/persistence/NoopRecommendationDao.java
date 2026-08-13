package dev.geolens.recommendation.persistence;

import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Brand;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.ScoreSnapshot;

import java.util.List;

/**
 * DB'siz çalışan noop implementasyon — Go'da {@code svc.pool == nil} durumunun karşılığı.
 * Kayıtlar için değerlendirme sonuçları hesaplanır ama DB'ye yazılmaz; mark işlemleri desteklenmez.
 */
public final class NoopRecommendationDao implements RecommendationDao {

    @Override
    public ScoreSnapshot loadScore(String brandId, String workspaceId, String tenantId) {
        return ScoreSnapshot.empty();
    }

    @Override
    public AuditSnapshot loadAudit(String brandId, String tenantId) {
        return AuditSnapshot.empty();
    }

    @Override
    public List<Brand> listActiveBrands(String workspaceId, String tenantId) {
        return List.of();
    }

    @Override
    public void save(Recommendation recommendation) {
        // DB bağlantısı yok — atla.
    }

    @Override
    public void markApplied(String id, String tenantId, String workspaceId) {
        throw new UnsupportedOperationException("DB bağlantısı yok");
    }

    @Override
    public void markDismissed(String id, String tenantId, String workspaceId) {
        throw new UnsupportedOperationException("DB bağlantısı yok");
    }
}