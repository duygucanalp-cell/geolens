package dev.geolens.drift.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.drift.Alert;
import dev.geolens.drift.DriftAnalyzer;
import dev.geolens.drift.Observation;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drift tespiti REST controller'ı — Go {@code drift.handler} portu (R17).
 * <p>Route'lar (go cmd/api): POST /v1/drift/record, GET /v1/drift/observations,
 * GET /v1/drift/entities, GET /v1/drift/analysis, GET /v1/drift/alerts.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; eşik aşımında {@code drift.alert.triggered}
 * olayı outbox üzerinden {@code q:governance} stream'ine taşınır (O-6, deterministik idempotency anahtarıyla).
 */
@RestController
@RequestMapping("/v1/drift")
public class DriftController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public DriftController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- Record ----------

    @PostMapping("/record")
    public ResponseEntity<?> record(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody RecordObservationRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.entityId() == null || req.entityId().isBlank()
                || req.metric() == null || req.metric().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "entity_id ve metric zorunludur");
        }

        Map<String, Object> row;
        try {
            row = map("""
                    INSERT INTO drift.observations (tenant_id, entity_id, entity_name, metric, value, window_start)
                    VALUES (?, ?, ?, ?, ?, COALESCE(NULLIF(?, '')::timestamptz, now()))
                    RETURNING id, tenant_id, entity_id, entity_name, metric, value, window_start, created_at
                    """, tenantId, req.entityId(), nz(req.entityName()), req.metric(), req.value(), nz(req.windowStart()));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "gözlem kaydedilemedi");
        }

        Observation obs = toObservation(row);
        return ResponseEntity.status(HttpStatus.CREATED).body(obs);
    }

    // ---------- ListObservations ----------

    @GetMapping("/observations")
    public ResponseEntity<?> listObservations(@RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestParam(value = "entity_id", required = false) String entityId,
                                              @RequestParam(value = "metric", required = false) String metric,
                                              @RequestParam(value = "limit", required = false) String limit) {
        String eid = entityId == null ? "" : entityId;
        String met = metric == null ? "" : metric;
        if (eid.isEmpty() || met.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "entity_id ve metric parametreleri zorunludur");
        }

        List<Map<String, Object>> rows;
        try {
            if (limit != null && !limit.isBlank()) {
                rows = list("""
                        SELECT id, tenant_id, entity_id, entity_name, metric, value, window_start, created_at
                        FROM drift.observations
                        WHERE tenant_id = ? AND entity_id = ? AND metric = ?
                        ORDER BY window_start DESC
                        LIMIT ?
                        """, tenantId, eid, met, limit);
            } else {
                rows = list("""
                        SELECT id, tenant_id, entity_id, entity_name, metric, value, window_start, created_at
                        FROM drift.observations
                        WHERE tenant_id = ? AND entity_id = ? AND metric = ?
                        ORDER BY window_start DESC
                        """, tenantId, eid, met);
            }
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("observations", List.of()));
        }

        List<Observation> observations = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            observations.add(toObservation(r));
        }
        return ResponseEntity.ok(Map.of(
                "observations", observations,
                "total", observations.size()));
    }

    // ---------- ListEntities ----------

    @GetMapping("/entities")
    public ResponseEntity<?> listEntities(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT entity_id, entity_name, metric,
                           COUNT(*)::int AS observation_count,
                           AVG(value)::float8 AS mean_value,
                           MAX(window_start) AS last_observed
                    FROM drift.observations
                    WHERE tenant_id = ?
                    GROUP BY entity_id, entity_name, metric
                    ORDER BY last_observed DESC
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("entities", List.of()));
        }

        List<Map<String, Object>> entities = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entity_id", str(r.get("entity_id")));
            item.put("entity_name", str(r.get("entity_name")));
            item.put("metric", str(r.get("metric")));
            item.put("observation_count", r.get("observation_count") == null ? 0 : ((Number) r.get("observation_count")).intValue());
            item.put("mean_value", r.get("mean_value") == null ? 0 : ((Number) r.get("mean_value")).doubleValue());
            item.put("last_observed", str(r.get("last_observed")));
            entities.add(item);
        }
        return ResponseEntity.ok(Map.of("entities", entities));
    }

    // ---------- Analyze ----------

    @GetMapping("/analysis")
    public ResponseEntity<?> analyze(@RequestHeader("X-Tenant-ID") String tenantId,
                                     @RequestParam(value = "entity_id", required = false) String entityId,
                                     @RequestParam(value = "metric", required = false) String metric,
                                     @RequestParam(value = "threshold", required = false) String threshold) {
        String eid = entityId == null ? "" : entityId;
        String met = metric == null ? "" : metric;
        if (eid.isEmpty() || met.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "entity_id ve metric parametreleri zorunludur");
        }

        // Tüm gözlemleri zaman sırasıyla getir (ASC — referans en eski, güncel en yeni)
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT value FROM drift.observations
                    WHERE tenant_id = ? AND entity_id = ? AND metric = ?
                    ORDER BY window_start ASC
                    """, tenantId, eid, met);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "gözlem sorgu hatası");
        }

        List<Double> values = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object v = r.get("value");
            if (v != null) {
                values.add(((Number) v).doubleValue());
            }
        }

        if (values.size() < 4) {
            return ResponseEntity.ok(Map.of(
                    "entity_id", eid,
                    "metric", met,
                    "drift_score", 0,
                    "severity", "insufficient_data",
                    "reference_mean", 0,
                    "current_mean", 0,
                    "delta", 0,
                    "detail", "drift analizi için en az 4 gözlem gerekir"));
        }

        // Referans: ilk yarı; Güncel: son 10 gözlem (yeterli yoksa son çeyrek)
        List<Double> refValues = values.subList(0, values.size() / 2);
        List<Double> curValues = values.subList(values.size() / 2, values.size());
        if (values.size() > 10) {
            curValues = values.subList(values.size() - 10, values.size());
        }

        DriftAnalyzer.DriftResult res = DriftAnalyzer.computeDriftScore(refValues, curValues);
        double score = res.score();
        double delta = res.delta();
        double refMean = res.refMean();
        double curMean = res.curMean();

        String severity = DriftAnalyzer.severityFor(score);

        // Varsayılan eşik 40; aşarsa uyarı kaydet
        double thresh = 40.0;
        if (threshold != null && !threshold.isBlank()) {
            try {
                thresh = Double.parseDouble(threshold);
            } catch (NumberFormatException ignored) {
                // geçersiz eşik — varsayılan 40 kullanılır (Go ile aynı)
            }
        }
        if (score >= thresh) {
            try {
                dsl.execute("""
                        INSERT INTO drift.alerts (tenant_id, entity_id, entity_name, metric, drift_score, severity,
                                                  reference_mean, current_mean, delta, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, tenantId, eid, "", met, score, severity, refMean, curMean, delta,
                        "güncel pencere referans ortalamasından sapıyor");
                // O-6: DriftAlertTriggered olayını outbox üzerinden taşı; içerik türevli
                // deterministik anahtar yinelenen olay üretmez (unique index).
                String idemKey = DriftAnalyzer.driftIdempotencyKey(eid, met, score, delta);
                enqueueAlertTriggered(tenantId, eid, met, score, severity, delta, idemKey);
            } catch (RuntimeException e) {
                // uyarı — Go: slog.Debug("drift uyarı kayıt hatası"); akış devam eder
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entity_id", eid);
        body.put("metric", met);
        body.put("drift_score", score);
        body.put("severity", severity);
        body.put("reference_mean", refMean);
        body.put("current_mean", curMean);
        body.put("delta", delta);
        body.put("detail", "referans ve güncel pencereler arasındaki normalleştirilmiş ortalama sapması");
        return ResponseEntity.ok(body);
    }

    // ---------- ListAlerts ----------

    @GetMapping("/alerts")
    public ResponseEntity<?> listAlerts(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, tenant_id, entity_id, entity_name, metric, drift_score, severity,
                           reference_mean, current_mean, delta, detail, created_at
                    FROM drift.alerts WHERE tenant_id = ? ORDER BY created_at DESC
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("alerts", List.of()));
        }

        List<Alert> alerts = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            alerts.add(new Alert(
                    str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_id")), str(r.get("entity_name")),
                    str(r.get("metric")), num(r.get("drift_score")), str(r.get("severity")),
                    num(r.get("reference_mean")), num(r.get("current_mean")), num(r.get("delta")),
                    str(r.get("detail")), str(r.get("created_at"))));
        }
        return ResponseEntity.ok(Map.of(
                "alerts", alerts,
                "total", alerts.size()));
    }

    // ---------- yardımcılar ----------

    private void enqueueAlertTriggered(String tenantId, String entityId, String metric,
                                       double score, String severity, double delta, String idemKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entity_id", entityId);
        payload.put("metric", metric);
        payload.put("drift_score", score);
        payload.put("severity", severity);
        payload.put("delta", delta);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        dsl.execute("""
                INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                VALUES (?, 'drift.alert.triggered', 'q:governance', ?::jsonb, ?, ?, now())
                """, Ulid.generate(), json, tenantId, idemKey);
    }

    private static Observation toObservation(Map<String, Object> r) {
        return new Observation(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_id")), str(r.get("entity_name")),
                str(r.get("metric")), num(r.get("value")), str(r.get("window_start")), str(r.get("created_at")));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
