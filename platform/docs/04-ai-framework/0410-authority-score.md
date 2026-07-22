# 0410 · Otorite Skoru (Authority Score)

| Alan | Değer |
|---|---|
| Doküman ID | 0410 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0409, 0405, 0309, 0204 |

---

## 1. Amaç

Bu doküman, bir markanın AI motorlarındaki otorite/güvenilirlik skorunu tanımlar. Otorite skoru, görünürlük skorunun kaynak payı bileşenini tamamlar.

---

## 2. Otorite Bileşenleri

| Bileşen | Açıklama | Varsayılan Ağırlık |
|---------|----------|:------------------:|
| **Alıntı Güvenilirliği** | Marka domainlerinin alıntılardaki güvenilirlik puanı | %40 |
| **Kaynak Çeşitliliği** | Farklı kaynaklardan alıntılanma sayısı | %30 |
| **Bağlam Kalitesi** | Markanın hangi bağlamda geçtiği (olumlu/nötr) | %20 |
| **Güncellik** | Kaynakların ne kadar güncel olduğu | %10 |

---

## 3. Alıntı Güvenilirliği Puanlaması

| Domain Türü | Puan | Örnek |
|:-----------:|:----:|-------|
| Resmî site | 1.0 | kendi domaini |
| Güvenilir medya | 0.8 | haber sitesi |
| Endüstri platformu | 0.6 | sektör blogsı |
| Kullanıcı içeriği | 0.3 | forum, yorum |
| Bilinmiyor | 0.5 | henüz sınıflandırılmamış |

---

## 4. Otorite Skoru Formülü

```
Otorite Skoru = Σ(domain_puanı × alıntı_sayısı) / toplam_alıntı × 0.40
                + kaynak_çeşitlilik_oranı × 0.30
                + bağlam_puanı × 0.20
                + güncellik_oranı × 0.10
```

---

## Kaynaklar

- 0409 Visibility Score — birleşik skor yapısı
- 0405 Citation Framework — alıntı türleri ve puanlaması
- 0309 Scoring Engine — hesaplama motoru
- 0204 PRD — FR-C6 (güven aralığı), FR-D2 (alıntı analizi)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: otorite bileşenleri, domain puanlaması, formül. |
