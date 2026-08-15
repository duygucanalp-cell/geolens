# 0506 · Worker Tasarımı (Worker Design)

| Alan | Değer |
|---|---|
| Doküman ID | 0506 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0503, 0307, 0308, 0309, ADR-014 |

---

## 1. Amaç

Bu doküman GeoLens worker süreçlerinin tasarımını tanımlar: profil yapısı, iş yaşam döngüsü, yeniden deneme politikaları ve kapanış davranışı.

---

## 2. Worker Profilleri

Java geçişi sonrası worker tek Spring profilidir (`worker`); ayrı report/notify profili yoktur. Ölçüm + analiz akışları aynı süreçte, virtual thread tüketicileriyle işlenir.

| Profil | Kuyruk | Sorumluluk |
|:------:|--------|------------|
| **worker** (tek profil) | q:measure | Motor çağrıları, ham yanıt saklama, skor hesaplama, tavsiye, sentiment/hallüsinasyon/gap analizi, kritik bildirim |
| **worker** (tek profil) | q:governance | Faz 4 olayları: guardrail/gate/incident/drift/redteam webhook iletimi + ACK |

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
| SPRING_PROFILES_ACTIVE | Profil seçimi (worker) | worker |
| queue.consumer-group | Redis Stream consumer group adı | cg:measure |
| queue.consumer-name | Tüketici adı (her instance farklı) | örnek adı |
| queue.read-block-ms | XREADGROUP BLOCK süresi | 5000 |
| queue.workers-enabled | Tüketicileri aç/kapat | true |

Tüketiciler virtual thread'lerde çalışır (measure + governance); ana iş parçacığı `CountDownLatch` ile açık tutulur.

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
SIGTERM/SHUTDOWN → @PreDestroy → latch serbest → tüketici döngüleri durur
                → İşlenmemiş mesajlar → XAUTOCLAIM ile devir
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
| 1.1 | 15.08.2026 | **Java geçişi:** Worker profilleri tek Spring `worker` profiline indirgendi (q:measure + q:governance); başlangıç parametreleri Spring property/env adlarıyla güncellendi; zarif kapanış `@PreDestroy` + latch olarak yeniden ifade edildi. ADR-014 ilişkili listesine eklendi. |
