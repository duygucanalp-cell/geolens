# 0102 · Ölçüm Standardı (GAVF S1)

| Alan | Değer |
|---|---|
| Doküman ID | 0102 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (S1), 0106, 0201, 0208 |

---

## 1. Amaç

Prompt tasarımı, motor çağrısı ve örnekleme sürecini tanımlar. GAVF S1 kapsamındadır.

## 2. Motor Çağrısı

Her ölçüm şu adımları içermelidir:

1. **Prompt seçimi**: Prompt taksonomisine (0106) uygun bir prompt seçilir
2. **Motor seçimi**: Ölçüm yapılacak AI motoru belirlenir
3. **Çağrı**: Prompt motor API'sine gönderilir
4. **Yanıt toplama**: Ham yanıt kaydedilir

## 3. Örnekleme

| Parametre | Varsayılan | Açıklama |
|-----------|:----------:|----------|
| n (tekrar sayısı) | 3 | Her prompt n kez koşulur |
| temperature | 0 | Determinizm için sıfır |
| Bayraklı oran | Eşik bazlı | Olağandışı yanıt oranı hesaplanır |

## 4. Motor Kademeleri

Kademe tanımları 0108'de yapılmıştır. Her ölçüm, motorun kademesini fidelite etiketi olarak taşır.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: S1 ölçüm standardı. |
