package dev.geolens.billing;

/**
 * Faturaları e-Fatura/e-Arşiv olarak GİB'e gönderen sağlayıcı — Go {@code billing.EFaturaProvider} portu.
 * <p>Stripe mock deseniyle aynı: gerçek GİB entegrasyonu kimlik bilgisi gerektirir,
 * sandbox/mock modda {@link #send} simüle edilmiş kabul döndürür.
 */
public interface EFaturaProvider {

    GIBResponse send(InvoiceDocument doc);

    /** Sağlayıcının çalışma modu: {@code "mock"} veya {@code "gib"}. */
    String mode();

    /** Yapılandırılan moda göre sağlayıcı döndürür (Go {@code NewEFaturaProvider}). */
    static EFaturaProvider create(String mode) {
        if (mode == null || mode.isBlank() || "mock".equals(mode)) {
            return new MockEFaturaProvider();
        }
        return new GibEFaturaProvider();
    }
}
