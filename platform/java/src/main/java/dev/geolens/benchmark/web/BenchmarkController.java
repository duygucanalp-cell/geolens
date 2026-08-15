package dev.geolens.benchmark.web;

import dev.geolens.benchmark.service.BenchmarkService;
import dev.geolens.benchmark.service.BenchmarkServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Model benchmark REST controller'ı — Go {@code benchmark.handler} portu (R10).
 * <p>Route'lar (go cmd/api): POST /v1/benchmarks/models, GET /v1/benchmarks/models,
 * GET /v1/benchmarks/compare.
 * <p>İş mantığı {@link BenchmarkService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/benchmarks")
public class BenchmarkController {

    private final BenchmarkService service;

    public BenchmarkController(BenchmarkService service) {
        this.service = service;
    }

    // ---------- RunBenchmark ----------

    @PostMapping("/models")
    public ResponseEntity<?> runBenchmark(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody RunBenchmarkRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.modelName() == null || req.modelName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "model_name gerekli");
        }
        if (req.engineName() == null || req.engineName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "engine_name gerekli");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.runBenchmark(tenantId, req));
    }

    // ---------- ListBenchmarks ----------

    @GetMapping("/models")
    public ResponseEntity<?> listBenchmarks(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
                                            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
                                            @RequestParam(value = "engine", required = false) String engine,
                                            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.ok(service.listBenchmarks(tenantId, limit, offset, engine, category));
    }

    // ---------- CompareModels ----------

    @GetMapping("/compare")
    public ResponseEntity<?> compareModels(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "engines", required = false) String enginesRaw) {
        if (enginesRaw == null || enginesRaw.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "engines parametresi gerekli (virgülle ayırın)");
        }
        return ResponseEntity.ok(service.compareModels(tenantId, enginesRaw));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(BenchmarkServiceException.class)
    public ResponseEntity<ApiError> handleService(BenchmarkServiceException ex) {
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
