# 1006 · Branching Stratejisi

| Alan | Değer |
|---|---|---|
| Doküman ID | 1006 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 1003, 1004, 0901 |

---

## 1. Amaç

Bu doküman GeoLens Platform branch adlandırma kurallarını ve branch yönetimini tanımlar. 1003 ile birlikte okunur.

---

## 2. Branch Adlandırma

| Branch Türü | Desen | Örnek |
|:-----------:|:-----:|-------|
| Feature | `feature/{kısa-açıklama}` | `feature/chatgpt-adapter` |
| Fix | `fix/{kısa-açıklama}` | `fix/rls-policy-error` |
| Release | `release/v{major}.{minor}.{patch}` | `release/v1.0.0` |
| Hotfix | `hotfix/{kısa-açıklama}` | `hotfix/auth-timeout` |
| Chore | `chore/{kısa-açıklama}` | `chore/update-deps` |

### Kurallar

| Kural | Açıklama |
|:-----:|----------|
| **Kısa açıklama** | 2-4 kelime, kebab-case, İngilizce |
| **Sayı içermez** | Issue/PR numarası branch adında olmaz |
| **Küçük harf** | Tüm branch adları küçük harf |
| **Tire** | Kelime ayracı olarak tire (-) kullanılır |

---

## 3. Branch Yaşam Döngüsü

| Aşama | Kural |
|:-----:|-------|
| **Oluşturma** | develop'den, güncel (rebased) |
| **Geliştirme** | Sık commit, erken push |
| **Rebase** | Merge öncesi develop ile rebase |
| **PR** | develop'e squash merge |
| **Silme** | Merge sonrası branch silinir |
| **Hotfix** | main'den, main + develop'e merge |

---

## 4. Branch Korumaları (GitHub)

| Branch | Korumalar |
|:------:|-----------|
| **main** | Doğrudan push yasak, PR zorunlu, review zorunlu, CI geçmeli |
| **develop** | Doğrudan push yasak, PR zorunlu, en az 1 review, CI geçmeli |

---

## 5. Sürüm Numaralandırma (SemVer)

`v{major}.{minor}.{patch}`

| Bileşen | Ne zaman artar? |
|:-------:|-----------------|
| **major** | Kırıcı API değişikliği |
| **minor** | Yeni özellik (geri uyumlu) |
| **patch** | Hata düzeltmesi (geri uyumlu) |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-82** | **1.0.0 = GA:** Pilot çıkış kapısı sonrası. Pilot dönemi 0.x. PO 21.07.2026. | AVIP 0406 O-1 |
| **D-77** | **Tren günü:** Cuma tren / Pazartesi terfi. TL 21.07.2026. | AVIP 0406 O-2 |
| **D-57** | **Dondurma pencereleri:** Kapı değerlendirme haftaları + yılbaşı (2 hafta). PO+TL 21.07.2026. | AVIP 0406 O-4 |

---

## Kaynaklar

- 1003 — git akışı detayı
- 1004 — PR süreci
- 0901 — CI kapıları
- archive/avip-v1/0406-release-versioning.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: branch adlandırma, yaşam döngüsü, branch korumaları, SemVer. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-82 (1.0.0=GA), D-77 (tren günü), D-57 (dondurma). Devralınan Kararlar eklendi. |
