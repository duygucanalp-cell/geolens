package dev.geolens.billing;

import java.time.OffsetDateTime;

/** e-Fatura/e-Arşiv için GİB'e gönderilen fatura içeriği — Go {@code billing.InvoiceDocument} portu. */
public record InvoiceDocument(
        String documentId,
        String number,
        InvoiceType invoiceType,
        String currency,
        long subtotal,
        int vatRate,
        long vatAmount,
        long total,
        String customerName,
        String customerTaxNo,
        String customerIdentity,
        String customerAddress,
        OffsetDateTime issueDate) {
}
