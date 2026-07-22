# 0203 · Görünürlük Skoru

| Alan | Değer |
|---|---|
| Doküman ID | 0203 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0104 (S3), 0202 |

---

## 1. Amaç

Görünürlük skorunun detaylı bileşen tanımı.

## 2. Varlık (Presence) Bileşeni

Bir markanın AI yanıtlarında ne sıklıkla geçtiğini ölçer.

| Metrik | Açıklama |
|--------|----------|
| Ham varlık | Yanıtta marka adının geçme sayısı |
| Normalize varlık | prompt başına ortalama geçme sayısı |
| Ağırlıklı varlık | Markalı promptlara daha yüksek ağırlık |

## 3. Konum (Position) Bileşeni

Markanın yanıt içinde hangi sırada göründüğünü ölçer.

| Sıra | Puan |
|:----:|:----:|
| 1. sıra | 100 |
| 2. sıra | 80 |
| 3. sıra | 60 |
| İlk 5 | 40 |
| İlk 10 | 20 |
| Dışı | 0 |

## 4. Kaynak (Citations) Bileşeni

Markanın AI yanıtlarında kaynak olarak ne sıklıkla gösterildiğini ölçer.

| Metrik | Açıklama |
|--------|----------|
| Doğrudan alıntı | citations[] içinde URL'nin geçmesi |
| Atıf | Metin içinde marka adının referans olarak geçmesi |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: varlık, konum, kaynak bileşen detayları. |
