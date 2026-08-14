package dev.geolens.billing;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go billing/handler_test.go parity testleri — Stripe imza doğrulama ve mock mod. */
class StripeClientTest {

    /** Stripe webhook HMAC imzasını test için üretir — Go {@code buildSignature} portu. */
    private static String buildSignature(String timestamp, String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(sig);
    }

    @Test
    void webhookSignatureInvalid() {
        StripeClient s = new StripeClient("", "secret");
        assertThrows(BillingException.class,
                () -> s.parseWebhook("{\"id\":\"evt_1\"}", "t=1620000000,v1=bad"));
    }

    @Test
    void webhookSignatureMissingSecretSkipsVerify() {
        StripeClient s = new StripeClient("", "");
        StripeEvent ev = s.parseWebhook(
                "{\"id\":\"evt_1\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}", null);
        assertEquals("invoice.paid", ev.type());
    }

    @Test
    void webhookSignatureValid() throws Exception {
        StripeClient s = new StripeClient("", "whsec_test");
        String payload = "{\"id\":\"evt_1\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}";

        StripeEvent ev = s.parseWebhook(payload, buildSignature("1620000000", payload, "whsec_test"));
        assertEquals("invoice.paid", ev.type());
    }

    @Test
    void checkoutMockMode() {
        StripeClient s = new StripeClient("mock", "");
        StripeClient.CheckoutSession sess = s.createCheckout(
                "T01", "pro", "usd", "https://app/ok", "https://app/cancel");
        assertEquals("cs_mock_T01", sess.id());
        assertEquals("https://app/ok", sess.url());
    }

    @Test
    void checkoutInvalidTier() {
        StripeClient s = new StripeClient("mock", "");
        assertThrows(IllegalArgumentException.class,
                () -> s.createCheckout("T01", "gold", "usd", "u", "c"));
    }

    @Test
    void portalMockMode() {
        StripeClient s = new StripeClient("mock", "");
        String url = s.createPortalSession("T01", "/billing");
        assertEquals("/billing", url);
    }

    @Test
    void metadataTenantIdFromObject() {
        StripeClient s = new StripeClient("mock", "");
        StripeEvent ev = s.parseWebhook("""
                {"id":"evt_1","type":"checkout.session.completed","data":{"object":{"metadata":{"tenant_id":"T01"}}}}
                """, null);
        assertEquals("T01", ev.metadataTenantId());
    }

    @Test
    void invoiceJsonTaxFields() {
        Invoice inv = new Invoice(
                "inv1", "in_1", "INV-1", "paid", 12000, "try",
                null, null, "", "", "2026-08-01T00:00:00Z",
                10000, 20, 2000, "efatura",
                "Acme", "123", "", "", "accepted", "doc1", "gib1");

        // Go Invoice JSON alan adlarıyla birebir serileşmeli (TestInvoiceJSON_TaxFields)
        String json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(inv).toString();
        assertTrue(json.contains("\"vat_rate\":20"), json);
        assertTrue(json.contains("\"vat_amount\":2000"), json);
        assertTrue(json.contains("\"subtotal\":10000"), json);
        assertTrue(json.contains("\"invoice_type\":\"efatura\""), json);
        assertTrue(json.contains("\"gib_status\":\"accepted\""), json);
        assertTrue(json.contains("\"document_id\":\"doc1\""), json);
        assertTrue(json.contains("\"customer_name\":\"Acme\""), json);
    }
}
