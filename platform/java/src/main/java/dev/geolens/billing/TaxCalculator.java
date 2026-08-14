package dev.geolens.billing;

import java.util.List;

/**
 * Türk KDV hesaplama — Go {@code billing.tax} portu (FR-A6).
 * <p>İzinli oranlar 2019'dan itibaren Türkiye'de yürürlükte olanlardır (0, 1, 10, 20).
 * KDV tutarı en yakın kuruşa yuvarlanır: {@code (subtotal * rate + 50) / 100}.
 */
public final class TaxCalculator {

    private TaxCalculator() {
    }

    /** e-Fatura/e-Arşiv faturalarında kullanılabilir KDV oranları. */
    public static final List<Integer> ALLOWED_VAT_RATES = List.of(0, 1, 10, 20);

    /**
     * Net ara toplam (kuruş) üzerinden KDV kırılımını hesaplar.
     *
     * @param subtotal net ara toplam (kuruş)
     * @param vatRate  izinli KDV oranı (0, 1, 10, 20)
     * @throws IllegalArgumentException geçersiz oran veya negatif ara toplam
     */
    public static TaxBreakdown calculateVat(long subtotal, int vatRate) {
        if (!ALLOWED_VAT_RATES.contains(vatRate)) {
            throw new IllegalArgumentException("geçersiz KDV oranı: " + vatRate + " (izinli: 0, 1, 10, 20)");
        }
        if (subtotal < 0) {
            throw new IllegalArgumentException("ara toplam negatif olamaz: " + subtotal);
        }

        long vat = (subtotal * vatRate + 50) / 100;
        return new TaxBreakdown(subtotal, vatRate, vat, subtotal + vat);
    }
}
