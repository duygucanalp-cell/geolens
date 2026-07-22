# 0413 · Öneri Motoru (Recommendation Engine)

| Alan | Değer |
|---|---|
| Doküman ID | 0413 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0412, 0309, 0204, 0302 |

---

## 1. Amaç

Bu doküman, GeoLens öneri motorunun tasarımını tanımlar. Öneri motoru, skor ve bulgulardan, kanıt derecesi etiketli, motor politikalarına uygun aksiyon önerileri üretir.

---

## 2. Öneri Türleri

| Tür | Kanıt Derecesi | Açıklama |
|:---:|:--------------:|----------|
| **Deneysel** | Gözleme dayalı | AI yanıtlarında markanın görülme sıklığı, sıralaması |
| **Korelasyonel** | Desen bazlı | Belirli bir değişiklikle birlikte skor değişimi |
| **Denenebilir** | Test edilebilir | Belirli bir aksiyon sonrası gözlem önerisi |

---

## 3. MVP Öneri Kuralları

| Kural | Koşul | Öneri | Kanıt |
|:-----:|-------|-------|:-----:|
| Düşen skor | Skor ≥%10 düştü | "Kaynak içeriğinizi güncelleyin" | Deneysel |
| Zayıf bileşen | Bir bileşen diğerlerinden %30+ düşük | "Zayıf bileşene odaklanın: [bileşen]" | Korelasyonel |
| Rakip geçişi | Rakip SOV'u %10+ arttı | "Rakibin güçlü olduğu alanları inceleyin" | Deneysel |
| Site engeli | Bot erişim engeli tespit edildi | "robots.txt ayarlarınızı güncelleyin" | Denenebilir |
| Düşük alıntı | Kaynak payı düşük | "Kendi domaininizdeki içeriği artırın" | Denenebilir |

---

## 4. NG10 Filtresi

Her öneri kullanıcıya gösterilmeden önce NG10 uygunluk filtresinden geçer:

| Filtre | Açıklama |
|:------:|----------|
| Politika uyumu | Motor kullanım politikalarına aykırı mı? |
| Etik sınır | Manipülatif taktik içeriyor mu? |
| Garanti yasağı | Kesin sonuç garantisi veriyor mu? |
| Dil denetimi | Olasılıksal dil kullanıyor mu? (İ4) |

---

## 5. Öneri Yaşam Döngüsü

```
Açık → Kullanıcı değerlendirir
     → Uygulandı → Etki izleniyor (HT1)
     → Reddedildi (gerekçe opsiyonel)
     → Süre doldu → Otomatik arşiv
```

---

## Kaynaklar

- 0412 Opportunity Engine — fırsat tespiti (öneri girdisi)
- 0309 Scoring Engine — hesap motoru, kurallar
- 0204 PRD — FR-E1, FR-E2, FR-E3, FR-E4, FR-F2
- 0302 Domain Model — Recommendation varlığı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: öneri türleri, MVP kuralları, NG10 filtresi, yaşam döngüsü. |
