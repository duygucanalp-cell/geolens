package dev.geolens.billing;

/** KDV hesaplama sonucu — Go {@code billing.TaxBreakdown} portu (tüm tutarlar kuruş). */
public record TaxBreakdown(long subtotal, int vatRate, long vatAmount, long total) {
}
