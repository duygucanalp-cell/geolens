package dev.geolens.billing.web;

import dev.geolens.billing.service.BillingService;
import dev.geolens.billing.service.BillingServiceException;
import dev.geolens.billing.service.BillingService.InvoicePdfDocument;
import dev.geolens.billing.service.BillingService.UblDocument;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Faturalama REST controller'ı — Go {@code billing.handler} portu (FR-A6, HT2 multi-currency).
 * <p>Route'lar (go cmd/api): POST /v1/billing/checkout, POST /v1/billing/webhook,
 * GET /v1/billing/subscription, GET /v1/billing/invoices, GET /v1/billing/invoices/{invoiceId},
 * POST /v1/billing/invoices/{invoiceId}/efatura, GET .../efatura/xml (UBL-TR),
 * GET .../pdf, POST /v1/billing/portal.
 * <p>İş mantığı {@link BillingService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/billing")
public class BillingController {

    private static final Set<String> VALID_TIERS = Set.of("pro", "business", "enterprise");

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
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

        return ResponseEntity.ok(service.createCheckoutSession(tenantId, req, currency));
    }

    // ---------- Webhook ----------

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestHeader(value = "Stripe-Signature", required = false) String signature,
                                           @RequestBody String body) {
        return ResponseEntity.ok(service.handleWebhook(signature, body));
    }

    // ---------- Abonelik ----------

    @GetMapping("/subscription")
    public ResponseEntity<?> getSubscription(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.getSubscription(tenantId));
    }

    // ---------- Faturalar ----------

    @GetMapping("/invoices")
    public ResponseEntity<?> listInvoices(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listInvoices(tenantId));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<?> getInvoice(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String invoiceId) {
        return ResponseEntity.ok(service.getInvoice(tenantId, invoiceId));
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

        return ResponseEntity.ok(service.submitEFatura(tenantId, invoiceId, req));
    }

    @GetMapping("/invoices/{invoiceId}/efatura/xml")
    public ResponseEntity<?> downloadUbl(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String invoiceId) {
        UblDocument doc = service.downloadUbl(tenantId, invoiceId);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "xml", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.fileName() + ".xml\"")
                .body(doc.xml());
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<?> downloadInvoicePdf(@RequestHeader("X-Tenant-ID") String tenantId,
                                                @PathVariable String invoiceId) {
        InvoicePdfDocument doc = service.downloadInvoicePdf(tenantId, invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + doc.fileName() + ".pdf\"")
                .body(doc.pdf());
    }

    // ---------- Portal ----------

    @PostMapping("/portal")
    public ResponseEntity<?> createPortalSession(@RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestBody PortalRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        String returnUrl = req.returnUrl() == null || req.returnUrl().isBlank() ? "/" : req.returnUrl();
        return ResponseEntity.ok(service.createPortalSession(tenantId, req, returnUrl));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    @ExceptionHandler(BillingServiceException.class)
    public ResponseEntity<ApiError> handleService(BillingServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
