package dev.geolens.billing.web;

import com.fasterxml.jackson.databind.JsonNode;
import dev.geolens.billing.BillingException;
import dev.geolens.billing.EFaturaProvider;
import dev.geolens.billing.GIBResponse;
import dev.geolens.billing.GIBStatus;
import dev.geolens.billing.Invoice;
import dev.geolens.billing.InvoiceDocument;
import dev.geolens.billing.InvoiceType;
import dev.geolens.billing.StripeClient;
import dev.geolens.billing.StripeEvent;
import dev.geolens.billing.TaxBreakdown;
import dev.geolens.billing.TaxCalculator;
import dev.geolens.billing.UblTrInvoice;
import dev.geolens.pdf.InvoiceData;
import dev.geolens.pdf.InvoicePdf;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Faturalama REST controller'ı — Go {@code billing.handler} portu (FR-A6, HT2 multi-currency).
 * <p>Route'lar (go cmd/api): POST /v1/billing/checkout, POST /v1/billing/webhook,
 * GET /v1/billing/subscription, GET /v1/billing/invoices, GET /v1/billing/invoices/{invoiceId},
 * POST /v1/billing/invoices/{invoiceId}/efatura, GET .../efatura/xml (UBL-TR),
 * GET .../pdf, POST /v1/billing/portal.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; webhook kiracıyı olay metadata/eşlemesinden çözer.
 */
@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private static final Set<String> VALID_TIERS = Set.of("pro", "business", "enterprise");

    private final DSLContext dsl;
    private final StripeClient stripe;
    private final EFaturaProvider efatura;

    public BillingController(DSLContext dsl, StripeClient stripe, EFaturaProvider efatura) {
        this.dsl = dsl;
        this.stripe = stripe;
        this.efatura = efatura;
    }

    // ---------- Checkout ----------

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(@RequestHeader("X-Tenant-ID") String tenantId,
                                                   @RequestBody CheckoutRequest req) {
        if (req == null || req.tier() == null || req.tier().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "tier zorunludur (pro, business, enterprise)");
        }
        if (!VALID_TIERS.contains(req.tier())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz tier (pro, business, enterprise)");
        }
        String currency = req.currency() == null || req.currency().isBlank() ? "usd" : req.currency();

        StripeClient.CheckoutSession session;
        try {
            session = stripe.createCheckout(tenantId, req.tier(), currency,
                    req.successUrl() == null ? "/" : req.successUrl(),
                    req.cancelUrl() == null ? "/" : req.cancelUrl());
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "ödeme oturumu oluşturulamadı");
        }

        return ResponseEntity.ok(Map.of(
                "session_id", session.id(),
                "url", session.url()));
    }

    // ---------- Webhook ----------

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestHeader(value = "Stripe-Signature", required = false) String signature,
                                           @RequestBody String body) {
        if (stripe.webhookSecret() == null || stripe.webhookSecret().isBlank()) {
            return error(HttpStatus.NOT_IMPLEMENTED, "webhook yapılandırılmamış");
        }

        StripeEvent event;
        try {
            event = stripe.parseWebhook(body, signature);
        } catch (RuntimeException e) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz webhook");
        }

        try {
            handleEvent(event);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "webhook işlenemedi");
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void handleEvent(StripeEvent event) {
        switch (event.type()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.payment_failed" -> {
                // uyarı logu — Go: slog.Warn("stripe ödeme başarısız")
            }
            case "invoice.created", "invoice.paid", "invoice.finalized", "invoice.updated" -> upsertInvoice(event);
            default -> {
                // bilinmeyen olay yok sayılır (Go ile aynı)
            }
        }
    }

    private void handleCheckoutCompleted(StripeEvent event) {
        JsonNode obj = event.object();
        String tenantId = obj.path("client_reference_id").asText("");
        if (tenantId.isBlank()) {
            tenantId = obj.path("metadata").path("tenant_id").asText("");
        }
        String tier = obj.path("metadata").path("tier").asText("");
        if (tier.isBlank()) {
            tier = "pro";
        }
        if (tenantId.isBlank()) {
            throw new BillingException("checkout.session.completed: tenant_id bulunamadı");
        }

        String customer = obj.path("customer").asText("");
        saveCustomerMapping(tenantId, customer);

        int updated = dsl.execute("""
                UPDATE identity.tenants SET tier = ?, updated_at = now() WHERE id = ?
                """, tier, tenantId);
        if (updated == 0) {
            // uyarı — Go: slog.Warn("tier güncelleme: tenant bulunamadı")
        }
    }

    private void upsertInvoice(StripeEvent event) {
        JsonNode obj = event.object();
        String invoiceId = obj.path("id").asText("");
        String customer = obj.path("customer").asText("");

        String tenantId = event.metadataTenantId();
        if (tenantId.isBlank() && !customer.isBlank()) {
            tenantId = lookupTenantByCustomer(customer);
        }
        if (tenantId.isBlank()) {
            throw new BillingException("invoice: tenant_id bulunamadı (invoice " + invoiceId + ")");
        }

        Long periodStart = obj.path("period_start").isMissingNode() || obj.path("period_start").isNull()
                ? null : obj.path("period_start").asLong();
        Long periodEnd = obj.path("period_end").isMissingNode() || obj.path("period_end").isNull()
                ? null : obj.path("period_end").asLong();
        JsonNode lines = obj.path("lines").path("data");
        if (lines.isArray() && lines.size() > 0) {
            JsonNode first = lines.get(0);
            long ps = first.path("period").path("start").asLong(0);
            long pe = first.path("period").path("end").asLong(0);
            if (ps != 0) {
                periodStart = ps;
            }
            if (pe != 0) {
                periodEnd = pe;
            }
        }

        String subscription = obj.path("subscription").isNull() ? null : obj.path("subscription").asText();
        long created = obj.path("created").asLong(0);

        dsl.execute("""
                INSERT INTO billing.invoices (
                    stripe_invoice_id, tenant_id, stripe_subscription, number, status,
                    amount_total, currency, period_start, period_end, hosted_invoice_url, invoice_pdf, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_timestamp(?), now())
                ON CONFLICT (stripe_invoice_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    amount_total = EXCLUDED.amount_total,
                    currency = EXCLUDED.currency,
                    number = EXCLUDED.number,
                    hosted_invoice_url = EXCLUDED.hosted_invoice_url,
                    invoice_pdf = EXCLUDED.invoice_pdf,
                    updated_at = now()
                """,
                invoiceId, tenantId, subscription,
                obj.path("number").asText(), obj.path("status").asText(),
                obj.path("amount_total").asLong(0), obj.path("currency").asText(),
                periodStart, periodEnd,
                obj.path("hosted_invoice_url").asText(), obj.path("invoice_pdf").asText(),
                created);

        // Stripe faturaları KDV kırılımı içermediği için varsayılan subtotal = amount_total, KDV = 0
        dsl.execute("""
                UPDATE billing.invoices
                SET subtotal = amount_total
                WHERE stripe_invoice_id = ? AND invoice_type = 'standard'
                """, invoiceId);
    }

    private String lookupTenantByCustomer(String customerId) {
        try {
            Record r = dsl.fetchOne("""
                    SELECT tenant_id FROM billing.stripe_customers WHERE customer_id = ?
                    """, customerId);
            return r == null ? "" : r.get(0, String.class);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void saveCustomerMapping(String tenantId, String customerId) {
        if (customerId == null || customerId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        try {
            dsl.execute("""
                    INSERT INTO billing.stripe_customers (tenant_id, customer_id, created_at)
                    VALUES (?, ?, now())
                    ON CONFLICT (tenant_id) DO UPDATE SET customer_id = EXCLUDED.customer_id
                    """, tenantId, customerId);
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("stripe customer eşleme kaydedilemedi")
        }
    }

    // ---------- Abonelik ----------

    @GetMapping("/subscription")
    public ResponseEntity<?> getSubscription(@RequestHeader("X-Tenant-ID") String tenantId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT tier, updated_at FROM identity.tenants WHERE id = ?
                    """, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "kiracı bulunamadı");
        }
        if (row == null) {
            return error(HttpStatus.NOT_FOUND, "kiracı bulunamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", tenantId);
        body.put("tier", row.get("tier"));
        body.put("updated_at", row.get("updated_at") == null ? null : String.valueOf(row.get("updated_at")));
        return ResponseEntity.ok(body);
    }

    // ---------- Faturalar ----------

    @GetMapping("/invoices")
    public ResponseEntity<?> listInvoices(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list(INVOICE_SELECT + " WHERE tenant_id = ? ORDER BY created_at DESC", tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "fatura listesi alınamadı");
        }

        List<Invoice> invoices = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            invoices.add(toInvoice(r));
        }
        return ResponseEntity.ok(Map.of(
                "invoices", invoices,
                "count", invoices.size()));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<?> getInvoice(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String invoiceId) {
        Invoice inv;
        try {
            inv = loadInvoice(tenantId, invoiceId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "fatura detayı alınamadı");
        }
        if (inv == null) {
            return error(HttpStatus.NOT_FOUND, "fatura bulunamadı");
        }
        return ResponseEntity.ok(inv);
    }

    // ---------- e-Fatura / e-Arşiv ----------

    @PostMapping("/invoices/{invoiceId}/efatura")
    public ResponseEntity<?> submitEFatura(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @PathVariable String invoiceId,
                                           @RequestBody SubmitEFaturaRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (!"efatura".equals(req.invoiceType()) && !"earsiv".equals(req.invoiceType())) {
            return error(HttpStatus.BAD_REQUEST, "invoice_type zorunludur (efatura, earsiv)");
        }
        if (!TaxCalculator.ALLOWED_VAT_RATES.contains(req.vatRate())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz KDV oranı (izinli: 0, 1, 10, 20)");
        }
        if (req.customerName() == null || req.customerName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "customer_name zorunludur");
        }
        if ("efatura".equals(req.invoiceType()) && (req.customerTaxNo() == null || req.customerTaxNo().isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "e-Fatura için customer_tax_no (VKN) zorunludur");
        }
        if ("earsiv".equals(req.invoiceType())
                && (req.customerTaxNo() == null || req.customerTaxNo().isBlank())
                && (req.customerIdentity() == null || req.customerIdentity().isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "e-Arşiv için customer_tax_no veya customer_identity zorunludur");
        }

        Invoice inv;
        try {
            inv = loadInvoice(tenantId, invoiceId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "fatura yüklenemedi");
        }
        if (inv == null) {
            return error(HttpStatus.NOT_FOUND, "fatura bulunamadı");
        }
        if (!"standard".equals(inv.invoiceType()) && !"none".equals(inv.gibStatus())) {
            return error(HttpStatus.CONFLICT, "fatura zaten e-Fatura/e-Arşiv olarak gönderilmiş");
        }

        TaxBreakdown tax;
        try {
            tax = TaxCalculator.calculateVat(inv.amountTotal(), req.vatRate());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        InvoiceDocument doc = new InvoiceDocument(
                Ulid.generate(),
                inv.number(),
                InvoiceType.from(req.invoiceType()),
                inv.currency(),
                tax.subtotal(), tax.vatRate(), tax.vatAmount(), tax.total(),
                req.customerName(), nz(req.customerTaxNo()), nz(req.customerIdentity()), nz(req.customerAddress()),
                OffsetDateTime.now(ZoneOffset.UTC));

        GIBResponse resp;
        try {
            resp = efatura.send(doc);
        } catch (RuntimeException e) {
            return error(HttpStatus.BAD_GATEWAY, "e-Fatura gönderilemedi");
        }

        try {
            dsl.execute("""
                    UPDATE billing.invoices SET
                        subtotal = ?, vat_rate = ?, vat_amount = ?,
                        invoice_type = ?, customer_name = ?, customer_tax_no = ?,
                        customer_identity = ?, customer_address = ?,
                        gib_status = ?, document_id = ?, gib_response_id = ?, efatura_sent_at = now(),
                        updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    """,
                    tax.subtotal(), tax.vatRate(), tax.vatAmount(), req.invoiceType(),
                    req.customerName(), nz(req.customerTaxNo()), nz(req.customerIdentity()), nz(req.customerAddress()),
                    resp.status().value(), doc.documentId(), resp.responseId(),
                    invoiceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "e-Fatura durumu kaydedilemedi");
        }

        Invoice updated = new Invoice(
                inv.id(), inv.stripeInvoiceId(), inv.number(), inv.status(), inv.amountTotal(), inv.currency(),
                inv.periodStart(), inv.periodEnd(), inv.hostedInvoiceUrl(), inv.invoicePdf(), inv.createdAt(),
                tax.subtotal(), tax.vatRate(), tax.vatAmount(), req.invoiceType(),
                req.customerName(), nz(req.customerTaxNo()), nz(req.customerIdentity()), nz(req.customerAddress()),
                resp.status().value(), doc.documentId(), resp.responseId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoice", updated);
        body.put("gib", resp);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/invoices/{invoiceId}/efatura/xml")
    public ResponseEntity<?> downloadUbl(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String invoiceId) {
        Invoice inv;
        try {
            inv = loadInvoice(tenantId, invoiceId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "fatura yüklenemedi");
        }
        if (inv == null) {
            return error(HttpStatus.NOT_FOUND, "fatura bulunamadı");
        }
        if ("standard".equals(inv.invoiceType()) || inv.documentId() == null || inv.documentId().isBlank()) {
            return error(HttpStatus.NOT_FOUND, "faturanın e-Fatura/e-Arşiv belgesi yok");
        }

        InvoiceDocument doc = new InvoiceDocument(
                inv.documentId(), inv.number(), InvoiceType.from(inv.invoiceType()), inv.currency(),
                inv.subtotal(), inv.vatRate(), inv.vatAmount(), inv.amountTotal(),
                inv.customerName(), inv.customerTaxNo(), inv.customerIdentity(), inv.customerAddress(),
                OffsetDateTime.now(ZoneOffset.UTC));

        String xml;
        try {
            xml = UblTrInvoice.build(doc);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "UBL XML üretilemedi");
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + inv.number() + ".xml\"")
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<?> downloadInvoicePdf(@RequestHeader("X-Tenant-ID") String tenantId,
                                                @PathVariable String invoiceId) {
        Invoice inv;
        try {
            inv = loadInvoice(tenantId, invoiceId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "fatura yüklenemedi");
        }
        if (inv == null) {
            return error(HttpStatus.NOT_FOUND, "fatura bulunamadı");
        }

        InvoiceData data = new InvoiceData(
                inv.number(), inv.status(), inv.invoiceType(), inv.gibStatus(), inv.documentId(),
                inv.currency(), inv.subtotal(), inv.vatRate(), inv.vatAmount(), inv.amountTotal(),
                inv.customerName(), inv.customerTaxNo(), inv.customerIdentity(), inv.customerAddress(),
                parsePeriod(inv.periodStart()), parsePeriod(inv.periodEnd()),
                inv.createdAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : OffsetDateTime.parse(inv.createdAt()));

        byte[] raw;
        try {
            raw = InvoicePdf.render(data);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "fatura PDF üretilemedi");
        }

        String fileName = inv.number() == null || inv.number().isBlank() ? inv.id() : inv.number();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + fileName + ".pdf\"")
                .body(raw);
    }

    // ---------- Portal ----------

    @PostMapping("/portal")
    public ResponseEntity<?> createPortalSession(@RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestBody PortalRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        String returnUrl = req.returnUrl() == null || req.returnUrl().isBlank() ? "/" : req.returnUrl();

        String url;
        try {
            url = stripe.createPortalSession(tenantId, returnUrl);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "portal oturumu oluşturulamadı");
        }

        return ResponseEntity.ok(Map.of("url", url));
    }

    // ---------- yardımcılar ----------

    private static final String INVOICE_SELECT = """
            SELECT id, stripe_invoice_id, COALESCE(number, '') AS number, status, amount_total, currency,
                   period_start, period_end, COALESCE(hosted_invoice_url, '') AS hosted_invoice_url,
                   COALESCE(invoice_pdf, '') AS invoice_pdf, created_at,
                   subtotal, vat_rate, vat_amount, invoice_type,
                   COALESCE(customer_name, '') AS customer_name, COALESCE(customer_tax_no, '') AS customer_tax_no,
                   COALESCE(customer_identity, '') AS customer_identity, COALESCE(customer_address, '') AS customer_address,
                   gib_status, COALESCE(document_id, '') AS document_id, COALESCE(gib_response_id, '') AS gib_response_id
            FROM billing.invoices
            """;

    /** Kiracıya ait bir faturayı (vergi/e-Fatura alanları dahil) getirir — Go {@code loadInvoice} portu. */
    private Invoice loadInvoice(String tenantId, String invoiceId) {
        Map<String, Object> row = map(INVOICE_SELECT + " WHERE id = ? AND tenant_id = ?", invoiceId, tenantId);
        return row == null ? null : toInvoice(row);
    }

    private static Invoice toInvoice(Map<String, Object> r) {
        return new Invoice(
                str(r.get("id")), str(r.get("stripe_invoice_id")), str(r.get("number")), str(r.get("status")),
                num(r.get("amount_total")), str(r.get("currency")),
                ts(r.get("period_start")), ts(r.get("period_end")),
                str(r.get("hosted_invoice_url")), str(r.get("invoice_pdf")), ts(r.get("created_at")),
                num(r.get("subtotal")), (int) num(r.get("vat_rate")), num(r.get("vat_amount")),
                str(r.get("invoice_type")),
                str(r.get("customer_name")), str(r.get("customer_tax_no")), str(r.get("customer_identity")),
                str(r.get("customer_address")), str(r.get("gib_status")), str(r.get("document_id")),
                str(r.get("gib_response_id")));
    }

    private static OffsetDateTime parsePeriod(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
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

    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
