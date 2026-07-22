# 0506 · Worker Tasarımı (Worker Design)

| Alan | Değer |
|---|---|
| Doküman ID | 0506 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0503, 0307, 0308, 0309 |

---

## 1. Amaç

Bu doküman GeoLens worker süreçlerinin tasarımını tanımlar: profil yapısı, iş yaşam döngüsü, yeniden deneme politikaları ve kapanış davranışı.

---

## 2. Worker Profilleri

| Profil | Kuyruk | Sorumluluk |
|:------:|--------|------------|
| **measure** | q:measure, q:audit | Motor çağrıları, ham yanıt saklama, skor hesaplama |
| **report** | q:report | PDF rapor üretimi, white-label şablon uygulama |
| **notify** | q:notify | Uyarı iletimi, e-posta özeti, bildirim dağıtımı |

---

## 3. İş Yaşam Döngüsü

```
kuyrukta → çalışıyor → tamamlandı
                      → kısmi (bazı motorlar başarısız)
                      → başarısız → kuyrukta (max 3 deneme)
                                  → DLQ (kalıcı başarısız)
```

---

## 4. Worker Başlangıç Parametreleri

| Parametre | Açıklama | Varsayılan |
|-----------|----------|:----------:|
| --profile | Worker profili | measure |
| --concurrency | Eşzamanlı iş sayısı | 4 |
| --batch-size | Toplu okuma boyutu | 10 |
| --queue-poll-interval | Kuyruk yoklama aralığı | 1s |
| --shutdown-timeout | Zarif kapanış süresi | 30s |

---

## 5. Yeniden Deneme Politikası

| Katman | Mekanizma | Max Deneme |
|:------:|-----------|:----------:|
| Çağrı (motor) | Bağdaştırıcı içi kısa deneme | 3 |
| İş (worker) | Üstel geri çekilme + jitter | 3 |
| Kota aşımı | Erteleme (deneme sayılmaz) | Süresiz |

---

## 6. Zarif Kapanış

```
SIGTERM → Yeni iş almayı durdur → Mevcut işleri bitir (timeout)
        → Onaylanamayanlar → XAUTOCLAIM ile devir
```

---

## Kaynaklar

- 0501 System Architecture — konteyner sorumlulukları
- 0503 Event-Driven — kuyruk yapısı, tüketim garantileri
- 0307 Background Jobs — iş sınıfları, digest, yeniden deneme
- 0308 AI Connectors — hata sınıfları
- archive/avip-v1/0307-background-jobs-scheduling.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: worker profilleri, iş yaşam döngüsü, yeniden deneme, zarif kapanış. |
