# 0109 · Uyumluluk Seviyeleri

| Alan | Değer |
|---|---|
| Doküman ID | 0109 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101, 0301, 0302, 0303, 0304 |

---

## 1. Amaç

GAVF uyumluluk seviyelerini ve her seviye için gereklilikleri tanımlar.

---

## 2. Seviye Tanımları

### Temel (Basic)

GAVF uyumlu skor üretimi için asgari gereklilikler.

| Gereklilik | İlke |
|------------|:----:|
| Skor girdileri kaydedilir ve izlenebilir | G1 |
| Aynı girdilerle aynı sonuç üretilir | G2 |
| Her skor fidelite etiketi taşır | G3 |
| Her skor güven aralığıyla sunulur | G4 |

### İleri (Advanced)

Temel + ek gereklilikler.

| Gereklilik | İlke |
|------------|:----:|
| Tüm S3 skor bileşenleri hesaplanır | G5 |
| Metodoloji versiyonlanır ve eski skorlar korunur | G5 |

### Tam (Full)

İleri + aksiyon bileşenleri.

| Gereklilik | İlke |
|------------|:----:|
| Tüm S4 bileşenleri (fırsat, öneri, trend, gözlem) sağlanır | G5 |
| Bağımsız denetimden geçer | — |

### Sertifikalı (Certified)

Tam + resmi sertifika.

| Gereklilik | İlke |
|------------|:----:|
| Üçüncü taraf bağımsız doğrulama | — |
| GeoLens sertifikası | — |
| Yıllık yenileme denetimi | — |

---

## 3. Uyumluluk Beyanı Formatı

GAVF uyumlu bir ürün aşağıdaki formatta beyan yayınlamalıdır:

```
GAVF Uyumluluk Beyanı
Ürün: [ürün adı]
Sürüm: [versiyon]
Uyumluluk Seviyesi: [Temel/İleri/Tam/Sertifikalı]
GAVF Sürümü: [1.0.0]
Son Güncelleme: [tarih]
```

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: 4 uyumluluk seviyesi, gereklilikler, beyan formatı. |
