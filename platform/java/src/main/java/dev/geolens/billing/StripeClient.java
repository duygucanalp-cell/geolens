package dev.geolens.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stripe istemcisi — Go {@code billing.stripe} portu (FR-A6/HT2).
 * <p>API key boş veya {@code "mock"} ise mock modda çalışır (gerçek ödeme alınmaz).
 * Webhook imza doğrulaması HMAC-SHA256 (Stripe {@code v1=} şeması) ile yapılır.
 * HTTP çağrıları {@link HttpClient} üzerinden gerçekleşir (30 sn timeout).
 */
public class StripeClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String webhookSecret;
    private final HttpClient httpClient;

    /** tier+currency → Stripe Price ID eşlemesi (HT2 multi-currency); boşsa varsayılan map kullanılır. */
    private Map<String, String> priceIds;

    public StripeClient(String apiKey, String webhookSecret) {
        this(apiKey, webhookSecret, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    StripeClient(String apiKey, String webhookSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.httpClient = httpClient;
    }

    public String webhookSecret() {
        return webhookSecret;
    }

    /** Env'den gelen tier→price eşlemesini uygular (PO review §4, HT2 multi-currency). */
    public void setPriceIds(Map<String, String> priceIds) {
        if (priceIds != null && !priceIds.isEmpty()) {
            this.priceIds = new LinkedHashMap<>(priceIds);
        }
    }

    /**
     * Verilen para birimi için tier→priceId araması döndürür.
     * Desteklenen: usd, eur, try (TR), gbp (HT2 multi-currency). Bilinmeyen için usd'e düşer.
     */
    public Map<String, String> currencyPriceIds(String currency) {
        if (priceIds != null) {
            return priceIds;
        }
        return switch (currency) {
            case "eur" -> Map.of(
                    "pro", "price_pro_monthly_eur",
                    "business", "price_business_monthly_eur",
                    "enterprise", "price_enterprise_monthly_eur");
            case "try" -> Map.of(
                    "pro", "price_pro_monthly_try",
                    "business", "price_business_monthly_try",
                    "enterprise", "price_enterprise_monthly_try");
            case "gbp" -> Map.of(
                    "pro", "price_pro_monthly_gbp",
                    "business", "price_business_monthly_gbp",
                    "enterprise", "price_enterprise_monthly_gbp");
            default -> Map.of(
                    "pro", "price_pro_monthly",
                    "business", "price_business_monthly",
                    "enterprise", "price_enterprise_monthly");
        };
    }

    public record CheckoutSession(String id, String url) {
    }

    /** Checkout oturumu oluşturur — Go {@code CreateCheckout} portu. */
    public CheckoutSession createCheckout(String tenantId, String tier, String currency,
                                          String successUrl, String cancelUrl) {
        Map<String, String> priceMap = currencyPriceIds(currency);
        String priceId = priceMap.get(tier);
        if (priceId == null) {
            throw new IllegalArgumentException("bilinmeyen tier: " + tier);
        }

        if (apiKey == null || apiKey.isBlank() || "mock".equals(apiKey)) {
            return new CheckoutSession("cs_mock_" + tenantId, successUrl);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "subscription");
        params.put("line_items", java.util.List.of(Map.of("price", priceId, "quantity", 1)));
        params.put("client_reference_id", tenantId);
        params.put("success_url", successUrl);
        params.put("cancel_url", cancelUrl);
        params.put("metadata", Map.of("tenant_id", tenantId, "tier", tier, "currency", currency));

        try {
            String body = post("https://api.stripe.com/v1/checkout/sessions", params);
            JsonNode result = MAPPER.readTree(body);
            return new CheckoutSession(result.path("id").asText(), result.path("url").asText());
        } catch (Exception e) {
            throw new BillingException("stripe checkout hatası", e);
        }
    }

    /**
     * Stripe Billing Portal oturumu açar — Go {@code CreatePortalSession} portu (FR-A6 self-serve UI).
     * Kiracının Stripe customer ID'si metadata eşlemesiyle bulunur.
     */
    public String createPortalSession(String tenantId, String returnUrl) {
        if (apiKey == null || apiKey.isBlank() || "mock".equals(apiKey)) {
            return returnUrl;
        }

        try {
            String customerBody = get("https://api.stripe.com/v1/customers?limit=100");
            JsonNode customers = MAPPER.readTree(customerBody);
            String customerId = null;
            for (JsonNode c : customers.path("data")) {
                if (tenantId.equals(c.path("metadata").path("tenant_id").asText())) {
                    customerId = c.path("id").asText();
                    break;
                }
            }
            if (customerId == null || customerId.isBlank()) {
                throw new BillingException("tenant için stripe customer bulunamadı: " + tenantId);
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("customer", customerId);
            params.put("return_url", returnUrl);
            String body = post("https://api.stripe.com/v1/billing_portal/sessions", params);
            JsonNode result = MAPPER.readTree(body);
            String url = result.path("url").asText();
            if (url.isBlank()) {
                throw new BillingException("stripe portal url boş");
            }
            return url;
        } catch (BillingException e) {
            throw e;
        } catch (Exception e) {
            throw new BillingException("stripe portal hatası", e);
        }
    }

    /**
     * Webhook gövdesini ayrıştırır ve imzayı doğrular — Go {@code ParseWebhook} portu.
     * Secret boşsa imza doğrulaması atlanır.
     */
    public StripeEvent parseWebhook(String body, String signatureHeader) {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signatureHeader == null || signatureHeader.isBlank()) {
                throw new BillingException("Stripe-Signature header eksik");
            }
            verifySignature(signatureHeader, body, webhookSecret);
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            return new StripeEvent(
                    node.path("id").asText(),
                    node.path("type").asText(),
                    node.path("api_version").asText(),
                    node.path("data").path("object"),
                    node.path("created").asLong());
        } catch (Exception e) {
            throw new BillingException("webhook ayrıştırma hatası", e);
        }
    }

    /** Stripe webhook HMAC imzasını doğrular — Go {@code verifyStripeSignature} portu. */
    static void verifySignature(String signatureHeader, String payload, String secret) {
        String sigTime = null;
        String sigValue = null;
        for (String part : signatureHeader.split(",")) {
            part = part.trim();
            if (part.startsWith("t=")) {
                sigTime = part.substring(2);
            } else if (part.startsWith("v1=")) {
                sigValue = part.substring(3);
            }
        }
        if (sigTime == null || sigValue == null) {
            throw new BillingException("geçersiz Stripe-Signature formatı");
        }

        String signedPayload = sigTime + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String expectedHex = HexFormat.of().formatHex(expected);
            if (!MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                    sigValue.getBytes(StandardCharsets.UTF_8))) {
                throw new BillingException("stripe webhook imzası eşleşmiyor");
            }
        } catch (BillingException e) {
            throw e;
        } catch (Exception e) {
            throw new BillingException("stripe imza doğrulama hatası", e);
        }
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new BillingException("stripe api hatası (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        return resp.body();
    }

    private String post(String url, Map<String, Object> params) throws Exception {
        String body = MAPPER.writeValueAsString(params);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new BillingException("stripe api hatası (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        return resp.body();
    }
}
