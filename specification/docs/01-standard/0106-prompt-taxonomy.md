# 0106 · Prompt Taksonomisi (GAVF S1)

| Alan | Değer |
|---|---|
| Doküman ID | 0106 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (S1), 0201, platform/docs/0402 |

---

## 1. Amaç

Bu doküman, AI görünürlük ölçümünde kullanılan promptların sınıflandırma şemasını tanımlar. GAVF S1 (Ölçüm Standardı) kapsamındadır.

---

## 2. Prompt Sınıflandırması

### 2.1 Boyut Bazlı

| Boyut | Prompt Türü | Örnek |
|-------|------------|-------|
| **Varlık (presence)** | Markanın doğrudan sorgulanması | "XYZ hakkında ne biliyorsun?" |
| **Karşılaştırma (comparison)** | Rakiple birlikte sorgulama | "XYZ ile ABC arasındaki fark nedir?" |
| **Öneri (recommendation)** | Tavsiye isteme | "XYZ yerine hangi markayı önerirsin?" |
| **Kategori (category)** | Kategori bazlı sorgulama | "En iyi [kategori] çözümleri nelerdir?" |
| **Problem (problem)** | Sorun bazlı sorgulama | "[Sorun] için hangi çözüm var?" |

### 2.2 Bağlam Bazlı

| Bağlam | Anlamı |
|--------|--------|
| **Markalı (branded)** | Doğrudan marka adı içeren prompt |
| **Kategorik (categorical)** | Kategori/sektör bazlı, marka adı içermeyen prompt |
| **Rakip (competitor)** | Rakip marka referansı içeren prompt |

### 2.3 Etiketleme Şeması

Her prompt: `{boyut}:{bağlam}:{dil}`

Örnek: `presence:branded:tr`

---

## 3. Prompt Havuzu Gereklilikleri

| Gereklilik | Değer |
|------------|-------|
| Minimum prompt sayısı | 5 (en az 3 farklı boyuttan) |
| Markalı / kategorik oranı | En az 1:1 |
| Dil başına ayrı set | Her dil için ayrı prompt seti tanımlanmalıdır |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: prompt taksonomisi, 5 boyut, 3 bağlam, etiketleme şeması. |
