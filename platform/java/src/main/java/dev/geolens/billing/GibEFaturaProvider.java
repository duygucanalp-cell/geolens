package dev.geolens.billing;

/**
 * Üretim GİB entegrasyonu için iskelet — Go {@code gibEFaturaProvider} portu.
 * <p>GİB (Gelir İdaresi Başkanlığı) web servis kimlik bilgileri (kullanıcı, şifre, mali mühür)
 * sağlanmadan gerçek gönderim yapılamaz; bu nedenle net bir hata döndürür.
 */
final class GibEFaturaProvider implements EFaturaProvider {

    @Override
    public String mode() {
        return "gib";
    }

    @Override
    public GIBResponse send(InvoiceDocument doc) {
        throw new IllegalStateException("gerçek GİB entegrasyonu için mali mühür ve web servis kimlik bilgileri gerekir");
    }
}
