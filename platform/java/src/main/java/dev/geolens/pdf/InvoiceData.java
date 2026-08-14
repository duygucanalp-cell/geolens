package dev.geolens.pdf;

import java.time.OffsetDateTime;

/**
 * Türkçe fatura PDF şablonu için gerekli alanlar — Go {@code pdf.InvoiceData} portu.
 * Tutarlar kuruş cinsindendir; PDF'te TL olarak biçimlendirilir.
 */
public record InvoiceData(
        String number,
        String status,
        String invoiceType,
        String gibStatus,
        String documentId,
        String currency,
        long subtotal,
        int vatRate,
        long vatAmount,
        long total,
        String customerName,
        String customerTaxNo,
        String customerIdentity,
        String customerAddress,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        OffsetDateTime createdAt) {
}
