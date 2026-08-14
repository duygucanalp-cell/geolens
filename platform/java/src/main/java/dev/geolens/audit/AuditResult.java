package dev.geolens.audit;

import java.time.Instant;
import java.util.List;

/**
 * Bir site denetiminin eksiksiz sonucu — Go {@code audit.AuditResult} portu.
 * {@link #withContext(String, String)} ile workspace/tenant context'i sonradan eklenir
 * (Go handler'ın RLS öncesi zenginleştirmesi).
 */
public record AuditResult(
        String id,
        String brandId,
        String brandName,
        String websiteUrl,
        String workspaceId,
        String tenantId,
        RobotsTxtCheck robotsTxtCheck,
        BotAccessCheck botAccessCheck,
        SSRCheck ssrCheck,
        SSRFCheck ssrfCheck,
        double overallScore,
        List<Issue> issues,
        Instant createdAt) {

    public AuditResult {
        if (issues == null) {
            issues = List.of();
        }
    }

    public AuditResult withContext(String workspaceId, String tenantId) {
        return new AuditResult(id, brandId, brandName, websiteUrl, workspaceId, tenantId,
                robotsTxtCheck, botAccessCheck, ssrCheck, ssrfCheck, overallScore, issues, createdAt);
    }
}