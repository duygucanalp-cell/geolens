package dev.geolens.billing.web;

/** e-Fatura/e-Arşiv gönderim isteği — Go {@code submitEFaturaRequest} portu. */
public record SubmitEFaturaRequest(
        String invoiceType,
        int vatRate,
        String customerName,
        String customerTaxNo,
        String customerIdentity,
        String customerAddress) {
}
