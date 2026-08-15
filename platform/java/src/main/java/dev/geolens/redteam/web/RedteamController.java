package dev.geolens.redteam.web;

import dev.geolens.redteam.service.RedteamService;
import dev.geolens.redteam.service.RedteamServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * LLM Red Teaming REST controller'ı — Go {@code redteam.handler} portu (R16).
 * <p>Route'lar (go cmd/api): GET/POST /v1/redteam/cases, DELETE /v1/redteam/cases/{caseId},
 * POST /v1/redteam/runs, GET /v1/redteam/runs, GET /v1/redteam/runs/{runId},
 * POST /v1/redteam/seed-defaults.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; tamamlanan çalıştırmada
 * {@code redteam.run.completed} olayı outbox üzerinden {@code q:governance} stream'ine taşınır (O-6).
 * <p>İş mantığı {@link RedteamService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/redteam")
public class RedteamController {

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "prompt_injection", "jailbreak", "roleplay", "encoding",
            "pii_extraction", "misinformation", "refusal_override", "custom");

    private final RedteamService service;

    public RedteamController(RedteamService service) {
        this.service = service;
    }

    // ---------- ListCases ----------

    @GetMapping("/cases")
    public ResponseEntity<?> listCases(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listCases(tenantId));
    }

    // ---------- CreateCase ----------

    @PostMapping("/cases")
    public ResponseEntity<?> createCase(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody CreateCaseRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.category() == null || !VALID_CATEGORIES.contains(req.category())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz category");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCase(tenantId, req));
    }

    // ---------- DeleteCase ----------

    @DeleteMapping("/cases/{caseId}")
    public ResponseEntity<?> deleteCase(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String caseId) {
        return ResponseEntity.ok(service.deleteCase(tenantId, caseId));
    }

    // ---------- SeedDefaults ----------

    @PostMapping("/seed-defaults")
    public ResponseEntity<?> seedDefaultsHandler(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.seedDefaults(tenantId));
    }

    // ---------- Run ----------

    @PostMapping("/runs")
    public ResponseEntity<?> run(@RequestHeader("X-Tenant-ID") String tenantId,
                                 @RequestBody RunRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.ok(service.run(tenantId, req));
    }

    // ---------- ListRuns ----------

    @GetMapping("/runs")
    public ResponseEntity<?> listRuns(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listRuns(tenantId));
    }

    // ---------- GetRun ----------

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRun(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String runId) {
        return ResponseEntity.ok(service.getRun(tenantId, runId));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(RedteamServiceException.class)
    public ResponseEntity<ApiError> handleRedteamError(RedteamServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
