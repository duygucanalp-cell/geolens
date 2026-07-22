# 0304 · Uyumluluk Test Senaryoları

| Alan | Değer |
|---|---|
| Doküman ID | 0304 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0301, 0303 |

---

## 1. Amaç

GAVF uyumluluğunu doğrulamak için test senaryoları.

## 2. Test Senaryoları

| ID | Senaryo | Beklenen Sonuç |
|:--:|---------|----------------|
| T1 | Aynı prompt 3 kez koşulur | Her koşuda aynı yanıt (determinizm) |
| T2 | Skor yeniden hesaplanır | Aynı girdilerle aynı skor |
| T3 | Fidelite etiketi kontrolü | Tüm skorlar etiket taşır |
| T4 | Güven aralığı kontrolü | Tüm skorlar CI ile sunulur |
| T5 | Alıntı şeması kontrolü | Tüm alıntılar 0107 şemasına uygun |
| T6 | Prompt taksonomisi kontrolü | Tüm promptlar 0106'ya uygun etiketlenmiş |

## 3. Test Raporu

Her test için: ID, durum (geçti/kaldı), hata varsa açıklama.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: 6 test senaryosu. |
