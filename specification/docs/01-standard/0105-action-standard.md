# 0105 · Aksiyon Standardı (GAVF S4)

| Alan | Değer |
|---|---|
| Doküman ID | 0105 |
| Proje | GeoLens Specification |
| Versiyon | 1.1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0101 (S4), 0206, 0207, 0208, 0209 |

---

## 1. Amaç

Skor verisinden aksiyon üretme sürecini tanımlar. GAVF S4 kapsamındadır.

## 2. Aksiyon Türleri

| Tür | Açıklama | Kaynak |
|-----|----------|--------|
| **Fırsat (Opportunity)** | Düşük skorlu ancak yüksek etkili alanlar | Bileşen bazlı skor analizi |
| **Öneri (Recommendation)** | Kanıt dereceli iyileştirme önerileri | Kural kütüphanesi + veri analizi |
| **Trend (Trend)** | Zaman içinde skor değişim yönü | Zaman serisi analizi |
| **Uyarı (Alert)** | İstatistiksel anlamlı değişim bildirimi | Anomali tespiti |
| **GEO İçerik (Content GEO)** | Topic cluster, FAQ, entity, semantik/LSI önerileri | Content gap analizi + otorite skoru |
| **GEO Teknik (Technical GEO)** | Schema, structured data, Knowledge Graph önerileri | Bot izleme + schema analizi |
| **Gap Kapatma (Gap Closure)** | Competitive gap azaltma stratejileri | 5 gap türü analizi |

## 3. Öneri Kanıt Dereceleri

| Seviye | Anlamı |
|:------:|--------|
| **Deneysel** | Veri destekli ama kesin kanıt yok |
| **Korelasyonel** | İki değişken arasında korelasyon var |
| **Uygulayıcı** | Önceki uygulamalarda olumlu sonuç vermiş |

## 4. GEO Aksiyon Türleri (S5)

### 4.1 Teknik GEO Aksiyonları

| Aksiyon | Açıklama | Öncelik |
|---------|----------|:-------:|
| **Bot erişim düzeltmesi** | robots.txt'de engellenen LLM botlarının izinlerinin açılması | Yüksek |
| **Schema ekleme** | Product, FAQ, Organization, Article schema tiplerinin eklenmesi | Yüksek |
| **Entity işaretleme** | Knowledge Graph entity'lerinin işaretlenmesi | Orta |
| **URL yapısı iyileştirme** | Citation dostu URL yapısına geçiş | Orta |
| **SSR/ön işleme** | Dinamik içeriğin botlar tarafından taranabilir hale getirilmesi | Düşük |

### 4.2 Content GEO Aksiyonları

| Aksiyon | Açıklama | Öncelik |
|---------|----------|:-------:|
| **Topic Cluster oluşturma** | Eksik konularda içerik kümeleri oluşturma | Yüksek |
| **FAQ sayfası genişletme** | Sık sorulan soruların kapsamlı yanıtlanması | Yüksek |
| **Entity profil geliştirme** | Marka/varlık bilgilerinin zenginleştirilmesi | Orta |
| **Semantik/LSI optimizasyon** | İlgili terim ve kavramların içeriğe eklenmesi | Orta |
| **İçerik boşluğu doldurma** | AI'nın eksik bulduğu konularda içerik üretimi | Yüksek |

### 4.3 Gap Kapatma Aksiyonları

| Gap Türü | Aksiyon | Beklenen Etki |
|:--------:|---------|:-------------:|
| Visibility Gap | Motor bazında strateji revizyonu | +5-15 puan |
| Citation Gap | Blog/editoryal içerik artışı | +10-20 puan |
| Content Gap | Kaynak çeşitliliği artırma | +5-10 puan |
| Topic Gap | Zayıf konularda içerik güçlendirme | +10-25 puan |
| Prompt Gap | Karşılaştırma/öneri içerikleri ekleme | +5-15 puan |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.1.0 | 27.07.2026 | Turkcell RFP kapsamında genişletme: S5 GEO aksiyon türleri (Teknik GEO, Content GEO) eklendi. Gap kapatma aksiyonları eklendi. GEO aksiyon tabloları (öncelik ve beklenen etki) tanımlandı. Platform 0417-0419 ile senkronize edildi. |
| 1.0.0 | 22.07.2026 | İlk yayın: S4 aksiyon standardı. |
