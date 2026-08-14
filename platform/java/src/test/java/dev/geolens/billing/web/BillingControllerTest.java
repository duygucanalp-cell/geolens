package dev.geolens.billing.web;

import dev.geolens.billing.EFaturaProvider;
import dev.geolens.billing.GIBResponse;
import dev.geolens.billing.GIBStatus;
import dev.geolens.billing.Invoice;
import dev.geolens.billing.StripeClient;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go billing/handler_test.go parity testleri — faturalama REST. */
@WebMvcTest(BillingController.class)
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @MockBean
    private StripeClient stripe;

    @MockBean
    private EFaturaProvider efatura;

    private static final String TENANT = "T01";

    // ---------- CreateCheckoutSession ----------

    @Test
    void checkoutMissingTierReturns400() throws Exception {
        mockMvc.perform(post("/v1/billing/checkout")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"success_url\":\"https://app/ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("tier zorunludur (pro, business, enterprise)"));
    }

    @Test
    void checkoutInvalidTierReturns400() throws Exception {
        mockMvc.perform(post("/v1/billing/checkout")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"tier\":\"gold\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz tier (pro, business, enterprise)"));
    }

    @Test
    void checkoutSuccess() throws Exception {
        when(stripe.createCheckout(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StripeClient.CheckoutSession("cs_1", "https://app/ok"));

        mockMvc.perform(post("/v1/billing/checkout")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"tier\":\"pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value("cs_1"))
                .andExpect(jsonPath("$.url").value("https://app/ok"));
    }

    // ---------- Webhook ----------

    @Test
    void webhookNotConfiguredReturns501() throws Exception {
        when(stripe.webhookSecret()).thenReturn("");
        mockMvc.perform(post("/v1/billing/webhook")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("webhook yapılandırılmamış"));
    }

    @Test
    void webhookInvalidSignatureReturns400() throws Exception {
        when(stripe.webhookSecret()).thenReturn("whsec_test");
        when(stripe.parseWebhook(anyString(), anyString()))
                .thenThrow(new dev.geolens.billing.BillingException("stripe webhook imzası eşleşmiyor"));

        mockMvc.perform(post("/v1/billing/webhook")
                        .header("X-Tenant-ID", TENANT)
                        .header("Stripe-Signature", "t=1620000000,v1=bad")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz webhook"));
    }

    @Test
    void webhookCheckoutCompletedUpdatesTier() throws Exception {
        when(stripe.webhookSecret()).thenReturn("whsec_test");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String payload = """
                {"id":"evt_1","type":"checkout.session.completed","data":{"object":{
                    "client_reference_id":"T01","customer":"cus_1","subscription":"sub_1",
                    "metadata":{"tenant_id":"T01","tier":"business"}}}}
                """;
        when(stripe.parseWebhook(anyString(), anyString()))
                .thenReturn(StripeEventFrom(payload, mapper));

        mockMvc.perform(post("/v1/billing/webhook")
                        .header("X-Tenant-ID", TENANT)
                        .header("Stripe-Signature", "t=1620000000,v1=valid")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ---------- Abonelik ----------

    @Test
    void subscriptionSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(tenantRow("T01", "business")));

        mockMvc.perform(get("/v1/billing/subscription")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value("T01"))
                .andExpect(jsonPath("$.tier").value("business"));
    }

    @Test
    void subscriptionNotFoundReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);
        mockMvc.perform(get("/v1/billing/subscription")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("kiracı bulunamadı"));
    }

    // ---------- Faturalar ----------

    @Test
    void listInvoicesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(invoiceRow("inv1", "INV-1"))));

        mockMvc.perform(get("/v1/billing/invoices")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.invoices[0].id").value("inv1"))
                .andExpect(jsonPath("$.invoices[0].number").value("INV-1"))
                .andExpect(jsonPath("$.invoices[0].stripe_invoice_id").value("in_1"));
    }

    @Test
    void getInvoiceNotFoundReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);
        mockMvc.perform(get("/v1/billing/invoices/nope")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("fatura bulunamadı"));
    }

    @Test
    void getInvoiceSuccess() throws Exception {
        Map<String, Object> row = invoiceRow("inv1", "INV-1");
        row.put("vat_rate", 20);
        row.put("vat_amount", 2000L);
        row.put("subtotal", 10000L);
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(get("/v1/billing/invoices/inv1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("inv1"))
                .andExpect(jsonPath("$.number").value("INV-1"))
                .andExpect(jsonPath("$.amount_total").value(12000))
                .andExpect(jsonPath("$.vat_rate").value(20));
    }

    // ---------- e-Fatura ----------

    @Test
    void submitEFaturaValidationErrors() throws Exception {
        submitBad("not-json");
        submitBad("{\"vat_rate\":20,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}");
        submitBad("{\"invoice_type\":\"pdf\",\"vat_rate\":20,\"customer_name\":\"Acme\"}");
        submitBad("{\"invoice_type\":\"efatura\",\"vat_rate\":15,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}");
        submitBad("{\"invoice_type\":\"efatura\",\"vat_rate\":20,\"customer_tax_no\":\"123\"}");
        submitBad("{\"invoice_type\":\"efatura\",\"vat_rate\":20,\"customer_name\":\"Acme\"}");
        submitBad("{\"invoice_type\":\"earsiv\",\"vat_rate\":20,\"customer_name\":\"Acme\"}");
    }

    private void submitBad(String body) throws Exception {
        mockMvc.perform(post("/v1/billing/invoices/INV1/efatura")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitEFaturaAlreadySentReturns409() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(invoiceRow("inv1", "INV-1", "efatura", "accepted")));

        mockMvc.perform(post("/v1/billing/invoices/inv1/efatura")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"invoice_type\":\"efatura\",\"vat_rate\":20,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("fatura zaten e-Fatura/e-Arşiv olarak gönderilmiş"));
    }

    @Test
    void submitEFaturaSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(invoiceRow("inv1", "INV-1")));
        when(efatura.send(any()))
                .thenReturn(new GIBResponse(GIBStatus.ACCEPTED, "gib_1",
                        "GİB Entegrasyon Servisi: fatura kabul edildi (mock mod)",
                        OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(post("/v1/billing/invoices/inv1/efatura")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"invoice_type\":\"efatura\",\"vat_rate\":20,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.invoice_type").value("efatura"))
                .andExpect(jsonPath("$.invoice.subtotal").value(12000))
                .andExpect(jsonPath("$.invoice.vat_amount").value(2400))
                .andExpect(jsonPath("$.invoice.gib_status").value("accepted"))
                .andExpect(jsonPath("$.gib.status").value("accepted"));
    }

    // ---------- Portal ----------

    @Test
    void portalSuccess() throws Exception {
        when(stripe.createPortalSession(anyString(), anyString())).thenReturn("https://billing.stripe.com/portal");

        mockMvc.perform(post("/v1/billing/portal")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"return_url\":\"/billing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.com/portal"));
    }

    // ---------- yardımcılar ----------

    private static dev.geolens.billing.StripeEvent StripeEventFrom(String payload,
                                                                   com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload);
        return new dev.geolens.billing.StripeEvent(
                node.path("id").asText(),
                node.path("type").asText(),
                node.path("api_version").asText(),
                node.path("data").path("object"),
                node.path("created").asLong());
    }

    private static Map<String, Object> tenantRow(String id, String tier) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tier", tier);
        m.put("updated_at", "2026-08-14T00:00:00Z");
        return m;
    }

    private static Map<String, Object> invoiceRow(String id, String number) {
        return invoiceRow(id, number, "standard", "none");
    }

    private static Map<String, Object> invoiceRow(String id, String number, String type, String gib) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("stripe_invoice_id", "in_1");
        m.put("number", number);
        m.put("status", "paid");
        m.put("amount_total", 12000L);
        m.put("currency", "try");
        m.put("period_start", null);
        m.put("period_end", null);
        m.put("hosted_invoice_url", "");
        m.put("invoice_pdf", "");
        m.put("created_at", "2026-08-01T00:00:00Z");
        m.put("subtotal", 12000L);
        m.put("vat_rate", 0);
        m.put("vat_amount", 0L);
        m.put("invoice_type", type);
        m.put("customer_name", "");
        m.put("customer_tax_no", "");
        m.put("customer_identity", "");
        m.put("customer_address", "");
        m.put("gib_status", gib);
        m.put("document_id", "");
        m.put("gib_response_id", "");
        return m;
    }
}
