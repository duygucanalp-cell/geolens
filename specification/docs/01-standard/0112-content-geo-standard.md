# 0112 · İçerik GEO Standardı

| Alan | Değer |
|---|---|
| Doküman ID | 0112 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 10 Ağustos 2026 |
| İlişkili | 0104 (S3), 0105 (S4), 0000 (master plan §4), platform/docs/0418 |

---

## 1. Amaç

Bir markanın AI motorlarında içerik yoluyla bulunabilirliğini artırmaya yönelik içerik boşluklarını (gap) tespit eden ve öneri üreten **Content GEO** metodolojisini tanımlar. GAVF S5 (GEO Standardı) kapsamındadır.

## 2. Content Gap Analizi

### 2.1 Gap Türleri

| Gap Türü | Tanım |
|----------|-------|
| Konu boşluğu (Topic Gap) | Rakibin kapsadığı, markanın kapsamadığı konular |
| Varlık boşluğu (Entity Gap) | Rakibin varlık olarak geçtiği, markanın geçmediği bağlamlar |
| SSS boşluğu (FAQ Gap) | Rakip yanıtlarında sorulan, markada yanıtlanmayan sorular |
| Semantik boşluk | Rakip tarafından kullanılan eş anlamlı/ilişkili terimler |
| Otorite boşluğu | Rakibin sahip olduğu, markanın olmadığı otoriter kaynak bağlantıları |

### 2.2 Fırsat Puanı

Her gap için: `Fırsat Puanı = Etki × Aciliyet × Güven`

- Etki: konunun marka görünürlüğüne katkısı (1-10)
- Aciliyet: rakip kapsamının gücü (1-10)
- Güven: tespitin kanıt gücü (0-1)

En yüksek puanlı gap'ler öneri motoruna girer.

## 3. GEO İçerik Önerileri

| Öneri | Açıklama |
|-------|----------|
| Topic Cluster | Merkez konu + destek içerikler hiyerarşisi |
| FAQ önerileri | AI yanıtlarında geçen sorulara yanıt içeriği |
| Entity optimizasyonu | Schema + açık varlık işaretleme |
| Semantik/LSI | Rakip terimleriyle içerik zenginleştirme |
| İçerik güncelleme | Eski içerikteki boşlukları kapatma |

## 4. Content Hub Puanı

Markanın içerik merkezinin GEO olgunluğu 0-100 puanla ölçülür:

| Bileşen | Ağırlık |
|---------|:-------:|
| Konu kapsamı derinliği | %35 |
| Varlık/schema uyumu | %25 |
| FAQ/soru kapsamı | %20 |
| Bağlantı otoritesi | %20 |

## 5. GAVF Uyumu

- S5 kapsamında Content GEO, Teknik GEO (0111) ile birlikte GEO Standardı katmanını oluşturur.
- Öneriler S4 (Aksiyon Standardı) çıktısı olarak aksiyon kuyruğuna girer; NG10 filtresi ve iddia dili kurallarına uyar.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 10.08.2026 | İlk yayın: 5 gap türü, fırsat puanı, GEO içerik önerileri, Content Hub puanı, GAVF uyumu. Platform 0418'den türetilmiştir. |
