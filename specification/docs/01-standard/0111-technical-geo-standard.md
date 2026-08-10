# 0111 · Teknik GEO Standardı

| Alan | Değer |
|---|---|
| Doküman ID | 0111 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 10 Ağustos 2026 |
| İlişkili | 0104 (S3), 0000 (master plan §4), platform/docs/0417 |

---

## 1. Amaç

Bir web sitesinin AI motorları tarafından **teknik olarak erişilebilirliğini** ve okunabilirliğini ölçen GEO (Generative Engine Optimization) teknik standardını tanımlar. GAVF S5 (GEO Standardı) kapsamındadır.

## 2. LLM Bot Erişimi

### 2.1 İzlenen Botlar

Aşağıdaki botlar en azından izlenir (liste güncellenebilir):

| Bot | Kullanıcı ajanı anahtarı |
|-----|--------------------------|
| GPTBot | `GPTBot` |
| ChatGPT-User | `ChatGPT-User` |
| Google-Extended | `Google-Extended` |
| PerplexityBot | `PerplexityBot` |
| ClaudeBot | `ClaudeBot` |
| anthropic-ai | `anthropic-ai` |
| CCBot | `CCBot` |
| Bingbot (AI) | `bingbot` |

### 2.2 Erişim Skoru

Her bot için `robots.txt` ve meta etiket bazlı erişim durumu:

| Durum | Puan |
|-------|:----:|
| Tam erişim (engel yok) | 100 |
| Kısmi engel (dizin altı) | 50 |
| Tam engel (Disallow: /) | 0 |

Site erişim skoru = bot puanlarının ortalaması.

## 3. Structured Data / Schema Analizi

Aşağıdaki schema türleri taranır ve puanlanır (her tür +12,5 puan, üst sınır 100):

- Organization, Product, Article, FAQPage, BreadcrumbList, LocalBusiness, Review, Person, VideoObject, Event

Schema puanı = doğru uygulanan tür sayısı × 12,5 (JSON-LD tercih edilir; mikro veri kabul edilir).

## 4. Citation Breakdown

Alıntı URL'leri aşağıdaki kategorilere ayrılır:

| Kategori | Örnek |
|----------|-------|
| Resmi kurumsal | şirket alan adı |
| Yüksek otoriteli | gov, edu, büyük yayıncılar |
| Sektörel | sektör portalları, dergiler |
| Sosyal/forum | sosyal medya, Reddit |
| Düşük otoriteli | kişisel blog, içerik çiftlikleri |

Çeşitlilik puanı: en az 3 farklı kategori → 100; 2 kategori → 75; 1 kategori → 50; alıntı yok → 20.

## 5. Teknik GEO Önerileri

Sınırlayıcı durumlarda aşağıdaki öneriler üretilir:

- Bot erişimi engelli ise: `robots.txt` düzeltme, izin verilen dizin listesi
- Schema eksikse: JSON-LD uygulama, doğrulama (schema.org doğrulayıcı)
- Kaynak otoritesi düşükse: otoriter kaynak bağlantısı, sayfa içeriği derinleştirme

## 6. GAVF Uyumu

- S5 kapsamında teknik GEO, Content GEO (0112) ile birlikte GEO Standardı katmanını oluşturur.
- Metrikler S3 skor bileşenleriyle (kaynak payı, otorite) ilişkilidir ancak skoru doğrudan değiştirmez; bağlam sağlar.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 10.08.2026 | İlk yayın: LLM bot listesi, erişim skoru, schema analizi, citation breakdown, teknik öneriler. Platform 0417'den türetilmiştir. |
