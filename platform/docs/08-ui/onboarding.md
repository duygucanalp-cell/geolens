# Onboarding (Kullanıcı Karşılama)

| Alan | Değer |
|---|---|
| Doküman ID | 08-ui/onboarding |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 08-ui/*, 0202, 0201, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform onboarding (kullanıcı karşılama) akışlarını tanımlar. Kayıt, ilk kurulum ve ilk ölçüme kadar olan süreç bu dokümanda detaylandırılır.

---

## 2. Onboarding Adımları

```
Kayıt → E-posta Doğrulama → [İlk Giriş] → Kurulum Sihirbazı → İlk Ölçüm
                                 ↓
                          İlk Skor → Pano [Değer Anı]
```

| Adım | Süre | Açıklama |
|:----:|:----:|----------|
| **Kayıt** | 1 dk | E-posta + parola, ödeme bilgisi istenmez |
| **Doğrulama** | <5 dk | E-posta doğrulama linki |
| **Kurulum Sihirbazı** | 5 dk | Marka ekle, prompt seç, rakip ekle |
| **İlk Ölçüm** | 30-60 sn | Otomatik tetiklenen ilk ölçüm |
| **İlk Skor** | Anlık | Skor panoda görünür → değer anı |

---

## 3. Kurulum Sihirbazı Adımları

| Adım | Ekran | Aksiyon |
|:----:|:-----:|---------|
| 1 | Hoş geldin | Ürün tanıtımı, 3 adımda kurulum vaadi |
| 2 | Marka Ekle | Marka adı, web sitesi, sektör seçimi |
| 3 | Prompt Seç | Sektör şablonlarından önerilen prompt seti |
| 4 | Rakip Ekle | (İsteğe bağlı) rakip marka ekleme |
| 5 | Ölçüm Başlıyor | İlk ölçüm otomatik tetiklenir |
| 6 | İlk Skor | Skor görünür, sonraki aksiyon önerisi |

---

## 4. Free → Pro Geçişi

| Aşama | Tetikleyici | Aksiyon |
|:-----:|:-----------:|---------|
| Kota aşımı | Free limitleri aşıldı | Yükseltme teklifi |
| Değer anı | İlk skor görüldü | Pro özellikleri tanıt |
| 7 gün sonra | Kullanım devam ediyor | E-posta ile Pro teklifi |
| Çalışma alanı sınırı | 2+ marka ekleme | Yükseltme gerekli |

---

## 5. Başarı Metrikleri

| Metrik | MVP Hedefi | Ölçüm |
|:------:|:----------:|-------|
| Kayıt → İlk ölçüm | <10 dk | Zaman damgası farkı |
| Kurulum tamamlama | >%70 | Sihirbazı tamamlayan oranı |
| İlk skor görülme | >%80 | Pano açma oranı |
| 7 gün aktif kalma | >%40 | Haftalık aktif kullanıcı |
| Free → Pro dönüşüm | Pilotla belirlenecek | Paket yükseltme oranı |

---

## Kaynaklar

- 08-ui/design-system — bileşen kullanımı
- 08-ui/navigation — kurulum sonrası yönlendirme
- 0202 User Journeys — kullanıcı yolculukları (UC-01, UC-02, UC-03)
- 0201 Personas — P2 KOBİ, P4 self-serve
- 0204 PRD — FR-A1, FR-B3

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: onboarding adımları, kurulum sihirbazı, Free→Pro geçişi, başarı metrikleri. |
