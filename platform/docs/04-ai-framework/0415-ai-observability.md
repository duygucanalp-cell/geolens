# 0415 · AI Gözlemlenebilirlik (AI Observability)

| Alan | Değer |
|---|---|
| Doküman ID | 0415 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0401, 0409, 0414, 0311, 0204 |

---

## 1. Amaç

Bu doküman, GeoLens'in kendi AI görünürlük ölçüm sisteminin gözlemlenebilirliğini tanımlar. Yani: GeoLens'in kendi AI varlığını (markasını) nasıl izlediği, ölçtüğü ve raporladığı.

---

## 2. Kendi Kendini Ölçme

GeoLens, kendi marka görünürlüğünü izlemek için aynı GAVF metodolojisini kullanır:

| Ölçüm | Frekans | Amaç |
|-------|:-------:|------|
| Marka varlığı (GeoLens) | Haftalık | AI motorlarında varlık takibi |
| Sektör GAVF referansı | Haftalık | Standarda atıf yapılma oranı |
| Terim görünürlüğü | Aylık | "AI görünürlük" teriminin AI'lardaki geçişi |
| Rakip kıyası | Aylık | Profound, Otterly'ye göre durum |

---

## 3. Platform Sağlık Metrikleri (0311 ile bağlantılı)

| Metrik | Açıklama | Kaynak |
|--------|----------|--------|
| Ölçüm başarı oranı | Başarılı ölçüm / toplam ölçüm | 0307 |
| Motor gecikmesi | Motor bazlı yanıt süresi | 0308 |
| Skor determinizmi | Aynı girdiyle aynı skor mu? | 0309 |
| Panel güncelliği | Panel değişim sıklığı | 0302 |
| Hata oranı | Motor bazlı hata yüzdesi | 0308 |

---

## 4. Sektör Gözlem Raporu

Periyodik olarak üretilen, AI görünürlük sektörünün genel durumunu özetleyen rapordur:

| Bölüm | İçerik |
|-------|--------|
| Motor değişiklikleri | API, erişim, kademe değişiklikleri |
| Standart uyumu | GAVF güncelleme ihtiyaçları |
| TR pazarı | TR'deki AI görünürlük trendleri |
| Rakip hareketleri | Profound, Otterly, diğer oyuncular |

---

## 5. Alerting & Monitoring (Genişletilmiş)

Turkcell RFP kapsamında eklenen uyarı ve izleme senaryoları:

| Uyarı Türü | Açıklama | Tespit Yöntemi | Eşik |
|:----------:|----------|:---------------:|:----:|
| **Visibility Score Düşüşü** | Görünürlük skorunda anlamlı düşüş | GA ayrışması (0309 §6) | ≥5 puan düşüş |
| **Citation Kaybı** | Daha önce alıntılanan kaynağın kaybı | Citation varlık karşılaştırması | ≥2 kaynak kaybı |
| **Rakip Görünürlük Artışı** | Rakibin görünürlüğünde anlamlı artış | SOV trend karşılaştırması | ≥10 puan artış |
| **Negatif Sentiment** | Marka hakkında olumsuz duygu durumu | NLP duygu analizi | ≤0.4 sentiment skoru |
| **Hallüsinasyon Tespiti** | AI'nın marka hakkında yanlış bilgi üretmesi | Doğruluk kontrolü | Her tespit |
| **Yeni Kaynak** | Daha önce görülmeyen bir kaynağın ortaya çıkması | Domain karşılaştırması | Yeni domain tespiti |

### 5.1 Conversation Replay & Response Archive

Conversation Replay ve Response Archive, AI yanıtlarının saklanması ve geçmişe dönük incelenmesini sağlar:

| Özellik | Açıklama | Saklama Süresi |
|---------|----------|:--------------:|
| **Conversation Replay** | AI motor yanıtının anlık görüntüsü (ekran görüntüsü) | 90 gün (STANDARD) |
| **Response Archive** | Tüm yanıtların versiyonlu, aranabilir arşivi | 1 yıl |
| **Tarihsel Karşılaştırma** | Aynı prompt'un farklı zamanlardaki yanıtlarının karşılaştırması| 1 yıl |
| **AI Cevap Değişimi İzleme** | Aynı prompt'un zaman içinde değişen yanıtlarının takibi | Sürekli |

> Conversation Replay verileri KVKK/GDPR kapsamında değerlendirilmeli ve kullanıcı onayı ile saklanmalıdır.

---

## 6. GAVF Geri Bildirim Döngüsü

```
Platform ölçüm verisi → Metodoloji doğrulama → GAVF güncelleme → Platform'a geri
      ↓                     ↓                        ↓
  Gözlem raporu       Kalibrasyon          Specification
  Alert/Monitoring    Sentiment analizi    reposu
  Hallüsinasyon       Conversation Replay
```

> Bu döngü, GeoLens Platform ile GeoLens Specification arasındaki sürekli iyileştirme bağını kurar. Alert verileri ve Conversation Replay kayıtları, metodoloji iyileştirmelerine girdi sağlar.

---

## Kaynaklar

- 0401 AI Visibility Standard — GAVF metodolojisi
- 0409 Visibility Score — skor modeli
- 0411 Share of Voice — SOV ve sentiment
- 0414 Trend Analysis — trend tespiti
- 0311 Observability — sistem gözlem metrikleri
- 0310 Security — güvenlik ve uyum

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: kendi kendini ölçme, platform sağlık metrikleri, sektör gözlem raporu, GAVF geri bildirim döngüsü. |
| 1.1 | 27.07.2026 | Turkcell RFP kapsamında genişletme: Alerting & Monitoring bölümü eklendi (Visibility düşüşü, Citation kaybı, rakip artışı, negatif sentiment, hallüsinasyon, yeni kaynak uyarıları). Conversation Replay & Response Archive bölümü eklendi. GAVF geri bildirim döngüsü güncellendi. |
