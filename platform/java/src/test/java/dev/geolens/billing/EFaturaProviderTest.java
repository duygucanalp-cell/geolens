package dev.geolens.billing;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go billing/efatura_test.go parity testleri — sağlayıcı modları ve UBL-TR üretimi. */
class EFaturaProviderTest {

    @Test
    void mockMode() {
        assertEquals("mock", EFaturaProvider.create("").mode());
        assertEquals("mock", EFaturaProvider.create("mock").mode());
    }

    @Test
    void gibMode() {
        assertEquals("gib", EFaturaProvider.create("gib").mode());
    }

    @Test
    void mockSend() {
        EFaturaProvider p = EFaturaProvider.create("mock");
        GIBResponse resp = p.send(doc("123e4567-e89b-12d3-a456-426614174000", "INV-2026-001", InvoiceType.EFATURA));
        assertEquals(GIBStatus.ACCEPTED, resp.status());
        assertFalse(resp.responseId().isBlank());
    }

    @Test
    void buildUblEfatura() {
        InvoiceDocument doc = new InvoiceDocument(
                "123e4567-e89b-12d3-a456-426614174000", "INV-2026-001", InvoiceType.EFATURA,
                "TRY", 100000, 20, 20000, 120000,
                "Acme Bilişim A.Ş.", "1234567890", "", "Levent Mah. Büyükdere Cad. No:1 İstanbul",
                OffsetDateTime.of(2026, 8, 3, 10, 30, 0, 0, ZoneOffset.UTC));

        String s = UblTrInvoice.build(doc);
        assertFalse(s.isEmpty());

        for (String want : new String[]{
                "<Invoice", "TR1.2", "TICARIFATURA", "123e4567-e89b-12d3-a456-426614174000",
                "2026-08-03", "SATIS", "TRY", "VKN", "1234567890", "KDV",
                "1000.00", "200.00", "1200.00", "Acme Bilişim A.Ş."}) {
            assertTrue(s.contains(want), "üretilen xml '" + want + "' içermiyor:\n" + s);
        }
    }

    @Test
    void buildUblEArsivTckn() {
        InvoiceDocument doc = new InvoiceDocument(
                "abc-123", "INV-2026-002", InvoiceType.EARSIV,
                "TRY", 5000, 10, 500, 5500,
                "Ali Yılmaz", "", "11122233344", "",
                OffsetDateTime.now(ZoneOffset.UTC));

        String s = UblTrInvoice.build(doc);
        for (String want : new String[]{"EARSIV", "TCKN", "11122233344"}) {
            assertTrue(s.contains(want), "üretilen xml '" + want + "' içermiyor:\n" + s);
        }
    }

    @Test
    void buildUblValidation() {
        assertThrows(IllegalArgumentException.class, () -> UblTrInvoice.build(null));
        assertThrows(IllegalArgumentException.class,
                () -> UblTrInvoice.build(new InvoiceDocument("", "", InvoiceType.EFATURA,
                        "TRY", 0, 0, 0, 0, "", "", "", "", OffsetDateTime.now(ZoneOffset.UTC))));
    }

    @Test
    void ublWellFormed() {
        String s = UblTrInvoice.build(doc("d1", "INV-1", InvoiceType.EFATURA));
        assertTrue(s.startsWith("<?xml"), "xml başlık eksik");
    }

    private static InvoiceDocument doc(String docId, String number, InvoiceType type) {
        return new InvoiceDocument(docId, number, type, "TRY", 100, 20, 20, 120,
                "", "", "", "", OffsetDateTime.now(ZoneOffset.UTC));
    }
}
