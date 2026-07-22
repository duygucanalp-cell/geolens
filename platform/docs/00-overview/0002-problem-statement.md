# 0002 · Problem Bildirimi

| Alan | Değer |
|---|---|
| Doküman ID | 0002 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0000, 0001, 0101, 0201 |

---

## 1. Problem Bildirimi

> Kurumlar, satın alma ve tercih kararlarını giderek daha fazla şekillendiren AI yanıt motorlarında nasıl temsil edildiklerini ölçemiyor, bu temsilin nedenlerini anlayamıyor ve onu iyileştirmek için sistematik bir araçtan yoksun.

Problem üç alt boşluktan oluşur:

| Kod | Boşluk | Tanım |
|-----|--------|-------|
| **P1** | Ölçüm boşluğu | AI yanıtlarındaki marka varlığının standart bir metriği ve sürekli izleme aracı yok. LLM yanıtları olasılıksaldır — tek sorgu anlık görüntüsü yanıltıcıdır. |
| **P2** | Atıf boşluğu | AI yanıtlarını hangi kaynakların ve hangi içeriğin etkilediği bilinmiyor. Geleneksel analitik, AI kaynaklı etkiyi ayrıştıramıyor. |
| **P3** | Aksiyon boşluğu | Görünürlüğü artırmak için hangi değişikliğin işe yaradığına dair kapalı geri besleme döngüsü yok. |

---

## 2. Bağlam: Davranış Değişimi

Kullanıcı davranışı, sorulara yanıt aramanın birincil yüzeyini değiştiriyor: geleneksel arama sonuç sayfasının bağlantı listesi yerine, AI motorlarının tek sentezlenmiş yanıtı. Bu yüzeyde kullanıcı çoğu zaman kaynak listesi görmez; motorun önerdiği az sayıda marka kararın tamamını şekillendirir.

**İki yapısal sonuç:**
1. Görünürlük ikili hale gelir — yanıtın içinde olan kazanır, olmayan hiç var olmamış gibidir.
2. Görünürlüğün mekanizması değişir — sıralama sinyalleri yerine modelin eğitim verisi ve alıntı davranışı belirleyicidir.

---

## 3. Etkilenen Aktörler

| Aktör | Acı Noktası | Bugünkü Çözüm |
|-------|-------------|---------------|
| **Pazarlama lideri** | "AI bizi öneriyor mu?" sorusuna yanıt yok | Anekdot, tek seferlik denemeler |
| **SEO/içerik ekibi** | Hangi içeriğin AI tarafından alıntılandığı görülemiyor | Elle prompt deneme |
| **Marka/PR ekibi** | AI motorları eski/hatalı bilgi üretebilir | Şikayet gelince manuel kontrol |
| **Ajanslar** | Müşteriye AI görünürlüğü hizmeti satmak istiyor | Elle derlenen ekran görüntüsü raporları |
| **Üst yönetim** | Rakibin AI'da öne geçmesi sessiz pazar kaybı | — |

---

## 4. Mevcut Çözümler Neden Yetersiz

| Yaklaşım | Yetersizlik |
|----------|-------------|
| Geleneksel SEO | SERP'i ölçer, AI yanıtının içini ölçmez |
| Manuel prompt | Ölçeklenmez, örnekleme yok, tarihsel seri yok |
| Web analitiği | Yalnızca tıklama ile gelen AI trafiğini görür |
| Yeni GEO araçları | Parçalı kapsam, standartsız metrikler, sınırlı motor desteği |

---

## 5. Problem Hipotezleri

| ID | Hipotez |
|----|---------|
| **H1** | Pazarlama ekipleri AI görünürlüklerini düzenli ölçmüyor; ölçenler manuel yöntem kullanıyor |
| **H2** | Aynı prompt için görünürlük motorlar arasında farklılık gösteriyor; tek motor izlemek yetmez |
| **H3** | AI yanıtları değişkenlik gösteriyor; tek sorguya dayalı ölçüm güvenilir değil |
| **H4** | Alıntılanan kaynaklar tespit edilebilir örüntüler izliyor ve içerik stratejisiyle etkilenebilir |
| **H5** | Problem, kaynak ayrılacak kadar acil algılanıyor |

---

## 6. GeoLens İçin Çıkarımlar

1. **P1 (ölçüm)** → GeoLens'in çekirdek değer önerisi: çok motorlu, istatistiksel, güvenilir ölçüm.
2. **P2 (atıf)** → Alıntı analizi ve kaynak sınıflandırması ürünün farklılaştırıcılarından.
3. **P3 (aksiyon)** → Öneri motoru ve geri besleme döngüsü, kategorinin "ölçer düzeltmez" eleştirisini cevaplar.
4. **Specification bağlantısı:** Standart metrik yokluğu (P1'in bir nedeni) GAVF'in pazar girişinin tam zamanında olduğunu gösterir.

---

## 7. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Hipotez doğrulama zamanlaması | ⏳ Pilotla birlikte test edilecek. AVIP §8 planı devralındı (H1-H5 doğrulama yöntemleri). |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-04** | **Segment önceliği:** P3 (ajans) + P2 (KOBİ). PO 21.07.2026. | AVIP 0002 O-1 |
| **D-87** | **Coğrafi odak:** TR+EN paralel GTM. TR-first, baştan iki dilde. PO 21.07.2026. | AVIP 0002 O-2 |

---

## Kaynaklar

- 0101 Pazar Analizi — davranış değişimi verileri
- 0001 Vizyon — GeoLens'in cevabı
- archive/avip-v1/0002-problem-statement.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens problem bildirimi. P1-P3 boşlukları, aktör haritası, H1-H5 hipotezleri. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-04 (segment önceliği), D-87 (coğrafi odak). Devralınan Kararlar bölümü eklendi. |
