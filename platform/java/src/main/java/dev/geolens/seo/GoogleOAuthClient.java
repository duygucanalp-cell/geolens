package dev.geolens.seo;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Google OAuth2 token alışverişi — Go {@code seo} paketi portu (FR-B8).
 * <p>Authorization code'u token ile değiştirir ({@link #exchangeCode}), access token'dan
 * e-posta alır ({@link #tokenEmail}) ve refresh token ile yeni access token üretir
 * ({@link #refresh}). Gerçek Google uçlarını çağırır; yapılandırma yoksa boş döner.
 */
public class GoogleOAuthClient {

    public static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String GOOGLE_TOKENINFO_URL = "https://www.googleapis.com/oauth2/v2/tokeninfo";
    public static final String SCOPE_SEARCH_CONSOLE = "https://www.googleapis.com/auth/webmasters.readonly";
    public static final String SCOPE_GA4 = "https://www.googleapis.com/auth/analytics.readonly";

    private final String clientId;
    private final String clientSecret;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleOAuthClient(String clientId, String clientSecret) {
        this(clientId, clientSecret, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public GoogleOAuthClient(String clientId, String clientSecret, HttpClient http) {
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.http = http;
    }

    public boolean configured() {
        return !clientId.isBlank();
    }

    /** OAuth consent URL'si üretir — Go {@code GetAuthURL} portu. */
    public static String buildAuthUrl(String clientId, String redirectUri, String scopes, String state) {
        return GOOGLE_AUTH_URL + "?client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code&scope=" + urlEncode(scopes)
                + "&access_type=offline&state=" + state;
    }

    /** Authorization code'u token ile değiştirir — Go {@code exchangeCode} portu. */
    public TokenResponse exchangeCode(String code, String redirectUri) {
        Map<String, String> form = Map.of(
                "code", code,
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code");
        TokenResponse tr = postForm(GOOGLE_TOKEN_URL, form);
        if (tr.accessToken() == null || tr.accessToken().isBlank()) {
            throw new SeoException("token yanıtında access_token yok");
        }
        if (tr.expiresIn() <= 0) {
            tr = tr.withExpiresIn(3600);
        }
        tr = tr.withExpiresAt(Instant.now().plusSeconds(tr.expiresIn()));
        tr = tr.withEmail(tokenEmail(tr.accessToken()));
        return tr;
    }

    /** Access token'dan Google hesap e-postasını alır — Go {@code getTokenEmail} portu. */
    public String tokenEmail(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_TOKENINFO_URL + "?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "";
            }
            var node = mapper.readTree(resp.body());
            return node.path("email").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Refresh token ile yeni access token üretir — Go {@code refreshAccessToken} portu. */
    public TokenResponse refresh(String refreshToken) {
        Map<String, String> form = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token");
        TokenResponse tr = postForm(GOOGLE_TOKEN_URL, form);
        if (tr.accessToken() == null || tr.accessToken().isBlank()) {
            throw new SeoException("refresh yanıtı boş");
        }
        if (tr.expiresIn() <= 0) {
            tr = tr.withExpiresIn(3600);
        }
        return tr.withExpiresAt(Instant.now().plusSeconds(tr.expiresIn()));
    }

    private TokenResponse postForm(String url, Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return mapper.readValue(resp.body(), TokenResponse.class);
        } catch (Exception e) {
            throw new SeoException("token isteği başarısız: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** Google OAuth2 token yanıtı — Go {@code tokenResponse} struct portu. */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            int expiresIn,
            String scope,
            String tokenType,
            String email,
            Instant expiresAt) {

        public TokenResponse withExpiresIn(int seconds) {
            return new TokenResponse(accessToken, refreshToken, seconds, scope, tokenType, email, expiresAt);
        }

        public TokenResponse withExpiresAt(Instant at) {
            return new TokenResponse(accessToken, refreshToken, expiresIn, scope, tokenType, email, at);
        }

        public TokenResponse withEmail(String e) {
            return new TokenResponse(accessToken, refreshToken, expiresIn, scope, tokenType, e, expiresAt);
        }
    }
}
