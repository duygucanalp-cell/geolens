package dev.geolens.recommendation.persistence;

import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Brand;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.ScoreSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * Kalıcılık arayüzü. Go'daki {@code *db.Pool} erişimlerinin karşılığı.
 * <p>Tenant/workspace kısıtlamaları SQL içinde zorunludur (RLS uyumu: WHERE tenant_id/workspace_id).
 */
public interface RecommendationDao {

    ScoreSnapshot loadScore(String brandId, String workspaceId, String tenantId);

    AuditSnapshot loadAudit(String brandId, String tenantId);

    List<Brand> listActiveBrands(String workspaceId, String tenantId);

    void save(Recommendation recommendation);

    void markApplied(String id, String tenantId, String workspaceId);

    void markDismissed(String id, String tenantId, String workspaceId);

    /** Uygulanmış öneri kaydı; yoksa {@code null}. */
    AppliedRecommendation loadApplied(String id, String workspaceId, String tenantId);

    /**
     * Belirli bir ana göre skor. {@code before=true} → {@code freshness_at <= at} (en yeni);
     * {@code before=false} → {@code freshness_at > at} (en eski). Kayıt yoksa {@code null} döner.
     */
    ScoreAt loadScoreAt(String brandId, String workspaceId, String tenantId, Instant at, boolean before);
}