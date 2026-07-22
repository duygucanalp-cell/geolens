# Definition of Done (Tamamlanma Tanımı)

| Alan | Değer |
|---|---|
| Doküman ID | 10-engineering/definition-of-done |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 10-engineering/*, 0204, 0205, 0404 |

---

## 1. Amaç

Bu doküman GeoLens Platform için Definition of Done (DoD) kriterlerini tanımlar. Bir işin (feature/fix/story) tamamlanmış sayılması için gereken tüm koşulları listeler.

---

## 2. DoD Kriterleri

### Kod

| # | Kriter | Kontrol |
|:-:|--------|:-------:|
| 1 | Kod yazıldı ve branch'e push edildi | ✅ |
| 2 | Tüm birim testler geçiyor | CI |
| 3 | Kod standardına uygun (gofmt, lint) | CI |
| 4 | Yeni kod için test eklendi (birim/entegrasyon) | ✅ |
| 5 | Tüm testler yeşil (CI) | CI |

### Dokümantasyon

| # | Kriter | Kontrol |
|:-:|--------|:-------:|
| 6 | İlgili doküman güncellendi (varsa) | ✅ |
| 7 | API değişikliği varsa openapi.yaml güncellendi | ✅ |
| 8 | Migration varsa SQL dosyası eklendi | ✅ |

### Review

| # | Kriter | Kontrol |
|:-:|--------|:-------:|
| 9 | En az 1 code review'den geçti | ✅ |
| 10 | Tüm review yorumları yanıtlandı | ✅ |
| 11 | Gerekli güvenlik review'i yapıldı | ✅ |

### Dağıtım

| # | Kriter | Kontrol |
|:-:|--------|:-------:|
| 12 | Staging ortamına deploy edildi | ✅ |
| 13 | Smoke test geçti | CI |
| 14 | E2E testler (varsa) geçti | CI |

---

## 3. Sprint DoD

Sprint sonunda ayrıca:

| # | Kriter |
|:-:|--------|
| 15 | Sprint içindeki tüm story'ler bireysel DoD'u karşılıyor |
| 16 | Doküman-kod senkronu kontrol edildi |
| 17 | Açık bug sayısı sıfır (veya yönetilebilir) |
| 18 | Performans hedefleri karşılanıyor (NFR-9) |

---

## 4. MVP DoD (Pilot Çıkış Kapısı)

MVP için ek DoD kriterleri (0205 §7 ile uyumlu):

| # | Kriter |
|:-:|--------|
| 19 | Sert kural ihlali sıfır (NFR-1, NFR-6, NFR-7) |
| 20 | Pilot kiracılardan referans sinyali alındı |
| 21 | Güvenlik kapanışı tamam (açık kritik/yüksek bulgu sıfır) |

---

## 5. DoD İhlal Süreci

| Durum | Aksiyon |
|:-----:|---------|
| Eksik test | Test eklenir, CI tekrar çalıştırılır |
| Eksik doküman | Doküman eklenir, PR güncellenir |
| CI hatası | Hata düzeltilir, yeniden gönderilir |
| Review eksik | Reviewer atanır, review tamamlanır |

> DoD karşılanmadan hiçbir değişiklik develop veya main'e merge edilmez.

---

## Kaynaklar

- 10-engineering/code-review — review süreci
- 10-engineering/testing — test stratejisi
- 0205 MVP — pilot çıkış kapısı kriterleri
- 0204 PRD — NFR gereksinimleri
- 09-devops/ci-cd — CI pipeline ve kapılar
- archive/avip-v1/0401-development-process.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 21 maddelik DoD (14 temel + 4 sprint + 3 MVP), ihlal süreci. |
