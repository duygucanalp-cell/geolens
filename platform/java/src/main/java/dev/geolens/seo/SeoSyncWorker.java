package dev.geolens.seo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SEO veri senkronizasyon işçisi — Go {@code seo.SyncWorker} portu (FR-B8).
 * <p>Aktif bağlantılar için Search Console ve GA4 verilerini düzenli aralıkla çeker.
 * Go'da {@code go seoWorker.Start(ctx)} ile başlatılır; spike'ta {@code @Scheduled}
 * karşılığıdır. Varsayılan kapalı; {@code seo.sync.enabled=true} ile açılır.
 */
@Component
public class SeoSyncWorker {

    private static final Logger LOG = LoggerFactory.getLogger(SeoSyncWorker.class);

    private final DSLContext dsl;
    private final GoogleOAuthClient oauth;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SeoSyncWorker(DSLContext dsl, GoogleOAuthClient oauth,
                         @Value("${seo.sync.enabled:false}") boolean enabled) {
        this.dsl = dsl;
        this.oauth = oauth;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${seo.sync.interval-ms:3600000}",
               initialDelayString = "${seo.sync.initial-delay-ms:60000}")
    public void runSync() {
        if (!enabled) {
            LOG.debug("seo.sync.enabled=false; senkronizasyon çalıştırılmadı");
            return;
        }
        syncAll();
    }

    /** Tüm aktif bağlantıları senkronize eder — Go {@code syncAll} portu. */
    public void syncAll() {
        syncConnections("search_console");
        syncConnections("ga4");
    }

    /** Belirli bir platformdaki aktif bağlantıları işler — Go {@code syncConnections} portu. */
    void syncConnections(String platform) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, tenant_id, workspace_id, platform, access_token, refresh_token, token_expires_at
                    FROM seo.connections
                    WHERE is_active = true AND platform = ?
                    """, platform);
        } catch (RuntimeException e) {
            LOG.warn("seo sync: bağlantı sorgu hatası platform={}", platform, e);
            return;
        }

        for (Map<String, Object> r : rows) {
            String connId = str(r.get("id"));
            String tenantId = str(r.get("tenant_id"));
            String workspaceId = str(r.get("workspace_id"));
            String accessToken = str(r.get("access_token"));
            String refreshToken = str(r.get("refresh_token"));
            java.sql.Timestamp expiresAt = r.get("token_expires_at") instanceof java.sql.Timestamp t ? t : null;

            // Token süresi dolmuşsa yenile
            if (expiresAt != null && expiresAt.toInstant().isBefore(java.time.Instant.now()) && !refreshToken.isBlank()) {
                try {
                    GoogleOAuthClient.TokenResponse newToken = oauth.refresh(refreshToken);
                    accessToken = newToken.accessToken();
                    dsl.execute("""
                            UPDATE seo.connections
                            SET access_token = ?, refresh_token = COALESCE(NULLIF(?, ''), refresh_token),
                                token_expires_at = ?, updated_at = now()
                            WHERE id = ?
                            """, newToken.accessToken(), newToken.refreshToken() == null ? "" : newToken.refreshToken(),
                            java.sql.Timestamp.from(newToken.expiresAt()), connId);
                } catch (RuntimeException e) {
                    LOG.error("seo sync: token yenileme hatası conn={}", connId, e);
                    continue;
                }
            }

            try {
                if ("search_console".equals(platform)) {
                    syncSearchConsole(tenantId, workspaceId, connId, accessToken);
                } else if ("ga4".equals(platform)) {
                    syncGa4(tenantId, workspaceId, connId, accessToken);
                }
            } catch (RuntimeException e) {
                LOG.warn("seo sync: bağlantı senkronizasyon hatası platform={} conn={}", platform, connId, e);
                continue;
            }

            try {
                dsl.execute("UPDATE seo.connections SET last_synced_at = now() WHERE id = ?", connId);
            } catch (RuntimeException e) {
                LOG.warn("seo sync: last_synced_at güncelleme hatası conn={}", connId, e);
            }
        }
    }

    /** Search Console verilerini senkronize eder — Go {@code syncSearchConsole} portu. */
    void syncSearchConsole(String tenantId, String workspaceId, String connId, String accessToken) {
        List<Map<String, Object>> brands = list("""
                SELECT id, COALESCE(website_url, '') AS website_url FROM config.brands
                WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                """, workspaceId, tenantId);

        for (Map<String, Object> b : brands) {
            String brandId = str(b.get("id"));
            String siteUrl = str(b.get("website_url"));
            if (siteUrl.isBlank()) {
                continue;
            }
            try {
                syncBrandData(tenantId, connId, brandId, siteUrl, accessToken);
            } catch (RuntimeException e) {
                LOG.warn("seo sync: sc data hatası brand={}", brandId, e);
            }
        }
    }

    /** Tek marka için Search Console verisi çeker ve kaydeder — Go {@code syncBrandData} portu. */
    void syncBrandData(String tenantId, String connId, String brandId, String siteUrl, String accessToken) {
        String encodedUrl = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(7);

        String reqBody;
        try {
            reqBody = mapper.writeValueAsString(Map.of(
                    "startDate", start.toString(),
                    "endDate", today.toString(),
                    "dimensions", List.of("query"),
                    "rowLimit", 100));
        } catch (Exception e) {
            throw new SeoException("istek serileştirme: " + e.getMessage(), e);
        }

        String apiUrl = "https://www.googleapis.com/webmasters/v3/sites/" + encodedUrl + "/searchAnalytics/query";
        HttpResult res = postWithRetry(apiUrl, accessToken, reqBody);

        JsonNode root;
        try {
            root = mapper.readTree(res.body());
        } catch (Exception e) {
            throw new SeoException("yanıt ayrıştırma: " + e.getMessage(), e);
        }
        JsonNode rows = root.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return;
        }

        String todayStr = today.toString();
        for (JsonNode row : rows) {
            String query = row.path("keys").isArray() && row.path("keys").size() > 0
                    ? row.path("keys").get(0).asText("") : "";
            try {
                dsl.execute("""
                        INSERT INTO seo.search_console_data
                            (connection_id, tenant_id, brand_id, query, clicks, impressions, ctr, avg_position, measured_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::date)
                        ON CONFLICT (connection_id, brand_id, query, measured_at)
                        DO UPDATE SET clicks = EXCLUDED.clicks, impressions = EXCLUDED.impressions,
                                      ctr = EXCLUDED.ctr, avg_position = EXCLUDED.avg_position
                        """, connId, tenantId, brandId, query,
                        row.path("clicks").asLong(0), row.path("impressions").asLong(0),
                        row.path("ctr").asDouble(0), row.path("avgPosition").asDouble(0), todayStr);
            } catch (RuntimeException e) {
                LOG.warn("seo sync: veri kaydetme hatası query={}", query, e);
            }
        }
    }

    /** GA4 verilerini senkronize eder — Go {@code syncGA4} portu. */
    void syncGa4(String tenantId, String workspaceId, String connId, String accessToken) {
        String propertyId = discoverGa4Property(accessToken);
        if (propertyId == null) {
            return;
        }

        List<Map<String, Object>> brands = list("""
                SELECT id, COALESCE(website_url, '') AS website_url FROM config.brands
                WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                """, workspaceId, tenantId);

        for (Map<String, Object> b : brands) {
            String brandId = str(b.get("id"));
            String siteUrl = str(b.get("website_url"));
            if (siteUrl.isBlank()) {
                continue;
            }
            try {
                syncGa4Data(tenantId, connId, brandId, propertyId, accessToken);
            } catch (RuntimeException e) {
                LOG.warn("seo sync: ga4 data hatası brand={}", brandId, e);
            }
        }
    }

    /** İlk erişilebilir GA4 property'sini keşfeder — Go {@code discoverGA4Properties} portu. */
    String discoverGa4Property(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://analyticsadmin.googleapis.com/v1beta/accountSummaries?pageSize=50"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode summaries = root.path("accountSummaries");
            for (JsonNode acct : summaries) {
                for (JsonNode ps : acct.path("propertySummaries")) {
                    String prop = ps.path("property").asText("");
                    if (!prop.isBlank()) {
                        return prop;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Tek marka için GA4 verisi çeker ve kaydeder — Go {@code syncGA4Data} portu. */
    void syncGa4Data(String tenantId, String connId, String brandId, String propertyId, String accessToken) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(7);

        String reqBody;
        try {
            reqBody = mapper.writeValueAsString(Map.of(
                    "dateRanges", List.of(Map.of("startDate", start.toString(), "endDate", today.toString())),
                    "metrics", List.of(
                            Map.of("name", "screenPageViews"),
                            Map.of("name", "sessions"),
                            Map.of("name", "bounceRate"),
                            Map.of("name", "averageSessionDuration")),
                    "dimensions", List.of()));
        } catch (Exception e) {
            throw new SeoException("istek serileştirme: " + e.getMessage(), e);
        }

        String apiUrl = "https://analyticsdata.googleapis.com/v1beta/" + propertyId + ":runReport";
        HttpResult res = postWithRetry(apiUrl, accessToken, reqBody);

        JsonNode root;
        try {
            root = mapper.readTree(res.body());
        } catch (Exception e) {
            throw new SeoException("yanıt ayrıştırma: " + e.getMessage(), e);
        }
        JsonNode rows = root.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return;
        }

        JsonNode headers = root.path("metricHeaders");
        long pageViews = 0, sessions = 0;
        double bounceRate = 0, avgDuration = 0;
        JsonNode values = rows.get(0).path("metricValues");
        for (int i = 0; i < headers.size() && i < values.size(); i++) {
            String name = headers.get(i).path("name").asText("");
            String value = values.get(i).path("value").asText("");
            switch (name) {
                case "screenPageViews" -> pageViews = SeoApiSupport.parseInt64(value);
                case "sessions" -> sessions = SeoApiSupport.parseInt64(value);
                case "bounceRate" -> bounceRate = SeoApiSupport.parseFloat(value);
                case "averageSessionDuration" -> avgDuration = SeoApiSupport.parseFloat(value);
                default -> {
                }
            }
        }

        String todayStr = today.toString();
        dsl.execute("""
                INSERT INTO seo.ga4_data
                    (connection_id, tenant_id, brand_id, page_views, sessions, bounce_rate, avg_session_duration, measured_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::date)
                ON CONFLICT (connection_id, brand_id, measured_at)
                DO UPDATE SET page_views = EXCLUDED.page_views, sessions = EXCLUDED.sessions,
                              bounce_rate = EXCLUDED.bounce_rate, avg_session_duration = EXCLUDED.avg_session_duration
                """, connId, tenantId, brandId, pageViews, sessions, bounceRate, avgDuration, todayStr);
    }

    /** POST isteği — geçici hatalarda exponential backoff'lu retry (Go {@code doWithRetry} portu). */
    private HttpResult postWithRetry(String apiUrl, String accessToken, String body) {
        HttpResult[] result = new HttpResult[1];
        SeoApiSupport.doWithRetry(4, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                result[0] = new HttpResult(resp.statusCode(), resp.body());
                if (SeoApiSupport.retryableStatus(resp.statusCode())) {
                    return SeoApiSupport.RetryOutcome.retryAgain();
                }
                return SeoApiSupport.RetryOutcome.done();
            } catch (Exception e) {
                return SeoApiSupport.RetryOutcome.retryAgain();
            }
        });
        if (result[0] == null) {
            throw new SeoException("API isteği başarısız (retry tükendi)");
        }
        if (result[0].status() == 401) {
            throw new SeoException("yetkisiz erişim (token expired?)");
        }
        if (result[0].status() != 200) {
            throw new SeoException("api hatası (HTTP " + result[0].status() + ")");
        }
        return result[0];
    }

    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private record HttpResult(int status, String body) {
    }
}
