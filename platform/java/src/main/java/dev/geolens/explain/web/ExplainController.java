package dev.geolens.explain.web;

import dev.geolens.common.ApiError;

import dev.geolens.explain.service.ExplainHistoryResult;
import dev.geolens.explain.service.ExplainResult;
import dev.geolens.explain.service.ExplainService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explainability REST controller'ı — Go {@code explain.handler} portu (R7).
 * <p>Route'lar (go cmd/api): POST /v1/explain/{entityId}, GET /v1/explain/results.
 * <p>Yalnızca HTTP/transport katmanıdır; tüm iş mantığı {@link ExplainService} içindedir.
 */
@RestController
@RequestMapping("/v1/explain")
public class ExplainController {

    private final ExplainService explainService;

    public ExplainController(ExplainService explainService) {
        this.explainService = explainService;
    }

    // ---------- Explain ----------

    @PostMapping("/{entityId}")
    public ResponseEntity<?> explain(@RequestHeader("X-Tenant-ID") String tenantId,
                                     @PathVariable String entityId) {
        ExplainResult r = explainService.explain(tenantId, entityId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("analysis_id", r.analysisId());
        resp.put("entity_id", r.entityId());
        resp.put("entity_name", r.entityName());
        resp.put("entity_type", r.entityType());
        resp.put("risk_class", r.riskClass());
        resp.put("method", "SHAP (approximate)");
        resp.put("base_value", r.baseValue());
        resp.put("prediction", r.prediction());
        resp.put("feature_importance", r.featureImportance());
        resp.put("shap_values", r.shapValues());
        resp.put("interpretation", r.interpretation());
        return ResponseEntity.ok(resp);
    }

    // ---------- ListAnalyses ----------

    @GetMapping("/results")
    public ResponseEntity<?> listAnalyses(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestParam(value = "entity_id", required = false) String entityId,
                                          @RequestParam(value = "limit", required = false) String limit) {
        ExplainHistoryResult r = explainService.listAnalyses(tenantId, entityId, limit);
        return ResponseEntity.ok(Map.of("data", r.data(), "has_more", r.hasMore()));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleService(ServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}