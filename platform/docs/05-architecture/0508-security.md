# 0508 · Güvenlik Mimarisi (Security Architecture)

| Alan | Değer |
|---|---|
| Doküman ID | 0508 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0507, 0501, 0310, 0204, 0405 |

---

## 1. Amaç

Bu doküman GeoLens Platform'un güvenlik mimarisini tanımlar: tehdit modeli, kimlik doğrulama, yetkilendirme, veri koruma ve sır yönetimi.

---

## 2. Tehdit Modeli

| Tehdit | Savunma |
|--------|---------|
| Kiracılar arası veri sızıntısı | 5 katmanlı izolasyon + negatif testler |
| Yetki yükseltme | RBAC + sunucu tarafı zorlaması |
| Hesap ele geçirme | Parola politikası, hız sınırı, oturum sertleştirme |
| Motor anahtarları sızması | Kasa yönetimi, log hijyeni |
| Denetim izi tahrifi | Yalnız-ekleme + karma zinciri |
| API kötüye kullanımı | Hız sınırı + kota + bütçe tavanı |

---

## 3. Kimlik Doğrulama

| Yöntem | MVP | Açıklama |
|--------|:---:|----------|
| E-posta + parola | ✅ | bcrypt/argon2, hız sınırlı giriş |
| Oturum çerezi | ✅ | httpOnly, Secure, SameSite=Lax |
| CSRF token | ✅ | Durum değiştiren isteklerde |
| Parola sıfırlama | ✅ | Tek kullanımlık, kısa ömürlü token |
| SSO/SAML | 🔴 | Kurumsal kapı (FR-A4) |
| MFA | 🔴 | HT1 (yönetici rolü) |

---

## 4. RBAC Modeli

| Rol | Yetkiler |
|:---:|----------|
| **Yönetici** | Tüm yazma işlemleri, üye yönetimi, yapılandırma, çalışma alanı açma/arşivleme |
| **Üye** | Okuma (skor/trend/rapor/öneri), ölçüm tetikleme, öneri işaretleme, yapılandırma (marka/prompt/eşik/kanal) |

> MVP'de iki rol (Yönetici/Üye). Editör ve İzleyici rolleri HT1'de değerlendirilecek (0310 §4).

---

## 5. Veri Koruması

| Alan | Yöntem |
|:----:|--------|
| Aktarım (TLS) | TLS 1.3 tüm uç noktalar |
| Bekleme (DB) | Disk şifreleme |
| Bekleme (S3) | Sunucu tarafı şifreleme |
| Ham arşiv | Kiracı bazlı zarf anahtarı |
| KVKK silme | Anonimleştirme (kullanıcı) / kripto-silme (veri) |

---

## 6. Sır Yönetimi

| Sır Sınıfı | Rotasyon |
|:----------:|:--------:|
| Sağlayıcı API anahtarları | Kadanslı, çift anahtar penceresi |
| Oturum imza anahtarları | Çift anahtar kabul |
| Webhook HMAC sırları | Kiracı tetikli + platform kadansı |
| Zarf anahtarları | Rotasyon yok (yalnız imha) |

Tüm sırlar kasa/ortam kaynaklı; koda, loga veya hata mesajına giremez.

---

## Kaynaklar

- 0507 Multi-Tenancy — izolasyon katmanları
- 0501 System Architecture — middleware zinciri
- 0310 Security — RBAC, denetim izi, doğrulama çerçevesi
- 0204 PRD — NFR-1..NFR-6, NFR-12
- archive/avip-v1/0310-security-multi-tenancy.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: tehdit modeli, auth yöntemleri, RBAC, veri koruma, sır yönetimi. |
