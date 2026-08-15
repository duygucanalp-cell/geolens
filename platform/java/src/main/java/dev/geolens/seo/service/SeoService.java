package dev.geolens.seo.service;

import dev.geolens.seo.GoogleOAuthClient;
import dev.geolens.seo.SeoStateStore;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SEO platform entegrasyonu iş mantığı — Go {@code seo.handler} portu (FR-B8).
 * <p>Connections yönetimi, Google OAuth akışı ve search-console/GA4 veri sorgularını
 * yapar. Controller yalnızca HTTP katmanıdır; bu sınıf DB (DSLContext), OAuth ve state
 * store erişimini içerir.
 * <p>Callback Google OAuth redirect'i olduğundan JWT dışında çalışır — tenant/workspace
 * state token'dan çözülür; diğer işlemler {@code X-Tenant-ID} başlığından gelir.
 */
@Service
public class SeoService {

    private final DSLContext dsl;
    private final GoogleOAuthClient oauth;
    private final SeoStateStore stateStore;
    private final String baseUrl;
    private final String clientId;
    private final SecureRandom rng = new SecureRandom();

    public SeoService(DSLContext dsl, GoogleOAuthClient oauth, SeoStateStore stateStore,
                      @Value("${BASE_URL:http://localhost:8080}") String baseUrl,
                      @Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId) {
        this.dsl = dsl;
        this.oauth = oauth;
        this.stateStore = stateStore;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
    }

    /** Go {@code ListConnections} karşılığı — sorgu hatasında boş liste döner. */
    public List<Map<String, Object>> listConnections(String workspaceId, String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, platform, email, is_active, last_synced_at, created_at
                    FROM seo.connections
                    WHERE tenant_id = ? AND workspace_id = ?
                    ORDER BY platform
                    """, tenantId, workspaceId);
        } catch (RuntimeException e) {
            return List.of();
        }

        List<Map<String, Object>> conns = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("platform", str(r.get("platform")));
            item.put("email", str(r.get("email")));
            item.put("is_active", r.get("is_active") != null && Boolean.TRUE.equals(r.get("is_active")));
            if (r.get("last_synced_at") != null) {
                item.put("last_synced_at", ts(r.get("last_synced_at")));
            }
            item.put("created_at", ts(r.get("created_at")));
            conns.add(item);
        }
        return conns;
    }

    /** Go {@code GetAuthURL} karşılığı — doğrulama hatasında {@link SeoServiceException} atar. */
    public Map<String, Object> getAuthUrl(String workspaceId, String tenantId, String platform) {
        String plat = platform == null ? "" : platform;
        if (!"search_console".equals(plat) && !"ga4".equals(plat)) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "platform search_console veya ga4 olmalıdır");
        }
        if (!oauth.configured()) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "Google OAuth yapılandırılmamış");
        }

        byte[] stateBytes = new byte[16];
        rng.nextBytes(stateBytes);
        String stateToken = HexFormat.of().formatHex(stateBytes);

        String stateValue = tenantId + "|" + workspaceId + "|" + plat;
        stateStore.put("seo:state:" + stateToken, stateValue);

        String scopes = GoogleOAuthClient.SCOPE_SEARCH_CONSOLE;
        if ("ga4".equals(plat)) {
            scopes = GoogleOAuthClient.SCOPE_GA4;
        }

        String redirectUri = baseUrl + "/v1/workspaces/" + workspaceId + "/seo/callback";
        String authUrl = GoogleOAuthClient.buildAuthUrl(clientId, redirectUri, scopes, stateToken);

        return Map.of(
                "auth_url", authUrl,
                "state_token", stateToken);
    }

    /** Go {@code HandleCallback} karşılığı — başarılı bağlantı sonrası redirect URL'sini döner. */
    public String handleCallback(String workspaceId, String code, String state) {
        String c = code == null ? "" : code;
        String s = state == null ? "" : state;
        if (c.isEmpty() || s.isEmpty()) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "code ve state parametreleri gerekli");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "workspace ID gerekli");
        }

        // State token'ı doğrula — içinden tenantID, workspaceID ve platform'u çöz
        String stateValue = stateStore.get("seo:state:" + s);
        if (stateValue == null) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "geçersiz state token");
        }
        String[] parts = stateValue.split("\\|", 3);
        if (parts.length != 3) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "geçersiz state");
        }
        String tenantId = parts[0];
        String wsId = parts[1];
        String platform = parts[2];

        if (!wsId.equals(workspaceId)) {
            throw new SeoServiceException(HttpStatus.BAD_REQUEST, "workspace eşleşmez");
        }

        // state token'ını temizle
        stateStore.remove("seo:state:" + s);

        // Authorization code'u token ile değiştir
        String redirectUri = baseUrl + "/v1/workspaces/" + workspaceId + "/seo/callback";
        GoogleOAuthClient.TokenResponse token;
        try {
            token = oauth.exchangeCode(c, redirectUri);
        } catch (RuntimeException e) {
            throw new SeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "token alınamadı");
        }

        // Token'ı veritabanına kaydet
        try {
            dsl.execute("""
                    INSERT INTO seo.connections (id, tenant_id, workspace_id, platform, email, access_token,
                                                 refresh_token, token_expires_at)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, platform) DO UPDATE SET
                        email = EXCLUDED.email,
                        access_token = EXCLUDED.access_token,
                        refresh_token = EXCLUDED.refresh_token,
                        token_expires_at = EXCLUDED.token_expires_at,
                        is_active = true,
                        updated_at = now()
                    """, tenantId, workspaceId, platform, token.email(),
                    token.accessToken(), token.refreshToken() == null ? "" : token.refreshToken(),
                    java.sql.Timestamp.from(token.expiresAt()));
        } catch (RuntimeException e) {
            throw new SeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "bağlantı kaydedilemedi");
        }

        // Başarılı bağlantı — frontend'i yönlendir (307 Temporary Redirect)
        return baseUrl + "/?seo=connected&platform=" + platform;
    }

    /** Go {@code Disconnect} karşılığı — bağlantıyı kaldırır, durumu döner. */
    public Map<String, Object> disconnect(String workspaceId, String tenantId, String platform) {
        try {
            dsl.execute("""
                    DELETE FROM seo.connections
                    WHERE tenant_id = ? AND workspace_id = ? AND platform = ?
                    """, tenantId, workspaceId, platform);
        } catch (RuntimeException e) {
            throw new SeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "bağlantı kaldırılamadı");
        }
        return Map.of(
                "status", "disconnected",
                "platform", platform);
    }

    /** Go {@code GetSearchConsoleData} karşılığı — sorgu hatasında boş liste döner. */
    public List<Map<String, Object>> getSearchConsoleData(String tenantId, String brandId) {
        String b = brandId == null ? "" : brandId;
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT scd.query, scd.clicks, scd.impressions, scd.ctr, scd.avg_position, scd.measured_at
                    FROM seo.search_console_data scd
                    WHERE scd.tenant_id = ? AND (? = '' OR scd.brand_id = ?)
                    ORDER BY scd.measured_at DESC, scd.clicks DESC
                    LIMIT 100
                    """, tenantId, b, b);
        } catch (RuntimeException e) {
            return List.of();
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("query", str(r.get("query")));
            item.put("clicks", r.get("clicks") == null ? 0 : ((Number) r.get("clicks")).longValue());
            item.put("impressions", r.get("impressions") == null ? 0 : ((Number) r.get("impressions")).longValue());
            item.put("ctr", r.get("ctr") == null ? 0 : ((Number) r.get("ctr")).doubleValue());
            item.put("avg_position", r.get("avg_position") == null ? 0 : ((Number) r.get("avg_position")).doubleValue());
            item.put("measured_at", dateOnly(r.get("measured_at")));
            data.add(item);
        }
        return data;
    }

    /** Go {@code GetGA4Data} karşılığı — sorgu hatasında boş liste döner. */
    public List<Map<String, Object>> getGa4Data(String tenantId, String brandId) {
        String b = brandId == null ? "" : brandId;
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT gd.page_views, gd.sessions, gd.bounce_rate, gd.avg_session_duration, gd.measured_at
                    FROM seo.ga4_data gd
                    WHERE gd.tenant_id = ? AND (? = '' OR gd.brand_id = ?)
                    ORDER BY gd.measured_at DESC
                    LIMIT 100
                    """, tenantId, b, b);
        } catch (RuntimeException e) {
            return List.of();
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("page_views", r.get("page_views") == null ? 0 : ((Number) r.get("page_views")).longValue());
            item.put("sessions", r.get("sessions") == null ? 0 : ((Number) r.get("sessions")).longValue());
            item.put("bounce_rate", r.get("bounce_rate") == null ? 0 : ((Number) r.get("bounce_rate")).doubleValue());
            item.put("avg_session_duration", r.get("avg_session_duration") == null ? 0 : ((Number) r.get("avg_session_duration")).doubleValue());
            item.put("measured_at", dateOnly(r.get("measured_at")));
            data.add(item);
        }
        return data;
    }

    // ---------- yardımcılar ----------

    /** Go'daki {@code time.Time.Format("2006-01-02")} karşılığı — tarih kısmı. */
    private static String dateOnly(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return String.valueOf(o);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String ts(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
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
}
