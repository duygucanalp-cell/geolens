# 0505 · Test Uygulaması

| Alan | Değer |
|---|---|
| Doküman ID | 0505 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0304, 0501 |

---

## 1. Amaç

GAVF uyumluluğunu doğrulamak için referans test uygulaması.

## 2. Test Ortamı

- Go 1.22+ veya Python 3.11+
- Test motoru: Referans AI motoru simülasyonu
- Test prompt seti: 0106'ya uygun 10 prompt

## 3. Doğrulama Adımları

1. Test prompt setini seç
2. Ölçümü başlat (n=3, temp=0)
3. Skor hesaplamasını çalıştır
4. Sonuçları 0304 test senaryolarıyla karşılaştır
5. Fidelite etiketlerini doğrula
6. Güven aralıklarını doğrula

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: referans test uygulaması. |
