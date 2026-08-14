package dev.geolens.delivery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code delivery/digest_test.go} portu. */
class DigestTest {

    private static String digest(List<DigestBrandScore> brands, List<DigestRecommendation> recs, String ws) {
        return DeliveryService.buildDigestHTML("Test", brands, recs, ws, "t-1");
    }

    @Test
    void buildDigestHtmlEmptyBrandsAndRecs() {
        String html = digest(null, null, "ws-1");
        assertTrue(html.contains("GeoLens Haftal"), "HTML digest başlığı içermeli");
        assertTrue(html.contains("Hen") || html.contains("ölçüm"), "HTML boş marka durumu göstermeli");
        assertTrue(html.contains("öneri"), "HTML boş öneri durumu göstermeli");
        assertTrue(html.contains("Panoda"), "HTML pano butonu içermeli");
        assertTrue(html.contains("Panoya Git"), "HTML pano bağlantısı içermeli");
    }

    @Test
    void buildDigestHtmlWithBrands() {
        List<DigestBrandScore> brands = List.of(
                new DigestBrandScore("b1", "Acme", 85, 80, 5),
                new DigestBrandScore("b2", "BetaCorp", 62, 70, -8),
                new DigestBrandScore("b3", "GammaInc", 50, 50, 0));

        String html = digest(brands, null, "ws-1");

        assertTrue(html.contains("Acme"));
        assertTrue(html.contains("BetaCorp"));
        assertTrue(html.contains("GammaInc"));
        assertTrue(html.contains("change-neutral"), "HTML nötr değişim göstergesi içermeli");
        assertTrue(html.contains("85"), "HTML 85 skorunu içermeli");
        assertTrue(html.contains("62"), "HTML 62 skorunu içermeli");
    }

    @Test
    void buildDigestHtmlWithRecommendations() {
        List<DigestRecommendation> recs = List.of(
                new DigestRecommendation("Acme", "Skor Dususu", "Gorunurluk skorunuz dusuyor"),
                new DigestRecommendation("BetaCorp", "Trend", "Trend analizi yapmaniz onerilir"));

        String html = digest(null, recs, "ws-1");

        assertTrue(html.contains("Gorunurluk") || html.contains("Görünürlük"));
        assertTrue(html.contains("Trend analizi"));
        assertTrue(html.contains("Acme:"), "HTML öneride marka adı içermeli");
    }

    @Test
    void buildDigestHtmlDashboardUrl() {
        String html = digest(null, null, "ws-custom");
        assertTrue(html.contains("ws-custom"));
        assertTrue(html.contains("https://app.geolens.ai/v1/workspaces/ws-custom/dashboard"));
    }

    @Test
    void escapeHtmlNoSpecialChars() {
        assertEquals("Merhaba Dunya", DeliveryService.escapeHtml("Merhaba Dunya"));
    }

    @Test
    void escapeHtmlAmpersand() {
        assertEquals("Acme &amp; Co", DeliveryService.escapeHtml("Acme & Co"));
    }

    @Test
    void escapeHtmlLessThan() {
        assertEquals("a &lt; b", DeliveryService.escapeHtml("a < b"));
    }

    @Test
    void escapeHtmlGreaterThan() {
        assertEquals("a &gt; b", DeliveryService.escapeHtml("a > b"));
    }

    @Test
    void escapeHtmlDoubleQuote() {
        assertTrue(DeliveryService.escapeHtml("\"quote\"").contains("&quot;"));
    }

    @Test
    void escapeHtmlSingleQuote() {
        assertTrue(DeliveryService.escapeHtml("'quote'").contains("&#39;"));
    }

    @Test
    void escapeHtmlAllSpecialChars() {
        String result = DeliveryService.escapeHtml("<div class=\"test\">Acme & Co</div>");
        assertTrue(result.contains("&lt;"));
        assertTrue(result.contains("&gt;"));
        assertTrue(result.contains("&quot;"));
        assertTrue(result.contains("&amp;"));
    }

    @Test
    void escapeHtmlEmptyString() {
        assertEquals("", DeliveryService.escapeHtml(""));
    }
}