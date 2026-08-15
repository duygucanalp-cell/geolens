package dev.geolens.measure.service;

import dev.geolens.common.ServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.engine.Registry;
import dev.geolens.measure.ComponentWeights;
import dev.geolens.measure.MeasurementRequest;
import dev.geolens.measure.MeasurementResult;
import dev.geolens.measure.Scoring;
import dev.geolens.measure.web.MeasureJob;
import dev.geolens.measure.web.MeasureRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ölçüm/skor iş mantığı — Go {@code measure.handler} portu.
 * <p>Ölçüm tetikleme, durum sorgulama, skor/trend/rapor ve karşılaştırma
 * uçlarının DB erişimini (DSLContext) ve motor çağrılarını içerir.
 * Controller yalnızca HTTP katmanıdır; bu sınıf DB ve iş mantığını barındırır.
 * <p>Bean adı {@code measureWebService} — Go ölçüm motoru {@code MeasureService}
 * bean'iyle isim çakışmasını önlemek için açıkça belirtilir.
 */
@Service("measureWebService")
public class MeasureService {

    private final DSLContext dsl;
    private final Registry engines;
    private final dev.geolens.measure.MeasureService measureEngine;
    private final ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    public MeasureService(DSLContext dsl, Registry engines,
                          dev.geolens.measure.MeasureService measureEngine) {
        this.dsl = dsl;
        this.engines = engines;
        this.measureEngine = measureEngine;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Go {@code triggerMeasurement} karşılığı — outbox job'larını kuyruğa alır, senkron ölçüm başlatır. */
    public Map<String, Object> triggerMeasurement(String workspaceId, String tenantId, MeasureRequest req) {
        List<String> engineNames = engines.list();
        if (engineNames.isEmpty()) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "kayıtlı motor bulunamadı");
        }

        Map<String, Object> brand;
        try {
            brand = map("""
                    SELECT name, website_url FROM config.brands
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND is_active = true
                    """, req.brandId(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        String brandName = String.valueOf(brand.get("name"));
        String websiteUrl = String.valueOf(brand.get("website_url"));

        String promptText = brandName + " markası hakkında ne biliyorsun? Kaynak göstererek anlat.";
        String panelId = req.panelId();
        if (panelId != null && !panelId.isBlank()) {
            try {
                Map<String, Object> panel = map("""
                        SELECT COALESCE(ps.prompt_text, '') AS prompt_text FROM config.panels p
                        LEFT JOIN config.prompt_sets ps ON ps.id = p.prompt_set_id
                        WHERE p.id = ? AND p.workspace_id = ? AND p.tenant_id = ?
                        """, panelId, workspaceId, tenantId);
                String psPrompt = String.valueOf(panel.get("prompt_text"));
                if (psPrompt != null && !psPrompt.isBlank()) {
                    promptText = psPrompt;
                }
            } catch (RuntimeException ignored) {
                // panel sorgusu başarısızsa varsayılan prompt kullanılır (Go ile aynı)
            }
        }

        String runId = Ulid.generate();
        for (String engineName : engineNames) {
            for (int i = 0; i < 3; i++) {
                String idempotencyKey = String.format("measure:%s:%s:%d:%s", req.brandId(), engineName, i, runId);
                try {
                    enqueueOutbox(tenantId, idempotencyKey, new MeasureJob(
                            req.brandId(), brandName, websiteUrl, panelId, workspaceId, tenantId,
                            engineName, promptText, i));
                } catch (RuntimeException e) {
                    // outbox yazım hatası job atlanır (Go'da loglanıp devam edilir)
                }
            }
        }

        // Demo: asenkron job'ların yanında senkron ölçüm + skor da hesapla (mock engine ile anlık sonuç).
        String finalPrompt = promptText;
        executor.execute(() -> immediateMeasureAndScore(brandName, req.brandId(), websiteUrl,
                panelId, workspaceId, tenantId, finalPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "queued");
        body.put("run_id", runId);
        body.put("brand", brandName);
        body.put("engines", engineNames);
        return body;
    }

    /** Go {@code getMeasurementStatus} karşılığı — ölçüm işlerinin tamamlanma durumu. */
    public Map<String, Object> getMeasurementStatus(String workspaceId, String tenantId, String runId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT COUNT(*) FILTER (WHERE status = 'completed') AS completed,
                           COUNT(*) AS total
                    FROM measure.measurement_jobs
                    WHERE workspace_id = ? AND tenant_id = ? AND created_at > now() - interval '1 hour'
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "durum sorgulanamadı");
        }
        int totalJobs = ((Number) row.get("total")).intValue();
        int completedJobs = ((Number) row.get("completed")).intValue();

        String status = "running";
        if (totalJobs > 0 && completedJobs == totalJobs) {
            status = "completed";
        } else if (totalJobs == 0) {
            status = "pending";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", runId);
        body.put("status", status);
        body.put("total_jobs", totalJobs);
        body.put("completed_jobs", completedJobs);
        return body;
    }

    /** Go {@code listScores} karşılığı — son 50 skoru döner (sorgu hatasında boş liste). */
    public List<Map<String, Object>> listScores(String workspaceId, String tenantId) {
        return queryScores("""
                SELECT s.id, b.name AS brand_name, s.value, s.ci_low, s.ci_high, s.fidelity_label,
                       COALESCE(s.engine_breakdown::text, '{}') AS engine_breakdown, s.freshness_at, b.id AS brand_id
                FROM measure.scores s
                JOIN config.brands b ON b.id = s.brand_id
                WHERE s.workspace_id = ? AND s.tenant_id = ?
                ORDER BY s.freshness_at DESC
                LIMIT 50
                """, workspaceId, tenantId);
    }

    /** Go {@code listTrends} karşılığı — isteğe bağlı marka filtresiyle artan skor listesi. */
    public List<Map<String, Object>> listTrends(String workspaceId, String tenantId, String brandId) {
        String brand = brandId == null ? "" : brandId;
        return queryScores("""
                SELECT s.id, b.name AS brand_name, s.value, s.ci_low, s.ci_high, s.fidelity_label,
                       COALESCE(s.engine_breakdown::text, '{}') AS engine_breakdown, s.freshness_at, b.id AS brand_id
                FROM measure.scores s
                JOIN config.brands b ON b.id = s.brand_id
                WHERE s.workspace_id = ? AND s.tenant_id = ? AND (? = '' OR s.brand_id = ?)
                ORDER BY s.freshness_at ASC
                """, workspaceId, tenantId, brand, brand);
    }

    /** Go {@code listBrandScores} karşılığı — tek markanın skor geçmişi. */
    public Map<String, Object> listBrandScores(String workspaceId, String tenantId, String brandId) {
        String brandName;
        try {
            Map<String, Object> row = map("""
                    SELECT name FROM config.brands WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, brandId, workspaceId, tenantId);
            brandName = String.valueOf(row.get("name"));
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        List<Map<String, Object>> rows = queryScores("""
                SELECT s.id, s.value, s.ci_low, s.ci_high, s.fidelity_label,
                       COALESCE(s.engine_breakdown::text, '{}') AS engine_breakdown, s.freshness_at
                FROM measure.scores s
                WHERE s.brand_id = ? AND s.workspace_id = ? AND s.tenant_id = ?
                ORDER BY s.freshness_at DESC
                """, brandId, workspaceId, tenantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("brand_name", brandName);
        body.put("brand_id", brandId);
        body.put("scores", rows);
        return body;
    }

    /** Go {@code listCitations} karşılığı — brand_id veya job_id ile kaynak atıfları (sorgu hatasında boş). */
    public Map<String, Object> listCitations(String workspaceId, String tenantId, String brandId, String jobId) {
        List<Map<String, Object>> rows;
        try {
            if (jobId != null && !jobId.isBlank()) {
                rows = list("""
                        SELECT r.id, r.job_id, r.engine_name, r.content_text
                        FROM measure.raw_responses r
                        WHERE r.job_id = ? AND r.tenant_id = ?
                        ORDER BY r.created_at
                        """, jobId, tenantId);
            } else {
                rows = list("""
                        SELECT r.id, r.job_id, r.engine_name, r.content_text
                        FROM measure.raw_responses r
                        JOIN measure.measurement_jobs j ON j.id = r.job_id
                        WHERE j.brand_id = ? AND j.workspace_id = ? AND r.tenant_id = ?
                        ORDER BY r.created_at DESC
                        LIMIT 100
                        """, brandId, workspaceId, tenantId);
            }
        } catch (RuntimeException e) {
            return Map.of("citations", List.of());
        }

        List<Map<String, Object>> citations = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            String rawResponseId = String.valueOf(row.get("id"));
            String job = row.get("job_id") == null ? "" : String.valueOf(row.get("job_id"));
            String engineName = String.valueOf(row.get("engine_name"));
            String contentText = row.get("content_text") == null ? null : String.valueOf(row.get("content_text"));
            String content = contentText != null ? (contentText.length() > 200 ? contentText.substring(0, 200) : contentText) : "";
            if (contentText == null) {
                continue;
            }
            for (String u : extractURLs(contentText)) {
                if (seen.contains(u)) {
                    continue;
                }
                seen.add(u);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("raw_response_id", rawResponseId);
                item.put("job_id", job);
                item.put("engine_name", engineName);
                item.put("source_url", u);
                item.put("source_domain", extractDomain(u));
                item.put("content", content);
                citations.add(item);
            }
        }
        return Map.of("citations", citations, "count", citations.size());
    }

    /** Go {@code listBenchmark} karşılığı — son skorlarla marka benchmark tablosu (sorgu hatasında boş). */
    public Map<String, Object> listBenchmark(String workspaceId, String tenantId, String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    WITH latest AS (
                        SELECT DISTINCT ON (b.id)
                            b.id AS brand_id,
                            b.name AS brand_name,
                            s.value AS score_value,
                            s.fidelity_label,
                            s.freshness_at,
                            s.engine_breakdown AS engine_breakdown
                        FROM config.brands b
                        LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.workspace_id = b.workspace_id
                        WHERE b.workspace_id = ? AND b.tenant_id = ? AND b.is_active = true
                        ORDER BY b.id, s.freshness_at DESC
                    )
                    SELECT brand_id, brand_name,
                        COALESCE(score_value, 0) AS score_value,
                        COALESCE(fidelity_label, 'yok') AS fidelity_label,
                        freshness_at,
                        engine_breakdown
                    FROM latest
                    ORDER BY score_value DESC
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return Map.of("benchmark", List.of(), "count", 0);
        }
        List<Map<String, Object>> benchmarks = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("brand_id", row.get("brand_id"));
            item.put("brand_name", row.get("brand_name"));
            item.put("score_value", row.get("score_value") == null ? 0 : ((Number) row.get("score_value")).doubleValue());
            item.put("fidelity_label", row.get("fidelity_label") == null ? "yok" : row.get("fidelity_label"));
            item.put("freshness_at", row.get("freshness_at"));
            item.put("engine_breakdown", parseBreakdown(row.get("engine_breakdown")));
            item.put("is_target", brandId != null && brandId.equals(String.valueOf(row.get("brand_id"))));
            benchmarks.add(item);
        }
        return Map.of("benchmark", benchmarks, "count", benchmarks.size());
    }

    /** Go {@code listRadarComparison} karşılığı — marka motor bazında radar karşılaştırması. */
    public Map<String, Object> listRadarComparison(String workspaceId, String tenantId, String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    WITH latest AS (
                        SELECT DISTINCT ON (b.id)
                            b.id AS brand_id,
                            b.name AS brand_name,
                            s.value AS score_value,
                            s.engine_breakdown AS engine_breakdown,
                            s.freshness_at
                        FROM config.brands b
                        LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.workspace_id = b.workspace_id
                        WHERE b.workspace_id = ? AND b.tenant_id = ? AND b.is_active = true
                        ORDER BY b.id, s.freshness_at DESC
                    )
                    SELECT brand_id, brand_name, score_value, engine_breakdown
                    FROM latest
                    WHERE score_value IS NOT NULL
                    ORDER BY score_value DESC
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return Map.of("radar", List.of(), "engines", List.of(), "count", 0);
        }
        List<Map<String, Object>> radarData = new ArrayList<>();
        java.util.Set<String> allEngines = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> br = new LinkedHashMap<>();
            br.put("brand_id", row.get("brand_id"));
            br.put("brand_name", row.get("brand_name"));
            br.put("total_score", row.get("score_value") == null ? 0 : ((Number) row.get("score_value")).doubleValue());
            List<Map<String, Object>> enginesList = new ArrayList<>();
            Map<String, Object> breakdown = parseBreakdown(row.get("engine_breakdown"));
            if (breakdown != null) {
                for (Map.Entry<String, Object> e : breakdown.entrySet()) {
                    allEngines.add(e.getKey());
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("engine", e.getKey());
                    entry.put("score", e.getValue() instanceof Number n ? n.doubleValue() : 0);
                    enginesList.add(entry);
                }
            }
            br.put("engines", enginesList);
            br.put("is_target", brandId != null && brandId.equals(String.valueOf(row.get("brand_id"))));
            radarData.add(br);
        }
        List<String> engineList = new ArrayList<>(allEngines);
        return Map.of("radar", radarData, "engines", engineList, "count", radarData.size());
    }

    // ---------- yardımcılar ----------

    private List<Map<String, Object>> queryScores(String sql, Object... args) {
        List<Map<String, Object>> rows;
        try {
            rows = list(sql, args);
        } catch (RuntimeException e) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                item.put(e.getKey(), e.getValue());
            }
            if (item.containsKey("engine_breakdown")) {
                item.put("engine_breakdown", parseBreakdown(item.get("engine_breakdown")));
            }
            if (item.containsKey("freshness_at") && item.get("freshness_at") != null) {
                item.put("freshness_at", String.valueOf(item.get("freshness_at")));
            }
            out.add(item);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBreakdown(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        if (text.isBlank() || "{}".equals(text) || "null".equals(text)) {
            return null;
        }
        try {
            return mapper.readValue(text, LinkedHashMap.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void enqueueOutbox(String tenantId, String idempotencyKey, MeasureJob job) {
        String payload = job.toJson();
        dsl.execute("""
                INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                VALUES (?, 'measurement.requested', 'q:measure', ?::jsonb, ?, ?, now())
                """, Ulid.generate(), payload, tenantId, idempotencyKey);
    }

    private void immediateMeasureAndScore(String brandName, String brandId, String websiteUrl,
                                          String panelId, String workspaceId, String tenantId, String promptText) {
        try {
            MeasurementResult result = measureEngine.measure(new MeasurementRequest(
                    brandId, brandName, websiteUrl, promptText, null, tenantId, workspaceId, panelId, null));
            measureEngine.calculateScore(panelId, List.of(result), ComponentWeights.EMPTY);
        } catch (RuntimeException e) {
            // anlık ölçüm başarısız — asenkron pipeline işlemeye devam eder (Go ile aynı)
        }
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }

    /** Metin içindeki http(s) URL'lerini ayıklar — alıntı işleme yardımcısı. */
    public static List<String> extractURLs(String text) {
        List<String> urls = new ArrayList<>();
        String remaining = text;
        while (true) {
            int start = remaining.indexOf("http");
            if (start == -1) {
                break;
            }
            int end = start;
            while (end < remaining.length()) {
                char ch = remaining.charAt(end);
                if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r'
                        || ch == ')' || ch == ']' || ch == '}' || ch == '>' || ch == '"' || ch == '\'') {
                    break;
                }
                end++;
            }
            String url = remaining.substring(start, end);
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            }
            remaining = remaining.substring(end);
        }
        return urls;
    }

    private static String extractDomain(String url) {
        return Scoring.extractDomain(url);
    }
}
