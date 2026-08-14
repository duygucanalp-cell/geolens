package dev.geolens.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go billing/tax_test.go parity testleri — KDV hesaplama. */
class TaxCalculatorTest {

    @Test
    void validRates() {
        assertVat(10000, 20, 2000, 12000, "yüzde 20");
        assertVat(10000, 10, 1000, 11000, "yüzde 10");
        assertVat(10000, 1, 100, 10100, "yüzde 1");
        assertVat(10000, 0, 0, 10000, "yüzde 0");
        assertVat(995, 20, 199, 1194, "yüzde 20 kuruş yuvarlama"); // 199.0 → 199
    }

    private static void assertVat(long subtotal, int rate, long wantVat, long wantTotal, String name) {
        TaxBreakdown got = TaxCalculator.calculateVat(subtotal, rate);
        assertEquals(wantVat, got.vatAmount(), name + " VATAmount");
        assertEquals(wantTotal, got.total(), name + " Total");
        assertEquals(subtotal, got.subtotal(), name + " Subtotal korunmadı");
    }

    @Test
    void invalidRate() {
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.calculateVat(10000, 15));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.calculateVat(10000, -1));
    }

    @Test
    void negativeSubtotal() {
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.calculateVat(-100, 20));
    }
}
