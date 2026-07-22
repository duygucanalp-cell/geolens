# 0003 · Hedefler ve Hedef Olmayanlar

| Alan | Değer |
|---|---|
| Doküman ID | 0003 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0002, 0004, 0205, 0206 |

---

## 1. Amaç

Ürünün neyi başarmayı hedeflediğini ve neyi bilinçli olarak yapmayacağını tanımlar. Hedefler sonuç odaklıdır; sayısal eşikler 0004'te tanımlanır.

---

## 2. Hedefler

| ID | Hedef | Bağ | Ufuk |
|----|-------|:---:|:----:|
| **G1** | Markaların AI yanıtlarındaki görünürlüğünü çok motorlu, tekrarlanabilir ve istatistiksel olarak güvenilir ölçmek | P1 | V1 |
| **G2** | Her skoru üreten veriye kadar izlenebilir kılmak (calculation_run_id, faktör görüntüsü) | P1 | V1 |
| **G3** | AI yanıtlarını etkileyen kaynakları görünür kılmak (alıntı analizi, sınıflandırma) | P2 | V1 |
| **G4** | Görünürlüğü artıracak kanıt dereceli öneriler sunmak | P3 | V1 |
| **G5** | Rakip karşılaştırması ve kategori kıyası sağlamak | P1, P2 | V1 |
| **G6** | Zaman serisi izleme ve anlamlı değişim uyarıları sağlamak | P1 | V1 |
| **G7** | Çok kiracılı, güvenli ve denetlenebilir kurumsal altyapı sunmak | — | V1 |
| **G8** | Pilot müşterilerle değer hipotezini doğrulamak | H5 | V1 |
| **G9** | AI görünürlüğü ölçümünde kategori referansı olmak (GAVF ile) | Vizyon | Platform |

---

## 3. Hedef Olmayanlar

| ID | Hedef Olmayan | Gerekçe |
|----|---------------|---------|
| **NG1** | CMS olmak | Mevcut CMS'lerle entegrasyon hedeflenir; ikamesi değil, zekâ katmanıyız |
| **NG2** | Web sitesi barındırma | Altyapı hizmeti kategori dışı |
| **NG3** | Reklam yönetimi | Ücretli kanal ayrı disiplin |
| **NG4** | Sosyal medya planlama | Farklı yüzey, farklı kategori |
| **NG5** | Tam kapsamlı klasik SEO denetimi | SEO araçları tamamlanır, ikame edilmez |
| **NG6** | Backlink yönetimi | Etik risk; şeffaflık ilkesiyle çelişir |
| **NG7** | AI modeli eğitme/fine-tuning | Motor yüzeyinin ölçümü, içine müdahale değil |
| **NG8** | Görünürlük garantisi | AI çıktıları deterministik değil; olasılıksal ölçüm sunarız |
| **NG9** | Yetkisiz veri toplama | Resmî API'ler ve izinli yöntemler esastır |
| **NG10** | Manipülatif GEO teknikleri | Açıklanabilirlik ilkesiyle çelişir |

---

## 4. GeoLens İçin Çıkarımlar

1. **G1-G7** V1 hedefleridir ve 0205 MVP kapsamıyla doğrulanır.
2. **G9** en uzun vadeli hedeftir — GAVF standardı specification reposunda, platform değil.
3. **NG8-NG10** fidelite ve dürüstlük ilkelerinin sınırlarını çizer. Pazarlıksızdır.

---

## 5. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| ~~O-1~~ | ~~G9 için başarı metriği ne olmalı?~~ | ✅ **KAPANDI**: G9 platform ufkundadır. AVIP D-80 (MVP motorlar) ve D-79 (pilot profili) referans alınarak, metriği 0206 roadmap ile birlikte tanımlanacaktır. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-79** | **Pilot profili:** 6-8 kiracı (3 P3 + 2-3 P2 + 1-2 P4). PO 21.07.2026. | AVIP 0003 O-1 |
| **D-80** | **MVP motor kapsamı:** ChatGPT (direct) + Gemini (official_proxy) + Perplexity (Sonar API). Claude+Grok HT1'de. PO 21.07.2026. | AVIP 0003 O-2 |

---

## Kaynaklar

- 0002 Problem Statement — P1-P3 boşlukları
- 0004 Success Metrics — sayısal eşikler
- 0205 MVP — V1 kapsamı
- archive/avip-v1/0003-goals-non-goals.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens hedefleri. G1-G9, NG1-NG10, V1/Platform ufuk ayrımı. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-79 (pilot profili), D-80 (MVP motor kapsamı). O-1 kapandı. Devralınan Kararlar eklendi. |
