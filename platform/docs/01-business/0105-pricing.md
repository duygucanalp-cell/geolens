# 0105 · Fiyatlandırma

| Alan | Değer |
|---|---|
| Doküman ID | 0105 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0104, 0201, 0205 |

---

## 1. Amaç

GeoLens paketlerinin fiyatlandırma stratejisini ve kademe yapısını tanımlar. Fiyatlar pilot verisiyle kalibre edilecek başlangıç hipotezleridir.

---

## 2. Fiyatlandırma Felsefesi

| İlke | Anlamı |
|------|--------|
| **Değer bazlı** | Fiyat, müşterinin elde ettiği değere göre belirlenir |
| **Şeffaf** | Fiyatlar web sitesinde yayındır (enterprise hariç) |
| **Türkçe-öncelikli** | TR pazarına uygun fiyat kademeleri |
| **Büyümeyle uyumlu** | Free → Pro → Business doğal geçiş |

---

## 3. Paket ve Fiyat Tablosu

| Özellik | Free | Pro | Business | Enterprise |
|----------|:----:|:---:|:--------:|:----------:|
| **Fiyat** | $0 | $49/ay | $299/ay | Özel |
| **Marka sayısı** | 1 | 3 | 10+ | Sınırsız |
| **Motor sayısı** | 1 | 3 | 5 | Tümü |
| **Prompt kotası** | 50/ay | 500/ay | 5,000/ay | Sınırsız |
| **Ölçüm frekansı** | Haftalık | Haftalık | Günlük | Günlük |
| **White-label rapor** | — | — | ✓ | ✓ |
| **API/BI** | — | — | ✓ | ✓ |
| **SSO/SAML** | — | — | — | ✓ |
| **Ekip koltukları** | 1 | 3 | 10+ | Sınırsız |
| **Tarihçe** | 1 ay | 6 ay | 12 ay | 24+ ay |
| **Destek** | Topluluk | E-posta | Öncelikli | 7/24 |

---

## 4. Rekabetçi Konumlandırma

| Rakip | Giriş Seviyesi | Orta Seviye | Üst Seviye |
|-------|:--------------:|:-----------:|:----------:|
| Profound | — | — | ~$1,000+/ay |
| Peec AI | ~$49/ay | ~$199/ay | ~$599/ay |
| Otterly | ~$29/ay | ~$99/ay | ~$299/ay |
| Scrunch | ~$79/ay | ~$249/ay | ~$749/ay |
| **GeoLens** | **$0/ay** | **$49/ay** | **$299/ay** |

**Strateji:** Free kademesiyle en erişilebilir giriş. Pro ile Otterly ile rekabet. Business ile ajans segmentinde farklılaşma.

---

## 5. GeoLens İçin Çıkarımlar

1. **Fiyatlar pilot verisiyle kalibre edilecektir.** Bu tablo başlangıç hipotezidir.
2. **Free kademesi agresiftir** — kategori adoptasyonu ve pazar payı için. Rakiplerin çoğunda ücretsiz kademe yoktur.
3. **White-label ve API** Business paketinin fiyatlandırma gücüdür. Ajans, kendi markasıyla rapor satar.
4. **Enterprise fiyatı özeldir** — P1 satışı başlayınca belirlenir.

---

## 6. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | TR pazarı için indirimli fiyatlandırma | ⏳ Pilot verisiyle belirlenecek. AVIP D-04/D-87 (TR-first strateji) referans alındı. |
| O-2 | Yıllık ödemede %20 indirim | ⏳ Pilot verisiyle kalibre edilecek. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-16** | **E-posta sağlayıcısı:** SendGrid. TL 21.07.2026. | AVIP 0304 O-2 |
| **D-82** | **1.0.0 = GA.** Pilot sonrası ticari açılış. Pilot dönemi 0.x. PO 21.07.2026. | AVIP 0406 O-1 |

---

## Kaynaklar

- 0104 Business Model — gelir modeli, birim ekonomisi
- 0201 User Personas — paket yapısı
- 0102 Rekabet Analizi — rakip fiyat kademeleri

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens fiyatlandırma. 4 kademeli paket, rekabetçi konumlandırma. Pilotla kalibre edilecek başlangıç hipotezleri. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-16 (SendGrid), D-82 (1.0.0 = GA). Devralınan Kararlar eklendi. |
