package dev.geolens.recommendation.domain;

/** Bir markanın koşul değerlendirmesinde ihtiyaç duyulan tüm veri (Go: {@code EvaluationContext}). */
public record EvaluationContext(
        String brandId,
        String brandName,
        String workspaceId,
        String tenantId,
        ScoreSnapshot score,
        AuditSnapshot audit) {

    public EvaluationContext {
        brandName = brandName == null ? "" : brandName;
    }
}