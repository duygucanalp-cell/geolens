package dev.geolens.billing.web;

import dev.geolens.billing.Invoice;
import dev.geolens.billing.service.BillingService;
import dev.geolens.common.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
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

    @MockBean
    private BillingService billingService;

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
        when(billingService.createCheckoutSession(anyString(), any(), anyString()))
                .thenReturn(Map.of("session_id", "cs_1", "url", "https://app/ok"));

        mockMvc.perform(post("/v1/billing/checkout")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"tier\":\"pro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value("cs_1"))
                .andExpect(jsonPath("$.url").value("https://app/ok"));
    }

    @Test
    void checkoutServiceErrorReturns500() throws Exception {
        when(billingService.createCheckoutSession(anyString(), any(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "ödeme oturumu oluşturulamadı"));

        mockMvc.perform(post("/v1/billing/checkout")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"tier\":\"pro\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("ödeme oturumu oluşturulamadı"));
    }

    // ---------- Webhook ----------

    @Test
    void webhookNotConfiguredReturns501() throws Exception {
        when(billingService.handleWebhook(any(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_IMPLEMENTED, "webhook yapılandırılmamış"));

        mockMvc.perform(post("/v1/billing/webhook")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("webhook yapılandırılmamış"));
    }

    @Test
    void webhookInvalidSignatureReturns400() throws Exception {
        when(billingService.handleWebhook(any(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz webhook"));

        mockMvc.perform(post("/v1/billing/webhook")
                        .header("X-Tenant-ID", TENANT)
                        .header("Stripe-Signature", "t=1620000000,v1=bad")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz webhook"));
    }

    @Test
    void webhookSuccess() throws Exception {
        when(billingService.handleWebhook(any(), anyString()))
                .thenReturn(Map.of("status", "ok"));

        mockMvc.perform(post("/v1/billing/webhook")
                        .header("X-Tenant-ID", TENANT)
                        .header("Stripe-Signature", "t=1620000000,v1=valid")
                        .contentType("application/json")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ---------- Abonelik ----------

    @Test
    void subscriptionSuccess() throws Exception {
        when(billingService.getSubscription(anyString()))
                .thenReturn(Map.of("tenant_id", "T01", "tier", "business", "updated_at", "2026-08-14T00:00:00Z"));

        mockMvc.perform(get("/v1/billing/subscription")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value("T01"))
                .andExpect(jsonPath("$.tier").value("business"));
    }

    @Test
    void subscriptionNotFoundReturns404() throws Exception {
        when(billingService.getSubscription(anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "kiracı bulunamadı"));
        mockMvc.perform(get("/v1/billing/subscription")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("kiracı bulunamadı"));
    }

    // ---------- Faturalar ----------

    @Test
    void listInvoicesSuccess() throws Exception {
        when(billingService.listInvoices(anyString()))
                .thenReturn(Map.of("invoices", List.of(invoice("inv1", "INV-1")), "count", 1));

        mockMvc.perform(get("/v1/billing/invoices")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.invoices[0].id").value("inv1"))
                .andExpect(jsonPath("$.invoices[0].number").value("INV-1"));
    }

    @Test
    void getInvoiceNotFoundReturns404() throws Exception {
        when(billingService.getInvoice(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "fatura bulunamadı"));
        mockMvc.perform(get("/v1/billing/invoices/nope")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("fatura bulunamadı"));
    }

    @Test
    void getInvoiceSuccess() throws Exception {
        when(billingService.getInvoice(anyString(), anyString()))
                .thenReturn(invoice("inv1", "INV-1"));

        mockMvc.perform(get("/v1/billing/invoices/inv1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("inv1"))
                .andExpect(jsonPath("$.number").value("INV-1"))
                .andExpect(jsonPath("$.amount_total").value(12000));
    }

    // ---------- e-Fatura ----------

    @Test
    void submitEFaturaValidationErrors() throws Exception {
        submitBad("not-json");
        submitBad("{\"vat_rate\":20,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}");
        submitBad("{\"invoice_type\":\"pdf\",\"vat_rate\":20,\"customer_name\":\"Acme\"}");
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
        when(billingService.submitEFatura(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.CONFLICT, "fatura zaten e-Fatura/e-Arşiv olarak gönderilmiş"));

        mockMvc.perform(post("/v1/billing/invoices/inv1/efatura")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"invoice_type\":\"efatura\",\"vat_rate\":20,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("fatura zaten e-Fatura/e-Arşiv olarak gönderilmiş"));
    }

    @Test
    void submitEFaturaSuccess() throws Exception {
        when(billingService.submitEFatura(anyString(), anyString(), any()))
                .thenReturn(Map.of(
                        "invoice", invoice("inv1", "INV-1"),
                        "gib", Map.of("status", "accepted")));

        mockMvc.perform(post("/v1/billing/invoices/inv1/efatura")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"invoice_type\":\"efatura\",\"vat_rate\":20,\"customer_name\":\"Acme\",\"customer_tax_no\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.id").value("inv1"))
                .andExpect(jsonPath("$.gib.status").value("accepted"));
    }

    // ---------- Portal ----------

    @Test
    void portalSuccess() throws Exception {
        when(billingService.createPortalSession(anyString(), any(), anyString()))
                .thenReturn(Map.of("url", "https://billing.stripe.com/portal"));

        mockMvc.perform(post("/v1/billing/portal")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"return_url\":\"/billing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.com/portal"));
    }

    @Test
    void portalServiceErrorReturns500() throws Exception {
        when(billingService.createPortalSession(anyString(), any(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "portal oturumu oluşturulamadı"));

        mockMvc.perform(post("/v1/billing/portal")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"return_url\":\"/billing\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("portal oturumu oluşturulamadı"));
    }

    // ---------- yardımcılar ----------

    private static Invoice invoice(String id, String number) {
        return new Invoice(
                id, "in_1", number, "paid", 12000L, "try",
                null, null, "", "", "2026-08-01T00:00:00Z",
                12000L, 0, 0L, "standard",
                "", "", "", "", "none", "", "");
    }
}
