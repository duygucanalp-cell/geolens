# Kimlik Doğrulama (Authentication)

| Alan | Değer |
|---|---|
| Doküman ID | 07-api/authentication |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 07-api/rest-api, 0508, 0310, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform kimlik doğrulama mekanizmalarını tanımlar: oturum yönetimi, token yapısı, CSRF koruması ve API anahtarları.

---

## 2. Kimlik Doğrulama Yöntemleri

| Yöntem | MVP | Açıklama |
|--------|:---:|----------|
| E-posta + parola | ✅ | Kayıt ve giriş |
| Oturum çerezi | ✅ | httpOnly, Secure, SameSite=Lax |
| API anahtarı | 🔴 | HT1 (FR-F6) |
| SSO/SAML | 🔴 | Kurumsal Kapı (FR-A4) |
| MFA | 🔴 | HT1 (yönetici rolü) |

---

## 3. Oturum Yönetimi

| Özellik | Değer |
|---------|-------|
| Süre (mutlak) | 7 gün |
| Süre (kayan) | 2 saat |
| Depolama | Sunucu tarafı (Redis) |
| Çerez | httpOnly, Secure, SameSite=Lax |
| CSRF | Senkronizasyon token deseni |

### Oturum Yaşam Döngüsü

```
Giriş → Oturum oluştur → Çerez dön
İstek → Çerez doğrula → Süre yenile
Çıkış → Oturum sil → Çerez temizle
Parola değişimi → Tüm oturumları düşür
```

---

## 4. CSRF Koruması

| Metot | Korumalı mı? |
|:-----:|:------------:|
| GET, HEAD, OPTIONS | Hayır (güvenli) |
| POST, PUT, PATCH, DELETE | Evet (CSRF token zorunlu) |

CSRF token, oturum çereziyle birlikte ayrı bir header'da taşınır: `X-CSRF-Token`.

---

## 5. API Anahtarları (HT1)

Dış okuma API'si (FR-F6) için kiracı başına API anahtarları:

| Özellik | Değer |
|---------|-------|
| Format | `gl_{tenant_id}_{random}` |
| Kapsam | Salt okunur (skor, trend, rapor) |
| Taşıma | `Authorization: Bearer {key}` |
| Rate limit | Ayrı limit sınıfı |
| Yönetim | Kiracı yöneticisi üretebilir/silebilir |

---

## 6. Parola Politikası

| Kural | Değer |
|:----:|-------|
| Minimum uzunluk | 8 karakter |
| Karma | Argon2id |
| İhlal kontrolü | Bilinen ihlaller listesi |
| Giriş hız sınırı | 5 deneme/dk |
| Hesap kilidi | 10 başarısız deneme → 15 dk kilit |

---

## 7. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-27 | MFA (çok faktörlü kimlik doğrulama): HT1'de yönetici rolü için zorunlu. MVP'de MFA yok (authentication.md §2 ile uyumlu). | AVIP 0310 O-1 (TL 21.07.2026) |
| D-28 | Oturum süreleri: mutlak 7 gün, kayan 2 saat. Bu doküman §3 ile birebir uyumlu. | AVIP 0310 O-2 (TL 21.07.2026) |
| D-29 | Üyelik erişim kapsamı: çalışma alanı düzeyinde rol tabanlı erişim. RBAC modeli authentication.md'de embed. | AVIP 0302 O-1 (TL 21.07.2026) |
| D-83 | Derin bağlantı token ömrü [K]: 7 gün + tek kullanım. Oturum token'ları için de geçerli süre sınırı. | AVIP 0306 O-2 (TL 21.07.2026) |

---

## Kaynaklar

- 07-api/rest-api — auth uçları
- 0508 Security — güvenlik mimarisi
- 0310 Security — tehdit modeli, RBAC, sır yönetimi
- 0204 PRD — FR-A1, FR-A3, FR-A4

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: auth yöntemleri, oturum yönetimi, CSRF, API anahtarları, parola politikası. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-27 (MFA), D-28 (oturum süreleri), D-29 (üyelik erişim), D-83 (token ömrü). |
