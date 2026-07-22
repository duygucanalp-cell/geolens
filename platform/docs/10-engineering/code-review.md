# Code Review (Kod İnceleme)

| Alan | Değer |
|---|---|
| Doküman ID | 10-engineering/code-review |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 10-engineering/*, 09-devops/ci-cd, 0403 |

---

## 1. Amaç

Bu doküman GeoLens Platform code review sürecini tanımlar. Kalite, güvenlik ve tutarlılık için her değişiklik en az bir review'den geçmelidir.

---

## 2. Review Zorunluluğu

| Branch Türü | Review Sayısı | İstisna |
|:-----------:|:-------------:|---------|
| **feature → develop** | En az 1 | Acil durum (sonra review) |
| **fix → develop** | En az 1 | — |
| **release → main** | En az 2 | — |
| **hotfix → main** | En az 1 (hızlı) | TL onayı ile |

---

## 3. Review Kontrol Listesi

### Mimari

- [ ] Bağımlılık kurallarına (D1-D7) uygun mu?
- [ ] Bağlam sınırları korunuyor mu?
- [ ] Yeni bağımlılık eklenmiş mi (gerekçeli mi)?

### Kod Kalitesi

- [ ] DRY/KISS prensiplerine uygun mu?
- [ ] Hata yönetimi doğru mu?
- [ ] Loglama yeterli mi (correlation_id ile)?
- [ ] Test eklenmiş mi?

### Güvenlik

- [ ] RLS politikaları atlanmış mı?
- [ ] Sır/anahtar koda gömülmüş mü?
- [ ] Girdi doğrulaması yapılmış mı?

---

## 4. Review Süreci

```
Yazar → PR oluşturur → CI geçer → Reviewer atanır
    → Review yorumları → Yazar düzeltir
    → Onay → Merge (squash) → Branch silinir
```

| Süreç | Beklenti |
|:-----:|----------|
| İlk yanıt | <4 iş saati |
| Review tamamlama | <24 iş saati |
| Düzeltme süresi | <2 iş günü |
| Toplam PR süresi | <3 iş günü |

---

## 5. Review Türleri

| Tür | Açıklama |
|:---:|----------|
| **Tam review** | Tüm kontrol listesi uygulanır |
| **Hızlı review** | Kritik olmayan değişiklikler (doküman, konfigürasyon) |
| **Güvenlik review** | Güvenlikle ilgili değişiklikler (yetkilendirme, şifreleme) |

---

## 6. CODEOWNERS

Her bağlamın birincil sahibi, değişiklikler için otomatik reviewer olarak atanır:

| Bağlam | Sahip |
|--------|-------|
| internal/identity, internal/governance, platform/* | Backend #1 |
| internal/config, internal/insight, internal/delivery | Backend #2 |
| internal/measure, internal/engines | Siz (TL+CEO) |

---

## Kaynaklar

- 10-engineering/git-flow — PR süreci
- 10-engineering/definition-of-done — DoD listesi
- 0502 Service Architecture — CODEOWNERS eşlemesi
- 09-devops/ci-cd — CI kapıları

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: review zorunluluğu, kontrol listesi, süreç, review türleri, CODEOWNERS. |
