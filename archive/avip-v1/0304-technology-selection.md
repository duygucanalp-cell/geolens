# 0304 · Technology Selection

| Alan | Değer |
|---|---|
| Doküman ID | 0304 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 20 · Technology Selection (alternatifler ve gerekçeler) |
| İlişkili | 0301, 0303 (girdi); 0305, 0306, 0307, 0402, 0403 (çıktı); ADR-001 - ADR-005; D-02 |

---

## 1. Amaç ve Kapsam

Bu doküman, 0301'de Önerildi statüsüyle kaydedilen mimari kararları alternatif kıyaslarıyla karara bağlar. Bu dokümanın onayı ADR-001, ADR-003, ADR-004 ve ADR-005'i Kabul statüsüne geçirir ve D-02 kapsamındaki ADR-002'yi kapatır; kararlar 0007 karar kaydına işlenir. Kapsam dışı: dağıtım ortamı ve sağlayıcı seçimi (0402; TR veri yerleşimi kararıyla birlikte), CI/CD araç zinciri (0403), kütüphane sürüm sabitleme (depo tarafında).

## 2. Karar Yöntemi ve Kriterler

Her karar aynı yedi kritere göre değerlendirilir: (1) ekip yetkinliği ve teslim hızı (W4 kapasite gerçekliği), (2) operasyon yükü (kurulum, izleme, yama), (3) NFR uyumu (determinizm N7, izolasyon N1, zamanındalık N8, erişilebilirlik N16), (4) TR veri yerleşimi ve KVKK uyum esnekliği (N12), (5) maliyet profili (K1 disipliniyle uyum), (6) ekosistem olgunluğu ve işe alım havuzu, (7) geri dönüş maliyeti (karar yanlışlanırsa çıkış yolu, §9). Statü sözlüğü: Önerildi (0301), Kabul (bu dokümanın onayıyla), PENDING (dokuman dışı karar). ADR metinleri depoda docs/adr/ altında bu bölümlerin özetiyle tutulur.

## 3. ADR-001 · Çekirdek Yığın

| Katman | Seçim | Ana gerekçe | Elenen alternatifler |
|---|---|---|---|
| Sunucu dili | Go | İşçi eşzamanlılığı, tek ikili dağıtım, güçlü tipleme (deterministik hesap N7), ekip standardı | Node/TypeScript (tek dil cazibesi; işçi yükünde zayıf), Python (hız/dağıtım) |
| Veritabanı | PostgreSQL 16+ | RLS (ADR-004 ön koşulu), JSONB, BRIN, olgun operasyon; 0303 sözleşmesi doğrudan PG özelliklerine yaslanır | MySQL (RLS eşdeğeri zayıf) |
| Kuyruk/önbellek | Redis 7+ | Kuyruk (ADR-005), hız sınırı ve kilitler tek araçta; kayıpta yeniden inşa kuralı 0303 §7 | Ayrı mesaj aracısı (RabbitMQ/Kafka: MVP ölçeğinde operasyon fazlası) |
| Nesne depolama | S3-uyumlu arayüz | Arşiv ve rapor deposu; sağlayıcıdan bağımsız kod; nesne kilidi desteği aranır (I5) | Dosya sistemi (dayanıklılık/imzalı URL eksik) |

Sağlayıcı seçimi (TR bölgesi bulut veya MinIO self-host) 0402 dağıtım kararıdır; KVKK veri yerleşimi tercihi orada kesinleşir (O-1). ADR-001 arayüz düzeyinde bağlayıcıdır.

## 4. ADR-003 · Uygulama Topolojisi

| Seçenek | Güçlü | Zayıf | Sonuç |
|---|---|---|---|
| Modüler monolit + işçi havuzu | Tek kod tabanı, tek dağıtım; bağlam sınırları paket düzeyinde (0302 §3); işçiler aynı koddan ayrı süreç | Disiplin ister (modül sınır ihlali riski; 0305 kuralları) | Kabul |
| Mikroservisler | Bağımsız ölçekleme | W4 ekip için operasyon ve ağ karmaşıklığı; izolasyon testi zorlaşır | Red |
| Serverless işlevler | Sıfır boş maliyet | Uzun ölçüm işleri, bağlantı havuzu ve RLS oturum modeliyle uyumsuzluk | Red |

Uygulama: tek Go modülü; giriş noktaları api, scheduler, worker (ölçüm/rapor/bildirim profilleriyle); ölçekleme süreç kopyasıyla. Modül sınırları ve bağımlılık kuralları 0305'te.

## 5. ADR-004 · İzolasyon Mekanizması

| Seçenek | Güçlü | Zayıf | Sonuç |
|---|---|---|---|
| Tek şema + RLS + uygulama sözleşmesi | Tek migration hattı; katmanlı savunma (0301 §5); operasyon basit; benchmark toplulaştırması (HT2) uygulanabilir | Politika disiplini ve oturum değişkeni yönetimi ister | Kabul |
| Şema-per-tenant | Sert ayrım hissi | Binlerce şemada migration ve bağlantı havuzu yükü; self-serve provizyonu ağırlaşır | Red |
| DB-per-tenant | En sert ayrım | P5 Free hunisiyle (yüksek kiracı adedi, düşük hacim) maliyet/operasyon uyumsuz | Red |

Uygulama notları: bağlantı başına app.tenant_id oturum değişkeni; havuz kullanımında her işlem başında SET LOCAL; RLS politika şablonu 0303 K4; kurumsal kapıda münferit kiracı için adanmış örnek seçeneği kapıya ertelenmiş istisna olarak not edilir (İ1 ile çelişmez: aynı kod, ayrı örnek).

## 6. ADR-005 · İş Kuyruğu

| Seçenek | Güçlü | Zayıf | Sonuç |
|---|---|---|---|
| Redis Streams + tüketici grupları | Ack/yeniden teslim, bekleyen mesaj görünürlüğü, işçi grubu ölçekleme; outbox dağıtıcısıyla güvenilirlik tamam (0303 §4) | Redis kalıcılığına güven sınırlı (kabul: kaynak outbox) | Kabul |
| Redis liste (BRPOP) | En basit | Ack yok; işçi çökmesinde kayıp; görünürlük zaman aşımı elle | Red |
| PostgreSQL kuyruk (SKIP LOCKED) | Tek depo; outbox ile birleşik | Yoğun poll yükü DB'ye biner; hız sınırı/kilitler için Redis zaten gerekli | Yedek yol (§9) |

## 7. ADR-002 · İstemci Yığını (D-02 kapanışı)

Ekip standardı istemcide Flutter'dır ve standart, web pano karmaşıklığının aksini gerektirdiği durumu açıkça istisna tanır. AVIP V1 istemcisi tam da bu istisnadır: giriş arkası, veri-yoğun bir panodur (büyük tablolar, kırılım ve detay inişleri, zaman serileri, e-posta derin bağlantıları, erişilebilirlik NFR-N16). Kıyas:

| Seçenek | Güçlü | Zayıf | Sonuç |
|---|---|---|---|
| React + TypeScript SPA | Olgun veri ızgarası/grafik ekosistemi; erişilebilirlik ve metin seçimi doğal; hafif ilk yük; derin bağlantı rutin | Yerel mobil gelirse ikinci kod tabanı (pencere tetikleyicili, 0206) | Kabul |
| Flutter Web | Gelecekteki yerel mobil ile tek kod tabanı; ekip standardı | Veri-yoğun pano ekosistemi zayıf; erişilebilirlik ve ilk yük dezavantajı; N16 riski | Red (V1 pano için) |
| SvelteKit | Hafiflik | Ekip deneyimi ve ekosistem derinliği düşük | Red |

Karar çerçevesi: V1 panosu React + TypeScript tek sayfa uygulamasıdır (SSR gereksiz: ürün giriş arkasıdır; pazarlama sitesi ayrı ve set dışıdır). Flutter, yerel mobil uygulama penceresi için rezerve edilir (0206 platform ufku); pencere açıldığında ADR-002 eki mobil yığını karara bağlar (O-4 tetikleyici tanımı). Bu kapanış D-02'yi sonuçlandırır ve 0007 kaydına işlenir.

## 8. Destekleyici Seçimler

| Alan | Seçim | Gerekçe |
|---|---|---|
| Veri erişimi | sqlc (tip güvenli üretilmiş sorgular) | Deterministik, gözden geçirilebilir SQL; RLS ve K4 oturum modeliyle doğal uyum; ağır ORM reddi |
| Migration aracı | golang-migrate sınıfı sıralı SQL | 0303 §6 kuralları; CI'da baştan uygulama |
| HTTP yönlendirici | Standart kütüphane + chi sınıfı hafif router | Ara katman zinciri (RBAC, hak, kiracı bağlamı) sade kalır |
| Kimlik üretimi | oklog/ulid | K2 kuralı; zaman sıralı |
| PDF üretimi | Headless Chromium tabanlı sunucu render | FR-F4; HTML şablon → PDF; rapor işçisinde izole süreç |
| Gözlemlenebilirlik | OpenTelemetry + Prometheus uyumlu metrikler | 0301 §6 korelasyon zinciri; 0311 tasarımına taban |
| Test altyapısı | Go testing + testcontainers (PG/Redis) | İzolasyon negatif testleri gerçek RLS üzerinde koşar (0403 kapısı) |
| Pano grafikleri | Olgun React grafik/ızgara kütüphaneleri (kesinleştirme uygulamada) | GA bantlı zaman serisi ve kırılım tabloları; O-3 |

## 9. Reddedilen Alternatifler ve Dönüş Yolları

| Karar | Yanlışlanma sinyali | Dönüş yolu ve maliyeti |
|---|---|---|
| ADR-003 monolit | Belirli bir işçi profili bağımsız ölçek ister | İşçi zaten ayrı süreç; modül sınırları korunursa servis ayırma orta maliyet |
| ADR-004 RLS | Kurumsal müşteri adanmış ortam şartı koyar | Aynı kod, ayrı örnek (kapı istisnası); şema-per'e dönüş yüksek maliyet, öngörülmüyor |
| ADR-005 Streams | Redis operasyonu yük olur veya kayıp toleransı sorun çıkarır | PG SKIP LOCKED kuyruğa düşüş; outbox zaten kaynak olduğundan geçiş düşük maliyet |
| ADR-002 React | Yerel mobil penceresi açılır ve web ile kod paylaşımı kritikleşir | Flutter mobil + mevcut API; pano React kalır; ADR-002 eki (O-4 tetikleyicisi) |
| sqlc | Dinamik sorgu ihtiyacı aşırı artar | Sınırlı sorgu oluşturucu ekleme; ORM'e dönüş öngörülmüyor |

## 10. AVIP için Çıkarımlar

1. Bu onayla ADR-001, 002, 003, 004, 005 Kabul statüsüne geçer; D-02 kapanır; kayıtlar 0007 karar defterine ve depoda docs/adr/ dizinine işlenir.
2. 0305 modül iskeleti Go paket yapısı üzerinden tanımlanır (internal/identity, internal/measure benzeri); bağlam-paket eşlemesi 0302 §3 haritasıyla birebir.
3. 0306 API tasarımı sözleşme-öncelikli ilerler (OpenAPI); sqlc + üretilmiş istemci tipleriyle uçtan uca tip güvenliği hedeflenir.
4. 0307 kuyruk tasarımı Redis Streams + outbox dağıtıcısı üzerine yazılır; tüketici grubu ve yeniden teslim parametreleri orada.
5. 0402'ye devirler: sağlayıcı ve TR veri yerleşimi (O-1), e-posta sağlayıcısı (O-2), Chromium render kapasite planı.
6. Bekleyen karar defteri güncel: D-03 hâlâ PENDING (0308 öncesi zorunlu); v1.1 düzeltme turu zamanlaması (0206 O-4) açık.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~Nesne depolama ve barındırma sağlayıcısı; TR veri yerleşimi tercihi~~ | ~~0402; KVKK duruşu; PY + TL.~~ |
| 🔄 O-1 | Nesne depolama ve barındırma yaklaşımı | **YÖN KARARI** (21.07.2026, 0402 v1.1): VM tabanlı self-host. TR sağlayıcıdan VM kiralanacak, tüm stack VM'ler üzerinde kurulacak. Detaylı sağlayıcı ve konfigürasyon 0402 O-1 olarak açık kaldı. |
| ~~O-2~~ | ~~E-posta gönderim sağlayıcısı~~ | ~~0402; teslim edilebilirlik ve TR alan ısındırma; TL.~~ |
| ✅ O-2 | E-posta gönderim sağlayıcısı | **KAPANDI** (21.07.2026): SendGrid. 0402 v1.2, 0007 D-16. |
| ~~O-3~~ | ~~Grafik/ızgara kütüphanelerinin kesinleştirilmesi~~ | ~~Uygulama başlangıcında; GA bant görselleştirme gereksinimiyle; TL.~~ |
| ✅ O-3 | Grafik/ızgara kütüphaneleri | **KAPANDI** (21.07.2026): Recharts + TanStack Table. GA bant görselleştirme ve kırılım tabloları için. |
| ~~O-4~~ | ~~Flutter yeniden değerlendirme tetikleyicisinin tanımı~~ | ~~0206 O-2 ile hizalı; yerel mobil penceresi kriterleri; TL.~~ |
| **✅ O-4 (KAPANDI)** | **Mobil talep sayısı eşiği: kullanıcıların ≥%20'si mobil erişim talep ettiğinde veya P1 kurumsal kapısı açıldığında Flutter yeniden değerlendirmeye alınır.** | **TL kararı (21.07.2026). 0206 uyumlu. 0007 D-38.** |

---

## Kaynaklar

- 0301 System Architecture §8 · Önerildi statüsündeki karar seti (bu dokümanın girdisi)
- 0303 Database Design · RLS/K4, outbox, kimlik ve migration kuralları (kıyas zemini)
- 0204 PRD · NFR ailesi (kriter setinin kaynağı: N1, N7, N8, N12, N16)
- 0206 Post-MVP Roadmap · yerel mobil penceresi (ADR-002 rezerv kararının dayanağı)
- 0007 Governance · karar defteri ve Tip 1/Tip 2 süreci (kayıt hedefi)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 7 kriterli karar yöntemi; ADR-001 (Go+PG+Redis+S3), ADR-003 (modüler monolit), ADR-004 (tek şema+RLS), ADR-005 (Redis Streams), ADR-002 (React+TS pano; Flutter mobil pencereye rezerve, standart istisnası gerekçeli); 8 destekleyici seçim; yanlışlanma sinyalli dönüş yolları. |
| 1.1 | 21.07.2026 | O-1 yön kararı: VM tabanlı self-host (TR sağlayıcıdan VM + tüm stack self-host). Detaylı sağlayıcı seçimi 0402 O-1 olarak açık kaldı. 0402 v1.1, 0007 D-15. |
| 1.2 | 21.07.2026 | O-2 kapandı: SendGrid (e-posta sağlayıcısı). 0402 v1.2, 0007 D-16. |
| 1.3 | 21.07.2026 | O-3 kapandı: Recharts + TanStack Table. 0007 D-26. |
| 1.4 | 21.07.2026 | O-4 kapandı: Flutter reeval tetikleyicisi (mobil talep ≥%20 veya P1 kurumsal kapısı). 0007 D-38. |
