package dev.geolens.recommendation.web;

import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.persistence.AppliedRecommendation;
import dev.geolens.recommendation.persistence.RecommendationDao;
import dev.geolens.recommendation.persistence.ScoreAt;
import dev.geolens.recommendation.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Öneri REST controller'ı — Go handler'ının route/hata davranış birebir karşılığı.
 * <p>Go'daki {@code httpmw.GetTenantID/GetWorkspaceID} (JWT + URL) yerine bu spike'ta
 * tenant, gerçek geçit/middleware tarafından atılan {@code X-Tenant-ID} başlığından gelir.
 * <p>Route'lar (go cmd/api): GET list, GET /{recId}/impact, GET /rules, GET /rules/{sector},
 * POST /{recId}/apply, POST /{recId}/dismiss.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/recommendations")
public class RecommendationController {

    private final RecommendationService service;
    private final RecommendationDao dao;

    public RecommendationController(RecommendationService service, RecommendationDao dao) {
        this.service = service;
        this.dao = dao;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String workspaceId,
                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                  @RequestParam(value = "brand_id", required = false) String brandId) {
        try {
            List<Recommendation> recs = service.evaluate(brandId, workspaceId, tenantId);
            return ResponseEntity.ok(recs == null ? List.of() : recs);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "öneriler alınamadı");
        }
    }

    @PostMapping("/{recId}/apply")
    public ResponseEntity<?> markApplied(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String recId) {
        if (recId == null || recId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "recommendation_id gerekli");
        }
        try {
            service.markApplied(recId, tenantId, workspaceId);
            return ResponseEntity.ok(Map.of("status", "applied"));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "öneri uygulanamadı");
        }
    }

    @PostMapping("/{recId}/dismiss")
    public ResponseEntity<?> markDismissed(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @PathVariable String recId) {
        if (recId == null || recId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "recommendation_id gerekli");
        }
        try {
            service.markDismissed(recId, tenantId, workspaceId);
            return ResponseEntity.ok(Map.of("status", "dismissed"));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "öneri gizlenemedi");
        }
    }

    @GetMapping("/{recId}/impact")
    public ResponseEntity<?> impact(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String recId) {
        if (recId == null || recId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "recommendation_id gerekli");
        }
        AppliedRecommendation app = dao.loadApplied(recId, workspaceId, tenantId);
        if (app == null) {
            return error(HttpStatus.NOT_FOUND, "öneri bulunamadı veya henüz uygulanmamış");
        }
        if (app.appliedAt() == null) {
            return error(HttpStatus.CONFLICT, "öneri uygulanma tarihi bulunamadı");
        }
        ScoreAt before = dao.loadScoreAt(app.brandId(), workspaceId, tenantId, app.appliedAt(), true);
        ScoreAt after = dao.loadScoreAt(app.brandId(), workspaceId, tenantId, app.appliedAt(), false);

        double beforeVal = before == null ? 0 : before.value();
        double afterVal = after == null ? 0 : after.value();
        double change = (afterVal != 0 || beforeVal != 0) ? afterVal - beforeVal : 0;

        ScoreAtResponse beforeDto = dto(before);
        ScoreAtResponse afterDto = dto(after);
        return ResponseEntity.ok(new ImpactResponse(
                recId, app.brandId(), app.appliedAt().toString(), beforeDto, afterDto, change));
    }

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> rules(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Rule> rs = service.getRules();
        List<Rule> safe = rs == null ? List.of() : rs;
        return ResponseEntity.ok(Map.of("rules", safe, "count", safe.size()));
    }

    @GetMapping("/rules/{sector}")
    public ResponseEntity<?> rulesBySector(@PathVariable String sector) {
        if (sector == null || sector.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "sektör gerekli");
        }
        List<Rule> rs = service.getRulesBySector(sector);
        List<Rule> safe = rs == null ? List.of() : rs;
        return ResponseEntity.ok(Map.of("sector", sector, "rules", safe, "count", safe.size()));
    }

    private static ScoreAtResponse dto(ScoreAt at) {
        if (at == null) {
            return new ScoreAtResponse(0, "", "");
        }
        return new ScoreAtResponse(at.value(), at.fidelity() == null ? "" : at.fidelity(), at.measuredAt().toString());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}