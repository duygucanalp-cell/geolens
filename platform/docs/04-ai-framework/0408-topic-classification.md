# 0408 · Konu Sınıflandırma (Topic Classification)

| Alan | Değer |
|---|---|
| Doküman ID | 0408 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0406, 0407, 0309, 0204 |

---

## 1. Amaç

Bu doküman, AI yanıtlarında markanın hangi bağlam/kategori içinde göründüğünü sınıflandırma yöntemini tanımlar. Konu sınıflandırma, görünürlük skorunun bağlam kalitesini değerlendirmek için kullanılır.

---

## 2. Sınıflandırma Kategorileri

| Kategori | Anlamı | Örnek Bağlam |
|:--------:|--------|-------------|
| **Ürün (product)** | Markanın ürün/hizmetinden bahsediyor | "XYZ'nin yeni özelliği..." |
| **Karşılaştırma (comparison)** | Rakiple karşılaştırma | "XYZ, ABC'den daha iyi..." |
| **İnceleme (review)** | Kullanıcı/değerlendirme | "XYZ hakkında kullanıcı yorumları..." |
| **Haber (news)** | Güncel gelişme | "XYZ yatırım aldı..." |
| **Genel (general)** | Kategorize edilemeyen | Marka geçiyor ancak bağlam net değil |

---

## 3. Sınıflandırma Yöntemi

| Yöntem | Açıklama | MVP |
|--------|----------|:---:|
| Anahtar kelime eşleşmesi | Kategori bazlı anahtar kelimeler | ✅ |
| Prompt bağlamı | Prompt türünden türetme | ✅ |
| Yanıt yapısı | Listenin parçası mı, bağımsız mı? | ✅ |
| Duygu analizi | Olumlu/olumsuz/nötr sınıflandırma | 🔴 (HT2) |
| Derin bağlam | LLM tabanlı sınıflandırma | 🔴 (Ufuk) |

---

## 4. Kategori-Marka İlişki Matrisi

| Prompt Türü | Beklenen Yanıt Kategorisi |
|:-----------:|:-------------------------:|
| Varlık (presence) | Ürün / Genel |
| Karşılaştırma | Karşılaştırma |
| Öneri | İnceleme / Karşılaştırma |
| Kategori | Genel / Ürün |
| Problem | Ürün / Çözüm |

---

## Kaynaklar

- 0406 Answer Parser — normalize metin girdisi
- 0407 Entity Recognition — varlık konumları
- 0402 Prompt Taxonomy — prompt türleri
- 0309 Scoring Engine — bağlam kalitesi

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: sınıflandırma kategorileri, yöntem, kategori-prompt matrisi. |
