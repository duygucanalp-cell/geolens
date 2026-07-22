# Erişilebilirlik (Accessibility)

| Alan | Değer |
|---|---|
| Doküman ID | 08-ui/accessibility |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 08-ui/*, 0204, 0202 |

---

## 1. Amaç

Bu doküman GeoLens Platform erişilebilirlik standartlarını tanımlar. WCAG 2.1 AA uyumu hedeflenir.

---

## 2. Uyum Hedefi

| Seviye | MVP | HT1 | Kurumsal |
|:------:|:---:|:---:|:--------:|
| WCAG 2.1 A | ✅ | ✅ | ✅ |
| WCAG 2.1 AA | 🟡 (temel) | ✅ | ✅ |
| WCAG 2.1 AAA | — | — | 🟡 |

---

## 3. Erişilebilirlik Kuralları

| # | Kural | WCAG Karşılığı |
|:-:|-------|:--------------:|
| 1 | Tüm görsellerde alt metin (alt text) zorunludur | 1.1.1 |
| 2 | Renk tek bilgi kaynağı değildir; etiket/desen eklenir | 1.4.1 |
| 3 | Metin/arka plan kontrast oranı ≥4.5:1 | 1.4.3 |
| 4 | Klavye ile tam erişim (tüm işlemler klavyeyle yapılabilir) | 2.1.1 |
| 5 | Odak göstergesi görünür olmalıdır | 2.4.7 |
| 6 | Hata mesajları metin olarak sunulur | 3.3.1 |
| 7 | Sayfa başlıkları anlamlı ve benzersizdir | 2.4.2 |
| 8 | Dil etiketi doğru ayarlanır (TR/EN) | 3.1.1 |

---

## 4. Skor Görsellerinde Erişilebilirlik

| Görsel | Erişilebilirlik Önlemi |
|--------|-----------------------|
| **Trend grafiği** | Veri tablosu alternatifi |
| **Motor kırılımı** | Renk + etiket (renk tek bilgi kaynağı değil) |
| **Skor kartı** | Skor değeri metin olarak okunabilir |
| **Uyarı göstergesi** | Renk + simge + metin |

---

## 5. Test Yöntemleri

| Yöntem | Araç | Sıklık |
|--------|:----:|:------:|
| Otomatik tarama | axe-core, Lighthouse | Her PR |
| Klavye testi | Manuel | Her sürüm |
| Renk körlüğü simülasyonu | Chrome DevTools | Her sürüm |
| Ekran okuyucu | NVDA / VoiceOver | Aylık |
| Kullanıcı testi | — | Pilot öncesi |

---

## 6. Özel Durumlar

| Durum | Çözüm |
|:-----:|-------|
| Skor grafikleri | `aria-label` ile skor değeri okunur |
| Zaman serisi | Veri tablosu `role="table"` ile işaretlenir |
| Bildirim toasts | `role="alert"` ile ekran okuyucuya duyurulur |
| Modal diyaloglar | `aria-modal`, odak tuzağı, Escape kapatma |
| Hata mesajları | `aria-describedby` ile ilişkilendirme |

---

## 7. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-84 | Önem-SLA süreleri [K]: Kritik derhal, yüksek 3 iş günü, orta sprint içi, düşük planlı. Pilot verisiyle kalibre edilecek. | AVIP 0405 O-1 (TL 21.07.2026) |

---

## Kaynaklar

- 08-ui/design-system — renk paleti, kontrast
- 08-ui/dashboard — pano erişilebilirliği
- 0204 PRD — NFR-15 (erişilebilirlik)
- WCAG 2.1 — erişilebilirlik standardı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: WCAG 2.1 AA hedefi, 8 erişilebilirlik kuralı, skor görselleri, test yöntemleri. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-84 (Önem-SLA süreleri). |
