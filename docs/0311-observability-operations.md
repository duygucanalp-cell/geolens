# 0311 · Observability & Operations

| Alan | Değer |
|---|---|
| Doküman ID | 0311 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş çekirdek doküman: log, metrik, alarm, cache stratejisi (Faz 3 kapanışı) |
| İlişkili | 0303, 0305, 0306, 0307 §9, 0308, 0309, 0310 (girdi); 0402, 0403, 0404 (çıktı); M8, M10, M11, K1, K3 |

---

## 1. Amaç ve Kapsam

Bu doküman gözlemlenebilirlik mimarisini ve operasyon disiplinini sabitler: telemetri ve korelasyon sözleşmesi, metrik kataloğu, alarm tasarımı, log/iz yönetimi, cache stratejisi, periyodik rutinler, yedekleme/felaket kurtarma çerçevesi ve kapasite gözetimi. Önceki dokümanlardan devredilen izleme kancalarını tek envanterde toplar ve Faz 3'ü kapatır. Kapsam dışı: araç ve sağlayıcı kesin seçimleri (0402), runbook tam metinleri (depoda docs/runbooks), alarm eşiklerinin sayısal değerleri (tamamı [K]; pilotta kalibre edilir).

## 2. Telemetri Mimarisi ve Korelasyon

Üç sinyal türü OTel standardıyla üretilir: yapılandırılmış JSON loglar, Prometheus uyumlu metrikler ve dağıtık izler. Korelasyon sözleşmesi sabittir: request_id API katmanında doğar, kuyruğa job_id ile bağlanır (iz bağlamı Streams mesajında taşınır), hesapta calculation_run_id'ye zincirlenir; her hata zarfı correlation_id döndürdüğünden (0306 §6) destek talebinden tam zincire inilebilir. Kardinalite kuralı: metriklerde kiracı etiketi kullanılmaz (etiket patlaması); kiracı bazlı analiz loglar ve olay kayıtları üzerinden yapılır, K1 maliyet kırılımı usage_records'tan gelir. Log hijyeni mutlaktır: prompt içeriği, ham yanıt gövdesi ve kişisel veri telemetriye yazılmaz (0308 §9, 0310 maskeleme); loglar yalnız meta taşır.

## 3. Metrik Kataloğu

| Grup | Metrikler |
|---|---|
| API | Uç bazlı istek oranı, gecikme dağılımı (p50/p95/p99), hata kodu dağılımı (aile bazlı), hız sınırı ret sayacı |
| Kuyruk ve işçi | 0307 §9 seti: kuyruk derinliği, en yaşlı bekleyen yaşı, işlem süresi histogramı, devralma ve DLQ sayaçları, outbox pending/dead |
| Motor | Sınıf etiketli hata sayaçları (M8), canary sonuçları, devre kesici durumu, upstream kota sayacı, bütçe kullanım oranı, engine_meta sürüm kayması olayları |
| Hesap | calculation_run süresi, determinizm doğrulama sonuçları, anlamlılık tetik/uyarı oranları (M11 kalibrasyon beslemesi) |
| Ürün teknik | Pencere kapanış oranı (M10), tazelik yaşı dağılımı (K3), derin bağlantı çözümleme sayacı (M1 teknik kaynağı), rapor üretim süreleri |
| Veri katmanı | Bağlantı havuzu doygunluğu, sorgu gecikme trendleri, büyük tablo hacimleri, cache isabet oranı, Redis bellek ve komut gecikmesi |

## 4. Alarm Tasarımı

İlkeler: semptom öncelikli (kullanıcı etkisi olan durum sayfalar), her alarm bir runbook'a bağlanır, aksiyonsuz alarm envanterden silinir (alarm yorgunluğu disiplini). İki seviye kullanılır:

| Seviye | Envanter |
|---|---|
| Kritik | İzolasyon reddi olayı (kuyruk yükü uyuşmazlığı dahil), denetim zinciri kopukluğu, determinizm eşleşmezliği, ölçüm hattı durması (pencere kapanış oranı çöküşü), platform bütçe tavanı, DLQ girişi, outbox birikmesi, veritabanı erişilemezliği |
| Uyarı | Motor hata oranı artışı, canary başarısızlığı, kuyruk yaşlanması, tazelik ihlali (K3), upstream kota yaklaşımı, sertifika süresi yaklaşımı, cache isabet düşüşü |

Eşik değerleri [K] pilot verisiyle kalibre edilir ve alarm tanımları kod olarak depoda sürümlenir (0403). Güvenlik alarm seti 0310 §10 devrini birebir kapsar.

## 5. Log ve İz Yönetimi

Log seviyeleri standarttır; yüksek hacimli ayrıntı kayıtları örneklenir, hata ve ayrıcalıklı eylem kayıtları örneklenmez. Saklama sınıf bazlıdır [K]: operasyon logları kısa, denetim izi 0204 O-4 politikasına bağlı uzun (denetim izi zaten veritabanındadır; log kopyası ikincildir). İz bağlamı API'den işçiye mesaj alanıyla taşınır; bir kullanıcı destek talebi correlation_id ile gelir ve tek sorguda istek → iş → hesap zinciri görüntülenir. Loglara erişim rol kısıtlıdır ve erişimin kendisi ayrıcalıklı eylem olarak izlenir (0310 ilkesi).

## 6. Cache Stratejisi

Cache tek amaçlıdır: pano okuma yükünü ve maliyetini düşürmek (K1 pano koruması). Katmanlar: sunucu tarafı pano önbelleği (cache:dash anahtarları; kısa TTL; anahtar çalışma alanı + sorgu imzası), skor listelerinde HTTP koşullu istek desteği (ETag), istemci tarafında SPA kısa süreli bellek önbelleği. Geçersizleştirme bilinçli olarak TTL tabanlıdır, olay tabanlı değildir: skor yayını sonrası saniyeler mertebesindeki tutarsızlık kabul edilir çünkü tazelik gerçeğini her zaman freshness_at söyler; kural mutlaktır: cache hiçbir yüzeyde tazelik damgasını maskeleyemez veya bayat skoru taze gösteremez. İsabet oranı metriği (§3) K1 panosunun parçasıdır; düşüş uyarı üretir. Redis'in gerçek kaynak olmama kuralı (0303 §7) cache için de geçerlidir: tam kayıp yalnız gecikme üretir, veri üretmez.

## 7. Operasyon Rutinleri ve Runbook Envanteri

| Rutin / Runbook | İçerik ve kadans |
|---|---|
| Denetim zinciri doğrulama | Periyodik tarama + günlük kök karma saklama (0310 §7); kopukluk kritik alarm |
| DLQ inceleme ve yeniden oynatma | Yetki 0310 §8; gerekçeli denetim kaydı; oynatma adımları runbook'ta |
| Motor kesintisi müdahalesi | Devre kesici durumu, kademe düşürme iletişimi, K2 pilot tetiği (0308 §7) |
| Anahtar rotasyonları | 0310 §8 sınıf bazlı prosedürler; çift anahtar penceresi doğrulaması |
| Determinizm alarmı müdahalesi | Eşleşmeyen koşunun izolasyonu, girdi seti incelemesi, yayın dondurma kararı |
| Bakım temizlikleri | Outbox dispatched temizliği, kullanılmayan indeks taraması, canary değerlendirme özeti |
| Kapasite gözden geçirme | Aylık: §9 göstergeleri, bölümleme tetik kontrolü, maliyet kırılımı |
| Geri yükleme tatbikatı | Kadanslı [K] otomatik geri yükleme testi; §8 doğrulaması |

Runbook metinleri depoda docs/runbooks altında sürümlenir; her alarm tanımı ilgili runbook bağlantısını taşır.

## 8. Yedekleme ve Felaket Kurtarma

PostgreSQL: sürekli WAL arşivi ile zaman-noktası kurtarma (PITR) + periyodik tam yedek; yedek doğrulama otomatik geri yükleme testiyle yapılır, test edilmemiş yedek yok sayılır. Redis: kalıcılık ikincildir; tam kayıpta kuyruklar outbox'tan, sayaçlar usage_records'tan yeniden inşa edilir (0303 §7 prosedürü runbook'tadır). S3: kova sürümleme açıktır; çapraz hedef kopya kararı sağlayıcı ve TR veri yerleşimi seçimine bağlıdır (0402; O-3 saklama politikasıyla). Hedef çerçeve: PG için RPO dakikalar sınıfı (PITR), platform için RTO saatler sınıfı; kesin hedefler [K] 0402 altyapı seçimiyle sayısallaşır ve tatbikatla kanıtlanır. Zarf anahtarlarının (0310 §6) yedeği ayrı ve daha sıkı korunan kanaldadır: anahtar kaybı veri kaybıdır, bu bilinçli kripto-silme tasarımının bedelidir ve yedek prosedürü bunu açıkça yönetir.

## 9. Kapasite ve Maliyet Gözetimi

Aylık kapasite raporu dört göstergeyi izler: (1) büyük tablo hacimleri ve sorgu gecikme trendi; bölümlemeye geçiş tetiği bu ikilinin eşleşmesidir [K] (0303 devri), (2) S3 depolama büyümesi ve yaşam döngüsü sınıf geçiş etkisi, (3) motor harcaması ve kiracı başına maliyet (K1 panosu; bütçe tavanı payı), (4) işçi doygunluğu (kuyruk yaşlanma trendi ve profil bazlı süre histogramları). Rapor, replika ve kaynak kararlarının (0402) girdisidir; kapasite kararları tepkisel değil trend tabanlı alınır.

## 10. AVIP için Çıkarımlar

1. 0402'ye araç ve altyapı devirleri: log/metrik/iz depoları, uptime probu, kasa/KMS, çapraz kopya hedefi, RTO/RPO sayısallaştırması; tamamı TR veri yerleşimi kararıyla birlikte.
2. 0403 bağı: telemetri yapılandırması ve alarm tanımları kod olarak sürümlenir; korelasyon zinciri smoke testi dağıtım doğrulamasına girer (0404).
3. Pilot kalibrasyon konsolidasyonu [K]: alarm eşikleri, log saklama sınıfları, geri yükleme tatbikat kadansı, bölümleme tetiği; 0309 §10 listesiyle birlikte tek kalibrasyon gündemi oluşturur.
4. Faz 3 bu dokümanla kapanır: 0301-0311 seti mimariyi uçtan uca sabitledi; tüm ADR'ler Kabul, D-02 ve D-03 kapalı. Faz 4 uygulamaya iniş fazıdır (geliştirme ortamı, dağıtım, CI/CD, test).
5. Program açıkları hatırlatması: v1.1 düzeltme turu zamanlaması (0206 O-4; Faz 4 öncesi tek geçiş önerisi geçerli), 0002 O-1 segment kararı ve 0006 O-3 isim finali PO'da bekliyor.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Alarm eşiklerinin başlangıç seti [K] | TL önerisi + pilot kalibrasyonu. |
| O-2 | Log ve metrik saklama sınıfları [K] | 0204 O-4 saklama ailesiyle; maliyet etkili; TL. |
| O-3 | RTO/RPO sayısal hedefleri ve tatbikat kadansı [K] | 0402 altyapı seçimiyle; PO + TL. |
| O-4 | Bölümleme tetik kriterinin sayısallaştırılması [K] | Hacim + gecikme eşleşmesi; TL. |

---

## Kaynaklar

- 0307 §9 · kuyruk/işçi sinyal seti (metrik kataloğunun çekirdeği)
- 0308-0309 · canary, devre kesici, bütçe tavanı, determinizm alarmı devirleri
- 0310 · zincir doğrulama, rotasyon runbook'ları, güvenlik alarm seti, zarf anahtarı yedeği
- 0303 · hacim/bölümleme, kullanılmayan indeks, outbox izleme kancaları; Redis yeniden inşa kuralı
- 0004 Success Metrics · M8, M10, M11, K1, K3 (teknik ölçüm kaynakları)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: korelasyon zinciri sözleşmesi ve kardinalite kuralı, altı gruplu metrik kataloğu, iki seviyeli alarm envanteri (runbook bağlı), log/iz yönetimi, TTL tabanlı cache stratejisi (tazelik maskeleme yasağı), sekiz kalemlik rutin/runbook envanteri, PITR + yeniden inşa + zarf anahtarı yedeğiyle DR çerçevesi, dört göstergeli kapasite gözetimi. Faz 3 kapanışı. |
