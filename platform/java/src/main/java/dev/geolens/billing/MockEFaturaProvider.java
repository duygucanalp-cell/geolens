package dev.geolens.billing;

import dev.geolens.util.Ulid;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Geliştirme/test ortamları için simüle edilmiş GİB sağlayıcısı — Go {@code mockEFaturaProvider} portu. */
final class MockEFaturaProvider implements EFaturaProvider {

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public GIBResponse send(InvoiceDocument doc) {
        return new GIBResponse(
                GIBStatus.ACCEPTED,
                "gib_" + Ulid.generate(),
                "GİB Entegrasyon Servisi: fatura kabul edildi (mock mod)",
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
