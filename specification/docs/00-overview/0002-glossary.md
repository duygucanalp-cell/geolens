# 0002 · Sözlük — GeoLens Specification

| Alan | Değer |
|---|---|
| Doküman ID | 0002 |
| Proje | GeoLens Specification |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0000, 0001, 01-standard/*, platform/docs/0006 |

---

## 1. Standart Terimler

| Terim | Anlamı |
|-------|--------|
| **GAVF** | GeoLens AI Visibility Framework. AI görünürlüğü ölçüm standardı. |
| **AI Motoru** | ChatGPT, Gemini, Perplexity, Claude, Copilot, Grok gibi yapay zeka yanıt motorları. |
| **Marka** | İzlenen web sitesi / şirket / ürün. |
| **Panel** | Bir marka için tanımlanmış prompt seti, motor seçimi ve izleme planı. |
| **Prompt** | AI motoruna sorulan, markanın görünürlüğünü ölçmeyi hedefleyen soru. |
| **Prompt Seti** | Bir panel için tanımlanmış prompt koleksiyonu. |
| **Ölçüm (Measurement)** | Bir prompt setinin bir AI motorunda çalıştırılması ve yanıtların toplanması. |
| **Örnekleme (Sampling)** | Determinizm sağlamak için aynı prompt'un n kez (standart: 3) temp=0 ile koşulması. |
| **Skor Hamuru (Raw Score)** | Ham motor yanıtlarından çıkarılan ham veri. |
| **Hesaplanmış Skor (Calculated Score)** | Deterministik hesap katmanında hesaplanan nihai skor. |
| **Fidelite (Fidelity)** | Bir skorun hangi motor kademesinden üretildiğini gösteren etiket. |
| **Kademe 1 (Direct)** | AI motorunun doğrudan, API dokümantasyonuna uygun çağrıldığı kademe. |
| **Kademe 2 (Official Proxy)** | AI motorunun resmi arama/grounding API'si üzerinden çağrıldığı kademe. |
| **Kademe 3 (Directional)** | AI motorunun dolaylı yöntemlerle (trafik yönlendirme) değerlendirildiği kademe. |
| **Alıntı (Citation)** | AI yanıtında kaynak olarak verilen URL. |
| **Güven Aralığı (Confidence Interval)** | Skorun istatistiksel belirsizlik ölçüsü. |
| **Uyumluluk Seviyesi** | GAVF'a uygunluk derecesi: Temel / İleri / Tam / Sertifikalı. |

---

## 2. Kısaltmalar

| Kısaltma | Açılım |
|:--------:|--------|
| **GAVF** | GeoLens AI Visibility Framework |
| **S1** | Measurement Standard (GAVF Katman 1) |
| **S2** | Response Standard (GAVF Katman 2) |
| **S3** | Scoring Standard (GAVF Katman 3) |
| **S4** | Action Standard (GAVF Katman 4) |

---

## 3. Versiyon Bilgisi

Bu sözlük, GAVF standardıyla birlikte versiyonlanır. Her GAVF sürümünde yeni terimler eklenebilir veya var olanlar güncellenebilir.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GAVF standardı terimleri ve kısaltmalar. |
