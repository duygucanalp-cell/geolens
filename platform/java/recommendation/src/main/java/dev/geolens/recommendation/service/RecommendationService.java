package dev.geolens.recommendation.service;

import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Brand;
import dev.geolens.recommendation.domain.EvaluationContext;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.domain.ScoreSnapshot;
import dev.geolens.recommendation.engine.RecommendationEngine;
import dev.geolens.recommendation.ng10.NG10Filter;
import dev.geolens.recommendation.persistence.NoopRecommendationDao;
import dev.geolens.recommendation.persistence.RecommendationDao;
import dev.geolens.recommendation.rules.DefaultRules;
import dev.geolens.recommendation.rules.SectorRules;
import dev.geolens.recommendation.util.Ulid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Öneri servisi — Go {@code service} portu (birebir davranış).
 * <p>Sonuçlar NG10 filtresinden geçirilir: sadece NG (nötr) ve P (pozitif) öneriler döner.
 */
public final class RecommendationService {

    private final List<Rule> rules;
    private final Map<String, List<Rule>> sectorRules;
    private final RecommendationDao dao;
    private final NG10Filter ng10;

    public RecommendationService(RecommendationDao dao) {
        this.dao = dao;
        this.rules = new ArrayList<>(DefaultRules.RULES);
        this.sectorRules = new LinkedHashMap<>(SectorRules.RULES);
        this.ng10 = new NG10Filter();
    }

    public static RecommendationService withDao(RecommendationDao dao) {
        return new RecommendationService(dao);
    }

    public static RecommendationService withoutDatabase() {
        return new RecommendationService(new NoopRecommendationDao());
    }

    public List<Recommendation> evaluate(String brandId, String workspaceId, String tenantId) {
        if (brandId == null || brandId.isBlank()) {
            return evaluateAll(workspaceId, tenantId);
        }

        ScoreSnapshot snapshot = dao.loadScore(brandId, workspaceId, tenantId);
        AuditSnapshot audit = dao.loadAudit(brandId, tenantId);
        EvaluationContext ctx = new EvaluationContext(brandId, "", workspaceId, tenantId, snapshot, audit);

        List<Recommendation> recs = RecommendationEngine.evaluateBrand(ctx, rules);
        saveAll(recs);
        return ng10.filterRecommendations(recs);
    }

    public List<Recommendation> evaluateAll(String workspaceId, String tenantId) {
        List<Recommendation> results = new ArrayList<>();
        for (Brand brand : dao.listActiveBrands(workspaceId, tenantId)) {
            ScoreSnapshot snapshot = dao.loadScore(brand.id(), workspaceId, tenantId);
            AuditSnapshot audit = dao.loadAudit(brand.id(), tenantId);
            EvaluationContext ctx = new EvaluationContext(brand.id(), brand.name(), workspaceId, tenantId, snapshot, audit);
            results.addAll(RecommendationEngine.evaluateBrand(ctx, rules));
        }
        saveAll(results);
        return ng10.filterRecommendations(results);
    }

    public List<Rule> getRules() {
        List<Rule> all = new ArrayList<>(rules);
        for (List<Rule> pkg : sectorRules.values()) {
            all.addAll(pkg);
        }
        return all;
    }

    public List<Rule> getRulesBySector(String sector) {
        return sectorRules.getOrDefault(sector, List.of());
    }

    public void registerCustomRule(Rule rule) {
        String id = (rule.id() == null || rule.id().isBlank())
                ? "rule-custom-" + Ulid.generate()
                : rule.id();
        Rule registered = new Rule(id, rule.name(), rule.description(), rule.category(), rule.severity(),
                rule.evidence(), rule.conditions(), rule.title(), rule.detail(), rule.actionUrl(), true,
                rule.claimLang());
        rules.add(registered);
    }

    public void markApplied(String id, String tenantId, String workspaceId) {
        dao.markApplied(id, tenantId, workspaceId);
    }

    public void markDismissed(String id, String tenantId, String workspaceId) {
        dao.markDismissed(id, tenantId, workspaceId);
    }

    private void saveAll(List<Recommendation> recs) {
        for (Recommendation rec : recs) {
            dao.save(rec);
        }
    }
}