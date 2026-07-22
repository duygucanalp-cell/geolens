# 0104 · İş Modeli

| Alan | Değer |
|---|---|
| Doküman ID | 0104 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101, 0102, 0105, 0201, 0205 |

---

## 1. Amaç

GeoLens'in gelir modelini, birim ekonomisini, SaaS katmanlarını ve büyüme dinamiklerini tanımlar.

---

## 2. Gelir Modeli

GeoLens **dört kademeli SaaS** modeliyle çalışır:

| Paket | Hedef | Gelir Modeli | Tahmini Aylık |
|-------|-------|--------------|:-------------:|
| **Free** | P5 üretici, P4 deneme | Kullanım tabanlı sınırlama, dönüşüm hunisi | $0 |
| **Pro** | P4 danışman, P2 KOBİ | Sabit aylık + prompt kotası | $49/ay |
| **Business** | P3 ajans, büyüyen P2 | Koltuk bazlı + müşteri sayısı | $299/ay |
| **Enterprise** | P1 kurumsal | Sözleşmeli, özel fiyatlandırma | $1,000+/ay |

**Gelir prensipleri:**
- Fidelite etiketi ve güven öğeleri hiçbir pakette kısıtlanmaz (ürün kimliği)
- Free kademesi dönüşüm hunisi ve topluluk büyümesi içindir, gelir hedefi değil
- Business paketi B2B2B çarpanı nedeniyle en yüksek müşteri yaşam boyu değerine (LTV) sahiptir

---

## 3. Birim Ekonomisi

| Metrik | Tahmini Değer | Not |
|--------|:-------------:|-----|
| **Müşteri Edinme Maliyeti (CAC)** | $50-200 (Pro), $200-800 (Business) | PLG ağırlıklı; ajans kanalı düşük CAC |
| **Aylık Gelir (ARPU)** | $49 (Pro), $299 (Business) | Pilotla kalibre edilecek |
| **Müşteri Yaşam Süresi (LTV)** | 18-36 ay | SaaS ortalaması |
| **LTV/CAC Oranı** | > 5x hedef | Sağlıklı SaaS metriği |
| **Brüt Marj** | > %80 | API maliyetleri düşük |

**Panel maliyet modeli (K1):** Kiracı başına maliyet = panel boyutu × motor sayısı × örnekleme sayısı. Haftalık 150 promptluk panel maliyeti: <$5/hafta (0101 §5).

---

## 4. Gelir Kırılımı (12. Ay Projeksiyonu)

| Kanal | Pay | Açıklama |
|-------|:---:|----------|
| Business (ajans) | %45 | En yüksek LTV, B2B2B çarpanı |
| Pro (KOBİ + danışman) | %30 | Hacim segmenti |
| Enterprise | %15 | İlk kurumsal müşteriler |
| Free → Pro dönüşüm | %10 | Self-serve huni |

---

## 5. GeoLens İçin Çıkarımlar

1. **Birincil gelir motoru Business paketidir** — ajans kanalı. Free, pazarlama bütçesidir.
2. **Panel maliyet modeli** sağlıklı birim ekonomisinin temelidir. K1 koruması disiplinlidir.
3. **Specification bağlantısı:** GAVF uyumluluk ibaresi, Business ve Enterprise paketlerinde fiyatlandırma gücünü artırır.
4. **Pilot döneminde** paket atamaları arka ofisten yapılır; ödeme altyapısı HT2'de.

---

## 6. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Free kademe prompt kotası | ⏳ Pilot deneyiyle belirlenecek. AVIP D-07 ile self-serve kayıt politikası devralındı. |
| O-2 | Business paket fiyatlandırma modeli | ⏳ Pilot deneyiyle belirlenecek. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-07** | **Self-serve kayıt:** Sürtünmesiz (ödeme bilgisi istenmez). V1 MVP'de açık. PO 21.07.2026. | AVIP 0201 O-2 |
| **D-50** | **Analist raporu:** Şimdilik satın alınmayacak. Pilot sonrası değerlendirilecek. PO 21.07.2026. | AVIP 0105 O-1 |
| **D-16** | **E-posta sağlayıcısı:** SendGrid. TL 21.07.2026. | AVIP 0304 O-2 |

---

## Kaynaklar

- 0101 Pazar Analizi — maliyet modeli, pazar büyüklüğü
- 0201 User Personas — paket yapısı, segment önceliği
- 0205 MVP — pilot tanımı, K1 korumaları

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens iş modeli. 4 kademeli SaaS, birim ekonomisi, 12. ay gelir projeksiyonu. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-07 (self-serve kayıt), D-50 (analist raporu), D-16 (SendGrid). Devralınan Kararlar eklendi. |
