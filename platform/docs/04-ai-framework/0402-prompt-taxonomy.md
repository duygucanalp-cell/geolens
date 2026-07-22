# 0402 · Prompt Taksonomisi

| Alan | Değer |
|---|---|
| Doküman ID | 0402 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0401, 0403, 0404, 0204 |

---

## 1. Amaç

Bu doküman, AI görünürlük ölçümünde kullanılan promptların sınıflandırma şemasını tanımlar. Her prompt, hangi boyutu ölçtüğüne ve hangi marka/kategori bağlamında sorulduğuna göre etiketlenir.

---

## 2. Prompt Sınıflandırması

### 2.1 Boyut Bazlı Sınıflandırma

| Boyut | Prompt Türü | Örnek |
|-------|------------|-------|
| **Varlık (presence)** | Markanın doğrudan sorgulanması | "XYZ hakkında ne biliyorsun?" |
| **Karşılaştırma (comparison)** | Rakiple birlikte sorgulama | "XYZ ile ABC arasındaki fark nedir?" |
| **Öneri (recommendation)** | Tavsiye isteme | "XYZ yerine hangi markayı önerirsin?" |
| **Kategori (category)** | Kategori bazlı sorgulama | "En iyi [kategori] çözümleri nelerdir?" |
| **Problem (problem)** | Sorun bazlı sorgulama | "[Sorun] için hangi çözüm var?" |

### 2.2 Bağlam Bazlı Sınıflandırma

| Bağlam | Anlamı |
|--------|--------|
| **Markalı (branded)** | Doğrudan marka adı içeren prompt |
| **Kategorik (categorical)** | Kategori/sektör bazlı, marka adı içermeyen prompt |
| **Rakip (competitor)** | Rakip marka referansı içeren prompt |

### 2.3 Prompt Etiketleme Şeması

Her prompt şu etiketleri taşır: `{boyut}:{bağlam}:{dil}`

Örnek: `presence:branded:tr` — Türkçe, markalı, varlık sorgusu

---

## 3. Varsayılan Prompt Seti

Her sektör için bir varsayılan prompt seti tanımlanır. MVP'de aşağıdaki sektörler desteklenir:

| Sektör | Prompt Sayısı | Örnek Kategori Promptu |
|--------|:-------------:|------------------------|
| E-ticaret | 5-7 | "En iyi online alışveriş siteleri hangileri?" |
| SaaS / Teknoloji | 5-7 | "En iyi [kategori] yazılımları nelerdir?" |
| Finans | 5-7 | "[kategori] için hangi bankalar öne çıkıyor?" |
| Sağlık | 5-7 | "[kategori] için en güvenilir kaynaklar?" |

---

## Kaynaklar

- 0401 AI Visibility Standard — GAVF S1 katmanı
- 0301 Core Concepts — panel, prompt seti
- 0204 PRD — FR-B2 (şablon kütüphanesi)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: prompt taksonomisi, 5 boyut, 3 bağlam, sektör şablonları. |
