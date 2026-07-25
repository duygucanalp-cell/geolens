# ADR-010 · Güvenlik Sertleştirme Kararları (Dilim 4)

| Alan | Değer |
|---|---|
| ADR ID | ADR-010 |
| Durum | Kabul |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | ADR-012, project-plan §7, 0508, 0606 |

---

## Bağlam

Dilim 4 (H13–H16) kapsamında pilot çıkış kapısı için güvenlik sertleştirmesi yapılması gerekmiştir. ADR-012'de tanımlı 7 kriterden Kriter 7 (Güvenlik Kapanışı) aşağıdaki eksikleri kapatmayı hedeflemiştir:

- SOPS+Age sır yönetimi entegrasyonu
- KVKK veri silme endpoint'i
- Kripto-silme altyapısı (S3 AES-256-GCM)
- RBAC matrisi testleri + izolasyon negatif testleri
- Güvenlik header'ları doğrulaması

---

## Kararlar

### K1: SOPS+Age Sır Yönetimi

| Öngörü | Gerçekleşen |
|--------|-------------|
| `.env` dosyasında düz metin sırlar | `.env.secrets.enc` — Age şifreleme + `docker/entrypoint.sh` ile decrypt |

**Gerekçe:** SOPS+Age, GPG'ye kıyasla daha basit anahtar yönetimi ve CI/CD uyumluluğu sağlar. `make encrypt-secrets` / `make decrypt-secrets` / `make edit-secrets` komutlarıyla geliştirici deneyimi korunur.

### K2: KVKK Veri Silme Endpoint'i

| Öngörü | Gerçekleşen |
|--------|-------------|
| Kullanıcı verisi manuel silinir | `POST /v1/privacy/delete` — `internal/privacy/handler.go` |

**Gerekçe:** KVKK/GDPR uyumu için kiracının tüm verisini (identity, config, measure, governance, delivery tabloları) kaskad silen bir endpoint gerekliydi. Silme işlemi asynchronous çalışır, audit log'a kaydedilir.

### K3: Kripto-Silme Altyapısı (S3 AES-256-GCM)

| Öngörü | Gerçekleşen |
|--------|-------------|
| S3'te düz metin depolama | AES-256-GCM ile sunucu tarafı şifreleme (SSE-C) |

**Gerekçe:** NFR-5 (beklemede şifreleme) gereksinimini karşılamak için S3'e yazılan tüm nesneler (ham yanıtlar, PDF raporlar) AES-256-GCM ile şifrelenir. Anahtar yönetimi SOPS+Age üzerinden yapılır.

### K4: RBAC + İzolasyon Negatif Testleri

| Öngörü | Gerçekleşen |
|--------|-------------|
| RBAC testleri yok | Negatif test senaryoları: viewer editor olmayan kaynağa erişemez, tenant-A tenant-B verisini göremez |

**Gerekçe:** Multi-tenancy izolasyonunun doğrulanması için RLS politikalarının delinemediği negatif testlerle kanıtlanmalıdır.

---

## Sonuçlar

- Güvenlik kapanışı ADR-012 Kriter 7'yi yeşile çekmiştir
- SOPS+Age ile sır yönetimi standartlaşmıştır
- KVKK silme endpoint'i ile yasal uyum sağlanmıştır
- Kripto-silme ile NFR-5 karşılanmıştır
- Negatif testler RBAC+RLS güvencesini kanıtlamıştır

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: Dilim 4 güvenlik sertleştirme kararları |
