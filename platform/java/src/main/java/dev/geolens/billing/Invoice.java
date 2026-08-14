package dev.geolens.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fatura modeli — Go {@code billing.Invoice} portu (FR-A6).
 * <p>Tüm para tutarları kuruş cinsindendir. Tarih alanları RFC3339 string olarak taşınır
 * (Go tarafında {@code time.Time} → JSON'da RFC3339 otomatik biçimlenir).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Invoice(
        @JsonProperty("id") String id,
        @JsonProperty("stripe_invoice_id") String stripeInvoiceId,
        @JsonProperty("number") String number,
        @JsonProperty("status") String status,
        @JsonProperty("amount_total") long amountTotal,
        @JsonProperty("currency") String currency,
        @JsonProperty("period_start") String periodStart,
        @JsonProperty("period_end") String periodEnd,
        @JsonProperty("hosted_invoice_url") String hostedInvoiceUrl,
        @JsonProperty("invoice_pdf") String invoicePdf,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("subtotal") long subtotal,
        @JsonProperty("vat_rate") int vatRate,
        @JsonProperty("vat_amount") long vatAmount,
        @JsonProperty("invoice_type") String invoiceType,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("customer_tax_no") String customerTaxNo,
        @JsonProperty("customer_identity") String customerIdentity,
        @JsonProperty("customer_address") String customerAddress,
        @JsonProperty("gib_status") String gibStatus,
        @JsonProperty("document_id") String documentId,
        @JsonProperty("gib_response_id") String gibResponseId) {
}
