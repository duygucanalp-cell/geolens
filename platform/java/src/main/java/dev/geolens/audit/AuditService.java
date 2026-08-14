package dev.geolens.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.util.Ulid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Site denetim servisi — Go {@code audit.service} portu (0421 G).
 * <p>Saf mantık (skor, robots.txt parse, URL normalize) static; HTTP kontrolleri
 * {@link java.net.http.HttpClient} ile yapılır. Veritabanı yoksa {@link #save(AuditResult)}
 * no-op'tur (Go {@code pool == nil} davranışı).
 */
public final class AuditService {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_BODY_BYTES = 1 << 20; // max 1MB

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final HttpClient httpClient;

    public AuditService(JdbcTemplate jdbc, TransactionTemplate tx) {
        this(jdbc, tx, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build());
    }

    public AuditService(JdbcTemplate jdbc, TransactionTemplate tx, HttpClient httpClient) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.httpClient = httpClient;
    }

    private static void setTenant(JdbcTemplate jdbc, String tenantId) {
        jdbc.execute("SELECT set_config('app.tenant_id', ?, true)",
                (PreparedStatementCallback<Void>) ps -> {
                    ps.setString(1, tenantId);
                    ps.execute();
                    return null;
                });
    }

    private void runInTenant(String tenantId, Runnable work) {
        if (tx == null) {
            setTenant(jdbc, tenantId);
            work.run();
            return;
        }
        tx.executeWithoutResult(status -> {
            setTenant(jdbc, tenantId);
            work.run();
        });
    }

    /** Belirtilen marka için eksiksiz bir site denetimi gerçekleştirir — Go {@code Audit} portu. */
    public AuditResult audit(String brandId, String brandName, String websiteUrl) {
        List<Issue> issues = new ArrayList<>();

        // 1. Robots.txt kontrolü
        RobotsTxtCheck robotsResult = checkRobotsTxt(websiteUrl);
        if (robotsResult != null && robotsResult.disallowedAll()) {
            issues.add(new Issue("critical", "robots", "AI botları tamamen engellenmiş",
                    websiteUrl + " robots.txt tüm kullanıcı ajanlarını engelliyor. AI görünürlük ölçümü yapılamayabilir.",
                    "robots.txt'den 'Disallow: /' kuralını kaldırın veya AI botlarına özel izin verin."));
        }

        // 2. Bot erişim kontrolü
        BotAccessCheck botResult = checkBotAccess(websiteUrl);

        // 3. SSR sinyal kontrolü
        SSRCheck ssrResult = checkSSR(websiteUrl);
        if (ssrResult != null) {
            if (!ssrResult.hasMetaTags()) {
                issues.add(new Issue("medium", "ssr", "Meta etiketleri eksik",
                        "Sayfada meta description veya title etiketi bulunamadı. AI motorları içeriği doğru anlayamayabilir.",
                        "Her sayfaya unique meta title ve description ekleyin."));
            }
            if (!ssrResult.hasOGTags()) {
                issues.add(new Issue("medium", "ssr", "Open Graph etiketleri eksik",
                        "Sayfada Open Graph (og:) etiketleri bulunamadı. Sosyal medya ve AI görünürlüğü için önemlidir.",
                        "Standart OG etiketlerini (og:title, og:description, og:image) ekleyin."));
            }
            if (!ssrResult.hasStructuredData()) {
                issues.add(new Issue("medium", "ssr", "Yapılandırılmış veri eksik",
                        "Sayfada JSON-LD veya Schema.org yapılandırılmış verisi bulunamadı. AI motorları için bağlam sağlamak önemlidir.",
                        "JSON-LD formatında Organization, WebSite veya BreadcrumbList şeması ekleyin."));
            }
        }

        // 4. SSRF koruma kontrolü
        SSRFCheck ssrfResult = checkSSRFProtection(websiteUrl);

        // Genel skor hesapla
        AuditResult interim = new AuditResult("", brandId, brandName, websiteUrl, "", "",
                robotsResult, botResult, ssrResult, ssrfResult, 0, issues, Instant.now());
        double overallScore = computeOverallScore(interim);

        // Varsayılan başarı durumu
        if (botResult != null && botResult.accessible()) {
            issues.add(new Issue("info", "bot_access", "AI botları siteye erişebiliyor",
                    "Site " + botResult.statusCode() + " HTTP kodu döndü, " + botResult.responseTimeMs()
                            + " ms içinde yanıt verdi.",
                    "Mevcut durumu koruyun. Yanıt süresini iyileştirmek için CDN kullanmayı değerlendirin."));
        }

        return new AuditResult(Ulid.generate(), brandId, brandName, websiteUrl, "", "",
                robotsResult, botResult, ssrResult, ssrfResult, overallScore, List.copyOf(issues), Instant.now());
    }

    /** Denetim sonucunu audit_results tablosuna yazar — Go {@code Save} portu. */
    public void save(AuditResult result) {
        if (jdbc == null) {
            return;
        }

        String robotsJson = toJson(result.robotsTxtCheck());
        String botJson = toJson(result.botAccessCheck());
        String ssrJson = toJson(result.ssrCheck());
        String ssrfJson = toJson(result.ssrfCheck());
        String issuesJson = toJson(result.issues());
        String resultJson = toJson(result);

        runInTenant(result.tenantId(), () -> jdbc.update("""
                INSERT INTO governance.audit_results
                    (id, brand_id, workspace_id, tenant_id, brand_name, website_url,
                     overall_score, robots_txt, bot_access, ssr, ssrf, issues, raw_result, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, now())
                """,
                result.id(), result.brandId(), result.workspaceId(), result.tenantId(),
                result.brandName(), result.websiteUrl(),
                result.overallScore(),
                robotsJson, botJson, ssrJson, ssrfJson,
                issuesJson, resultJson));
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value != null ? value : "{}");
        } catch (Exception e) {
            return "{}";
        }
    }

    RobotsTxtCheck checkRobotsTxt(String websiteUrl) {
        String baseUrl = normalizeUrl(websiteUrl);
        String robotsUrl = baseUrl + "/robots.txt";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", "GeoLens-Audit/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                return new RobotsTxtCheck(resp.statusCode() == 404, false, List.of(), false);
            }
            byte[] body = resp.body().length > MAX_BODY_BYTES
                    ? Arrays.copyOfRange(resp.body(), 0, MAX_BODY_BYTES)
                    : resp.body();
            return parseRobotsTxtContent(body, true);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new RobotsTxtCheck(false, false, List.of(), false);
        }
    }

    BotAccessCheck checkBotAccess(String websiteUrl) {
        List<String> tested = new ArrayList<>();
        AICrawler testAgent = AICrawler.KNOWN.get(0);
        tested.add(testAgent.userAgent());

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(websiteUrl))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", testAgent.userAgent())
                    .GET()
                    .build();
            long start = System.nanoTime();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // 2xx veya 4xx (izin yok) erişim sayılır, 3xx redirect de
            boolean accessible = resp.statusCode() < 400
                    || resp.statusCode() == 403
                    || resp.statusCode() == 401;
            return new BotAccessCheck(accessible, resp.statusCode(), elapsedMs, tested);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new BotAccessCheck(false, 0, 0, tested);
        }
    }

    SSRCheck checkSSR(String websiteUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(websiteUrl))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; GeoLens-Audit/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body().length > MAX_BODY_BYTES
                    ? Arrays.copyOfRange(resp.body(), 0, MAX_BODY_BYTES)
                    : resp.body();
            String lower = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

            boolean hasMetaTags = lower.contains("<title")
                    && (lower.contains("name=\"description\"") || lower.contains("name='description'"));
            boolean hasOGTags = lower.contains("property=\"og:") || lower.contains("property='og:");
            boolean hasStructuredData = lower.contains("application/ld+json")
                    || lower.contains("itemscope")
                    || lower.contains("itemtype=\"http");
            return new SSRCheck(hasMetaTags, hasOGTags, hasStructuredData, body.length);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new SSRCheck(false, false, false, 0);
        }
    }

    SSRFCheck checkSSRFProtection(String websiteUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(websiteUrl))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", "GeoLens-Audit/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            java.net.http.HttpHeaders headers = resp.headers();

            boolean hasCloudflare = headers.firstValue("CF-Ray").isPresent()
                    || headers.firstValue("Server").orElse("").toLowerCase(Locale.ROOT).contains("cloudflare");
            boolean hasAwsHeaders = headers.firstValue("X-Amz-Request-Id").isPresent()
                    || headers.firstValue("X-Amz-Rid").isPresent();
            boolean hasRateLimit = headers.firstValue("X-RateLimit-Limit").isPresent()
                    || headers.firstValue("X-RateLimit-Remaining").isPresent()
                    || headers.firstValue("Retry-After").isPresent();
            boolean cspPresent = headers.firstValue("Content-Security-Policy").isPresent();
            return new SSRFCheck(hasCloudflare, hasAwsHeaders, hasRateLimit, cspPresent);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new SSRFCheck(false, false, false, false);
        }
    }

    /** Tek tek kontrollerden genel denetim skorunu hesaplar — Go {@code computeOverallScore} portu. */
    static double computeOverallScore(AuditResult result) {
        double score = 100;

        // Robots.txt kritik hata
        if (result.robotsTxtCheck() != null && result.robotsTxtCheck().disallowedAll()) {
            score -= 40;
        }
        // Bot erişim sorunu
        if (result.botAccessCheck() != null && !result.botAccessCheck().accessible()) {
            score -= 20;
        }
        // SSR eksikleri
        if (result.ssrCheck() != null) {
            if (!result.ssrCheck().hasMetaTags()) {
                score -= 10;
            }
            if (!result.ssrCheck().hasOGTags()) {
                score -= 5;
            }
            if (!result.ssrCheck().hasStructuredData()) {
                score -= 10;
            }
        }
        // Güvenlik
        if (result.ssrfCheck() != null) {
            if (!result.ssrfCheck().hasCloudflare()) {
                score -= 5;
            }
            if (!result.ssrfCheck().cspPresent()) {
                score -= 5;
            }
        }

        return Math.max(0, score);
    }

    /** robots.txt içeriğini {@link RobotsTxtCheck} yapısına çevirir — Go {@code parseRobotsTxtContent} portu. */
    static RobotsTxtCheck parseRobotsTxtContent(byte[] body, boolean exists) {
        String content = new String(body, StandardCharsets.UTF_8);

        boolean disallowedAll = false;
        boolean allowsAIBots = true; // Varsayılan: AI botlarına izin var
        List<String> blockedPaths = new ArrayList<>();
        String currentAgent = "";

        for (String rawLine : content.split("\n")) {
            String line = rawLine.trim();
            String lineLower = line.toLowerCase(Locale.ROOT);

            if (lineLower.startsWith("user-agent:")) {
                currentAgent = line.substring(11).trim();
            } else if (lineLower.startsWith("disallow:")) {
                String path = line.substring(9).trim();
                if (currentAgent.equals("*") || currentAgent.isEmpty()) {
                    if (path.equals("/")) {
                        disallowedAll = true;
                    }
                    blockedPaths.add(path);
                }

                // AI bot kontrolü — eşleşen AI botu için Disallow: / varsa AllowsAIBots=false
                for (AICrawler crawler : AICrawler.KNOWN) {
                    if (currentAgent.equalsIgnoreCase(crawler.userAgent())
                            || currentAgent.equalsIgnoreCase(crawler.userAgent().split("/")[0])) {
                        if (path.equals("/")) {
                            allowsAIBots = false;
                        }
                    }
                }
            }
        }

        // Tüm botlar engellenmişse (User-agent: * Disallow: /) AI botları da engellenmiştir
        if (disallowedAll) {
            allowsAIBots = false;
        }

        return new RobotsTxtCheck(exists, allowsAIBots, blockedPaths, disallowedAll);
    }

    /** Site URL'sini robots.txt tabanına normalleştirir — Go {@code normalizeURL} portu. */
    static String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        String[] parts = url.split("/");
        if (parts.length >= 3) {
            return parts[0] + "//" + parts[2];
        }
        return url;
    }
}