# 0007 · Governance & Ways of Working

| Alan | Değer |
|---|---|
| Doküman ID | 0007 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.27 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 |
| Karşıladığı madde | 11 · Ürün üretim, yönetim, yönetişim |
| İlişkili | 0000, 0003, 0004, 0005; tüm ADR'ler |

---

## 1. Amaç ve Kapsam

Bu doküman, ürünün nasıl üretildiğini ve yönetildiğini tanımlar: roller ve sorumluluk dağılımı, karar alma yolları, doküman yaşam döngüsü, mimari karar kaydı pratiği, çalışma kadansları ve faz geçiş kapıları. Kişi adları ve kadro planlaması kapsam dışıdır; tanımlar rol bazlıdır.

## 2. Roller ve RACI

Beş rol tanımlıdır. Bir kişi birden fazla rolü üstlenebilir; roller kişiye değil sorumluluğa bağlıdır.

| Rol | Sorumluluk alanı |
|---|---|
| **Ürün Sahibi (PO)** | Kapsam, öncelik, hedef ve eşik onayları; faz kapısı kararı; nihai onay mercii. |
| **Teknik Lider (TL)** | Mimari bütünlük, ADR sahipliği, teknik kalite standartları, sürüm onayı. |
| **Analist (AN)** | Doküman üretimi, izlenebilirlik, araştırma yürütme, açık soru takibi. |
| **Geliştirici (DEV)** | Uygulama, test, teknik dokümantasyon katkısı. |
| **Paydaş (PY)** | Ortaklık bağlamındaki gözden geçirmeler ve dış bağımlılıklar (hukuk, marka vekili dahil). |

| Aktivite | PO | TL | AN | DEV | PY |
|---|---|---|---|---|---|
| Doküman üretimi | A | C | R | I | I |
| Doküman onayı (Approved) | A | C | R | I | C |
| Kapsam değişikliği (0003, 0205) | A | C | R | I | C |
| Mimari karar (ADR) | A | R | C | C | I |
| Metrik eşik değişikliği (0004) | A | C | R | I | I |
| Sürüm çıkışı | C | A | I | R | I |
| İsim ve marka kararı (0006) | A | I | R | I | C |

R: yürütür, A: hesap verir (tek kişi), C: danışılır, I: bilgilendirilir.

## 3. Karar Süreci ve Karar Kaydı

Kararlar iki tipe ayrılır. Tip 1 (geri döndürülmesi pahalı: kapsam, mimari, isim, sert kural): yazılı öneri hazırlanır, iki iş günlük itiraz penceresi işletilir, A rolü onaylar, karar kaydı düşülür. Tip 2 (geri döndürülebilir: günlük uygulama kararları): R rolü karar verir ve kaydeder; onay beklemez. Anlaşmazlıkta ilke "karşı çık ve bağlan"dır (disagree and commit); çözülmeyen anlaşmazlık PO'ya taşınır.

Karar kaydının yeri karar tipine göre sabittir: ürün kararları ilgili dokümanın changelog'una, mimari kararlar ADR'ye, bekleyen konular açık soru (O-x) tablolarına işlenir. Örnek kayıt:

| ID | Karar | Tarih / Onay | Gerekçe |
|---|---|---|---|
| D-01 | Repo doküman dili: Türkçe | 12.07.2026 · PO | Ekip varsayılanı; yönetici çıktılarında dil ihtiyaca göre belirlenir. İngilizce öneriye itiraz gelmedi, TR ile devam edildi. |
| D-02 | Frontend platformu | Kapandı (0304 · ADR-002 onayı) | 0304 §10 1; ADR-002 Kabul statüsünde; 0007 §8 konsolide listesinden çıkartıldı. |
| D-03 | Google AI yüzeyi stratejisi | 21.07.2026 · PO | (b) Gemini grounding vekili, official_proxy etiketiyle MVP'de; (c) PY incelemesi paralel başladı. 0102 v1.1, 0205 v1.2, 0308 §2'ye kaydedildi. 0205 O-1 bloklayıcı açık soru kapandı. |
| D-04 | Segment önceliği | 21.07.2026 · PO | P3 (ajans) + P2 (KOBİ) V1 ticari odağı kabul edildi. P1 kurumsal-hazır mimariyle ertelendi. 0201 v1.1, 0002 v1.1'e kaydedildi. 0002 O-1 ve 0201 O-1 kapandı. |
| D-05 | Ürün adı seçimi | 21.07.2026 · PO | Mentiq seçildi. Doğrulama protokolü (domain, tescil, AI testleri) yürütülüyor. 0006 v1.1'e kaydedildi. 0006 O-3 kısmi kapandı (seçim yapıldı). |
| D-06 | Marka tescil sahibi ve kapsamı | 21.07.2026 · PO | Tescil U2 AI Studio adına, Madrid Protokolü ile uluslararası. Co-brand kullanımı ayrı sözleşmeyle. 0006 v1.2'ye kaydedildi. 0006 O-1 ve O-2 kapandı. |
| D-07 | Self-serve kayıt politikası | 21.07.2026 · PO | Self-serve V1 MVP'de açık (davetli+self-serve birlikte). Free kayıt sürtünmesiz — ödeme bilgisi istenmez. Paket atamaları arka ofisten. Genel açılış pilot çıkış kapısı sonrası. 0201 v1.2, 0202 v1.1, 0205'e kaydedildi. |
| D-08 | Benchmark bağlamı kapsamı | 21.07.2026 · PO | UC-12 (benchmark) MVP dışı, HT2'ye bırakıldı. 0203 v1.1, 0205'e kaydedildi. |
| D-09 | Rol modeli granülaritesi | 21.07.2026 · PO | Yönetici + üye rolleri MVP için yeterli. İzleyici rolü MVP kapsamı dışı. 0203 v1.1, 0310 tasarımına kaydedildi. |
| D-10 | API kapsamı (UC-21) | 21.07.2026 · PO | Okuma-yalnız başlar. Yazma yetkisi HT1'e bırakıldı. 0203 v1.2'ye kaydedildi. |
| D-11 | İlk değer eşikleri | 21.07.2026 · PO | Adım 4 (site denetimi) < 30sn, adım 6 (ilk skor) < 24sa. Tasarım hedefi; pilotta kalibre. 0202 v1.2'ye kaydedildi. |
| D-12 | Uyarı eşik varsayılanları | 21.07.2026 · PO | Pilot verisiyle kalibre edilecek. MVP'de manuel eşik ayarı yeterli. 0202 v1.2'ye kaydedildi. |
| D-13 | İş takip aracı | 21.07.2026 · TL | GitHub Projects/Issues. Depo ile aynı platform. 0401 v1.1'e kaydedildi. |
| D-14 | CI platformu | 21.07.2026 · TL | GitHub Actions. Self-hosted runner değerlendirilecek. 0403 v1.1'e kaydedildi. |
| D-15 | Altyapı yaklaşımı (VM self-host) | 21.07.2026 · PO + TL | TR sağlayıcıdan VM kiralanacak, tüm stack VM'ler üzerinde self-host. 0402 v1.1, 0304 v1.1'e kaydedildi. Detaylı sağlayıcı seçimi açık (0402 O-1). |
| D-16 | E-posta sağlayıcısı | 21.07.2026 · TL | SendGrid. API tabanlı, TR alan ısındırma pilot öncesi başlar. 0402 v1.2, 0304 v1.1'e kaydedildi. |
| D-17 | Hotfix istisna sınırları | 21.07.2026 · TL | Migration ve güvenlik etiketli PR'lar hotfix yolundan geçemez. 0401 v1.2'ye kaydedildi. |
| D-18 | İmaj imzalama | 21.07.2026 · TL | V1'de olmayacak, hızlı takipte değerlendirilecek. 0403 v1.2'ye kaydedildi. |
| D-19 | RTO/RPO hedefleri | 21.07.2026 · PO + TL | RPO 1 saat, RTO 8 saat. VM self-host ile uyumlu. 0402 v1.2'ye kaydedildi. |
| D-20 | Test kapsam eşikleri [K] | 21.07.2026 · TL | Kritik paketler ≥%70, genel ≥%50. 0404 v1.1'e kaydedildi. |
| D-21 | Fixture tazeleme kadansı | 21.07.2026 · TL + AN | engine_meta sinyaliyle tetikli + çeyreklik tam tarama. 0404 v1.1'e kaydedildi. |
| D-22 | Yük duman testi | 21.07.2026 · PO + TL | MVP'de yok, pilot verisi sonrası. 0404 v1.1'e kaydedildi. |
| D-23 | Mutasyon testi | 21.07.2026 · TL | HT1'de değerlendirilecek. 0404 v1.1'e kaydedildi. |
| D-24 | Self-serve ödeme (UC-26) | 21.07.2026 · PO | HT2 — genel açılışla birlikte. Pilot döneminde arka ofis. 0203 v1.3'e kaydedildi. |
| D-25 | P2/P3 görüşme erişimi | 21.07.2026 · PO + AN | TR ajans ekosistemi, AN yürütür, pilot öncesi tamamlanmalı. 0201 v1.3'e kaydedildi. |
| D-26 | Grafik kütüphaneleri | 21.07.2026 · TL | Recharts + TanStack Table. 0304 v1.3'e kaydedildi. |
| D-27 | MFA zamanlaması | 21.07.2026 · PO + TL | HT1 — kurumsal kapı öncesi. MVP'de MFA yok. 0310 v1.1'e kaydedildi. |
| D-28 | Oturum süreleri [K] | 21.07.2026 · TL | Mutlak 7 gün, kayan 2 saat. Pilotta kalibre. 0310 v1.1'e kaydedildi. |
| D-29 | Üyelik erişim kapsamı | 21.07.2026 · PO + TL | Kiracı düzeyinde, opsiyonel workspace listesi. 0302 v1.1, 0310 v1.1'e kaydedildi. |
| D-30 | Örnekleme başlangıç değerleri [K] | 21.07.2026 · TL | n=3, temperature=0 (deterministik), bayraklı oran ≥%50 → yetersiz-örneklem. 0309 v1.1'e kaydedildi. |
| D-31 | Anlamlılık eşik seti [K] | 21.07.2026 · TL | Mutlak fark ≥5 puan, %95 GA ayrışması, asgari örneklem n≥10. 0309 v1.1'e kaydedildi. |
| D-32 | İş devralma süreleri ve deneme tavanı [K] | 21.07.2026 · TL | Measure 5dk, notify 2dk, report 15dk boşta kalma; deneme tavanı 3. 0307 v1.1'e kaydedildi. |
| D-33 | Alarm eşik başlangıçları [K] | 21.07.2026 · TL | Kritik 15dk, yüksek 1sa, orta 4sa, düşük 8sa. 0311 v1.1'e kaydedildi. |
| D-34 | Panel versiyon trend sınırı gösterimi | 21.07.2026 · TL | Dikey kesik çizgi + hover araç ipucu (versiyon fark özeti). 0302 v1.2'ye kaydedildi. |
| D-35 | Kimlik stratejisi (ULID) | 21.07.2026 · TL | ULID: 26 karakter, zaman-sıralanabilir, metin tipi — tüm birincil anahtarlar. 0302 v1.2, 0303'e kaydedildi. |
| D-36 | Çalışma alanı path modeli | 21.07.2026 · TL | /v1/workspaces/(ws)/... path modeli onayı. 0306 v1.1'e kaydedildi. |
| D-37 | OpenAPI üreteç aracı | 21.07.2026 · TL | oapi-codegen seçildi. Go tipleri OpenAPI'den üretilecek. 0306 v1.1, 0403'e kaydedildi. |
| D-38 | Flutter reeval tetikleyicisi | 21.07.2026 · TL | Mobil talep ≥%20 veya P1 kurumsal kapısı açıldığında. 0304 v1.4'e kaydedildi. |
| D-39 | Gemini URI çözüm derinliği | 21.07.2026 · TL | Tam çözüm: son hedef alan adına kadar tüm zincir. 0308 v1.3'e kaydedildi. |
| D-40 | Log/metrik saklama sınıfları [K] | 21.07.2026 · TL | Operasyon 30gün, metrik 90gün, denetim log kopyası 1yıl. 0311 v1.2'ye kaydedildi. |
| D-41 | Bölümleme tetik kriteri [K] | 21.07.2026 · TL | 50M satır veya 10GB + sorgu ≥2x yavaşlama. 0311 v1.2'ye kaydedildi. |
| D-42 | Dilim 1 bağdaştırıcısı | 21.07.2026 · TL | Perplexity (Sonar API). Direct kademe. 0401 v1.3'e kaydedildi. |
| D-43 | Faz geçiş zaman çizelgesi | 21.07.2026 · PO | Event-driven: pilot kiracı bulununca Faz 4 başlar. 0000 v1.1'e kaydedildi. |
| D-44 | GTM sıralaması | 21.07.2026 · PO | TR+İngilizce paralel. Ürün baştan iki dilde. 0104 v1.2'ye kaydedildi. |
| D-45 | SOC 2 hazırlık zamanlaması | 21.07.2026 · PO | İlk kurumsal müşteriyle başlar. MVP'de güvenlik-ilk mimari yeterli. 0104 v1.2'ye kaydedildi. |
| D-46 | Anında iletim varsayılanı | 21.07.2026 · PO | Varsayılan digest (tüm sınıflar). Kullanıcı kanal ayarıyla anında iletim seçebilir. 0307 v1.2'ye kaydedildi. |
| D-47 | Harici sızma testi | 21.07.2026 · PO | Pilot kapısına eklenmez. Dilim 4'te yapılır. 0310 v1.2'ye kaydedildi. |
| D-48 | Doküman gözden geçirme kadansı | 21.07.2026 · PO | İlk tur pilot öncesi (v1.1 düzeltme). Çeyreklik döngü pilot sonrası. 0401 v1.4'e kaydedildi. |
| D-49 | Evertune metodolojisi incelemesi | 21.07.2026 · AN+TL | AN demo talep edecek, metodolojiyi inceleyecek. Pilot öncesi tamamlanmalı. 0103 v1.1'e kaydedildi. |
| D-50 | Analist raporu | 21.07.2026 · PO | Şimdilik satın alınmayacak. Pilot sonrası değerlendirilecek. 0105 v1.2'ye kaydedildi. |
| D-51 | Bot listesi bakım süreci | 21.07.2026 · AN+TL | 0007 haftalık senkron gündeminde. AN tarar, TL onaylar. 0308 v1.4'e kaydedildi. |
| D-52 | Öneri kural kütüphanesi | 21.07.2026 · AN+PO | Skor bazlı öneriler. AN hazırlar, NG10+iddia dili denetimi, PO onayı. 0309 v1.2'ye kaydedildi. |
| D-53 | Walking skeleton dilim planı | 21.07.2026 · PO+TL | 4 dilimli plan onay: D1 platform+identity+config+measure+gov → D2 kalan motorlar+pano+denetim → D3 delivery+insight → D4 sertleştirme. 0301 v1.1'e kaydedildi. |
| D-54 | S3 saklama süreleri [K] | 21.07.2026 · PO+TL | 30gün STANDARD → 90gün GLACIER → sil. 0303 v1.2'ye kaydedildi. |
| D-55 | Telafi/özet zamanlaması [K] | 21.07.2026 · PO+TL | Telafi 2 pencere. Digest 18:00 TR. Haftalık özet Pazartesi 09:00 TR. 0307 v1.3'e kaydedildi. |
| D-56 | Partial kapsam eşiği [K] | 21.07.2026 · PO+TL | ≥%50 motor tamam → partial yayın. 0309 v1.3'e kaydedildi. |
| D-57 | Dondurma pencereleri | 21.07.2026 · PO+TL | Kapı değerlendirme haftaları + yılbaşı (2 hafta). 0406 v1.1'e kaydedildi. |
| D-58 | KVKK silme uzlaşımı | 21.07.2026 · PY+TL | Kripto-silme (zarf anahtarı imhası) + anonimleştirme. 0310 v1.0 §6, 0303 v1.3'e kaydedildi. |
| D-59 | Performans eşikleri [K] | 21.07.2026 · TL | Pano <5sn, API <1sn, ölçüm <60sn. 0204 v1.2'ye kaydedildi. |
| D-60 | Benchmark gizlilik eşiği | 21.07.2026 · TL+PY | ≥5 kiracı. 0204 v1.2'ye kaydedildi. |
| D-61 | Okuma API kapsamı | 21.07.2026 · TL | Skor+trend+alıntı+rapor meta. /public/v1. 0204 v1.2'ye kaydedildi. |
| D-62 | Saklama süreleri | 21.07.2026 · TL+PO | 0303 O-2 ile uyumlu. Rapor 1 yıl. 0204 v1.2'ye kaydedildi. |
| D-63 | v1.1 düzeltme turu | 21.07.2026 · PO | Bu oturum sonu — Faz 4 öncesi tek geçiş. 0206 v1.1'e kaydedildi. |
| D-64 | Hesap motoru yeri | 21.07.2026 · TL | measure/calc alt paketi. 0305 v1.1'e kaydedildi. |
| D-65 | engines modülü | 21.07.2026 · TL | Tek modül (internal/engines). 0305 v1.1'e kaydedildi. |
| D-66 | SPA monorepo | 21.07.2026 · TL | web/ aynı depoda. 0305 v1.1'e kaydedildi. |
| D-67 | Lint kuralları | 21.07.2026 · TL | D1-D7 birebir depguard kuralı. 0305 v1.1, 0403'e kaydedildi. |
| D-68 | Uptime SLO politikası | 21.07.2026 · PO | İlk kurumsal müşteriyle sözleşmesel SLO. MVP'de %99.5 [K] tasarım hedefi. 0004 v1.2'ye kaydedildi. |
| D-69 | Kurumsal kapı tarihçe eşiği [K] | 21.07.2026 · PO | 12 ay (hipotez onay). Pilotta kalibre edilir. 0206 v1.2'ye kaydedildi. |
| D-70 | EN açılım tetikleyicisi | 21.07.2026 · PO | PMF sinyali bileşik: TR'de M2≥%80 + M1≥%60 + talep eşiği. 0206 v1.2'ye kaydedildi. |
| D-71 | Güvenlik tur kadansı | 21.07.2026 · PO | Çeyreklik (D-48 doküman kadansıyla hizalı). 0405 v1.1'e kaydedildi. |
| D-72 | Worker profilleri ölçekleme | 21.07.2026 · TL | V1'de tek replika seti. Pilot verisiyle HT1'de ayrıştırma adayı. 0301 v1.2'ye kaydedildi. |
| D-73 | Redis kilit kaybı politikası | 21.07.2026 · TL | Anında pasif (mevcut tasarım). Kilit kaybında üretim durur. 0301 v1.2'ye kaydedildi. |
| D-74 | Kuyruk türü (ADR-005) | 21.07.2026 · TL | Redis Streams + tüketici grupları. Liste elendi. 0303 v1.4'e kaydedildi. |
| D-75 | DLQ yeniden oynatma | 21.07.2026 · TL | Sistem otomatik + manuel override. Audit_log kaydı zorunlu. 0307 v1.4'e kaydedildi. |
| D-76 | İzolasyon hızlı alt kümesi | 21.07.2026 · TL | Kritik akışlar (1-2 negatif senaryo/yüzey). Tam paket main'de. 0403 v1.3'e kaydedildi. |
| D-77 | Tren günü ve terfi penceresi | 21.07.2026 · TL | Cuma tren / Pazartesi terfi. Pilot dönemi. 0406 v1.2'ye kaydedildi. |
| D-78 | AN araştırma eylem planı | 21.07.2026 · PO | Altı AN sorusu pilot öncesi çözülecek: 0101 O-1/O-2 (masabaşı+görüşme), 0103 O-3 (görüşme), 0104 O-3 (bloklayıcı — ajans önceliği), 0105 O-2/O-3 (araştırma+görüşme). 0201 kılavuzuna maddeler eklendi. AN yürütür. |
| D-79 | Pilot profili kararı | 21.07.2026 · PO | 0205 O-3 kararı teyit: 6-8 kiracı (3 P3 + 2-3 P2 + 1-2 P4). 0003 O-1, 0004 O-1 kapandı. |
| D-80 | MVP motor kapsamı | 21.07.2026 · PO | ChatGPT (direct) + Gemini (official_proxy) + Perplexity (Sonar API). Claude + Grok HT1'de. 0003 O-2 kapandı. |
| D-81 | Sentiment V1 kapsamı | 21.07.2026 · PO | V1 dışı (platform ufku). Mention+öneri+alıntı MVP için yeterli. 0005 O-2 kapandı. |
| D-82 | 1.0.0 anı tanımı | 21.07.2026 · PO | 1.0.0 = ticari genel açılış (GA). Pilot çıkış kapısı sonrası. Pilot dönemi 0.x. 0406 O-1 kapandı. |
| D-83 | Derin bağlantı token ömrü [K] | 21.07.2026 · TL | 7 gün + tek kullanım. Haftalık özet döngüsüyle uyumlu. 0306 O-2 kapandı. |
| D-84 | Önem-SLA süreleri [K] | 21.07.2026 · TL | Kritik derhal, yüksek 3 iş günü, orta sprint içi, düşük planlı. 0405 O-1 kapandı. |
| D-85 | VM sağlayıcı stratejisi | 21.07.2026 · TL | Küresel bulut TR bölge (self-host VM + yönetilen PG/KMS). 0402 O-1 kapandı. |
| D-86 | Staging yedek/geri dönüş | 21.07.2026 · TL | DB dump (pg_dump) + imaj geri terfisi. Sağlayıcı bağımsız. 0403 O-4 kapandı. |
| D-87 | Coğrafi odak | 21.07.2026 · PO | TR+EN paralel GTM (D-44 teyidi). TR-first, baştan iki dilde. 0002 O-2 kapandı. |
| D-88 | Örnekleme n ve sıklığı [K] | 21.07.2026 · TL | n=3 (D-30), frekans haftalık/günlük (0205 O-4). Pilotta kalibre. 0004 O-2 kapandı. |
| D-89 | Skor bileşen adları | 21.07.2026 · AN+TL | Pilot öncesi AN hazırlar, TL+PO onaylar. 0005 O-1 eylem planı. |
| D-90 | Sızma testi kapsamı | 21.07.2026 · PO+TL | Dış yüzeyler + izolasyon. Tedarik Dilim 3, uygulama Dilim 4. 0405 O-3 kapandı. |
| D-91 | Sürüm notu şablonları | 21.07.2026 · AN+PO | Pilot öncesi AN hazırlar (iç+kullanıcı), PO onaylar. 0406 O-3 eylem planı. |

## 4. Doküman Yaşam Döngüsü

Durum akışı: Draft, Review, Approved. Approved'a geçiş PO onayıyla olur ve künyeye işlenir. Versiyonlama: 0.x taslak evresi, 1.0 ilk onaylı yayın; içerik değişikliği minor (1.1), yapı veya karar değişikliği major (2.0) artırır. Her versiyonda changelog kaydı zorunludur.

Çalışma sözleşmesi (0000 §6 ile birlikte): mesaj başına tek doküman teslim edilir; revizyon talepleri biriktirilir ve tek geçişte topluca uygulanır; her doküman karşıladığı maddeyi künyesinde belirtir. Review mekanizması GitHub PR üzerinden yürütülür: doküman PR olarak açılır, C rollerinin yorumu PR'da toplanır, PO onayı merge ile Approved'a çevirir.

## 5. ADR Pratiği

Mimari kararlar docs/03-architecture/adr/ altında ADR-001'den başlayarak numaralanır. Zorunlu ADR tetikleyicileri: teknoloji seçimi, veri modeli kırılımları, motor entegrasyon yöntemi, kimlik ve yetkilendirme tasarımı, kuyruk ve zamanlama altyapısı. ADR formatı beş bölümdür: Bağlam, Karar, Alternatifler, Sonuçlar, Durum (Önerildi, Kabul, Reddedildi, Yerini aldı). Kabul edilen ADR değiştirilmez; yeni karar eskisinin yerini alır ve çapraz referanslanır. Rezerve numaralar: ADR-001 teknoloji yığını (0304 ile), ADR-002 frontend platformu (D-02).

## 6. Kadanslar ve Faz Kapıları

| Ritüel | Sıklık | İçerik |
|---|---|---|
| Ürün senkronu | Haftalık | İlerleme, engeller, açık soru durumu; 30 dakikayı aşmaz. |
| Metrik gözden geçirme | Pilotta 2 haftada bir, sonra aylık | 0004 §7 kalibrasyon ritmi; [K] eşiklerinin kontrolü. |
| Research tazeleme | 3 ayda bir | 0101–0105 dokümanlarının güncellik kontrolü (0000 R-05); pazar hızla değişiyor. |
| Faz kapısı | Faz sonunda | Kapanış kriterleri kontrolü ve sonraki faza geçiş kararı (PO). |

### Faz kapısı kriterleri

1. Fazın tüm dokümanları Approved durumda.
2. Açık soruların her biri bir sahibe ve hedef dokümana atanmış.
3. Sonraki fazın giriş bağımlılıkları hazır (örnek: Faz 1 için araştırma soruları netleşmiş).

## 7. Değişiklik ve Risk Yönetimi

Risk kaydı 0000 §8'de yaşar; yeni risk AN tarafından eklenir, PO önceliklendirir. Kapsam değişikliği akışı: etki analizi (hangi dokümanlar etkilenir), ilgili doküman revizyonları, 0000 izlenebilirlik matrisinin güncellenmesi. Yanlışlanan hipotez (0002 §7) otomatik değişiklik tetikleyicisidir. Guardrail ihlali (0004 §5) ilgili çalışmayı duraklatır ve PO gündemine taşınır.

## 8. Faz 0 Kapanış Durumu

Bu doküman Faz 0'ın son teslimatıdır. Kapanış tablosu:

| ID | Doküman | Versiyon | Durum |
|---|---|---|---|
| 0000 | Master Plan | 1.0 | Review |
| 0001 | Vision | 0.2 | Review |
| 0002 | Problem Statement | 1.0 | Review |
| 0003 | Goals & Non-Goals | 1.0 | Review |
| 0004 | Success Metrics | 1.0 | Review |
| 0005 | Glossary | 1.1 | Review |
| 0006 | Brand & Domain | 1.0 | Review; isim doğrulama protokolü yürütülüyor |
| 0007 | Governance | 1.0 | Review |

Faz 0 teslimatları 12.07.2026'da tamamlanmıştır; tüm dokümanlar Review durumundadır ve PO onayı beklemektedir (0007 §5 faz kapısı kriteri 1).

### Konsolide bekleyen kararlar

| Kaynak | Konu | Sahip | Karar yeri |
|---|---|---|---|
| ~~0006 O-3~~ | ~~İsim finalistleri (öneri: Mentiq, Vizora, Visanta) ve doğrulama turu~~ | PO | ~~0006 v1.1~~ → **Mentiq seçildi; protokol devam ediyor** |
| ~~0006 O-1, O-2~~ | ~~Marka sahipliği ve tescil kapsamı~~ | PO + PY | ~~0006 v1.1~~ → **KAPANDI** (21.07.2026) |
| ~~0002 O-1~~ | ~~Hedef segment önceliği~~ | PO | ~~0201~~ → **KAPANDI** (21.07.2026) |
| 0003 O-2 | MVP motor kapsamı | PO + TL | 0205 |
| 0004 O-2 | Örnekleme büyüklüğü n | TL | 0309 pilotu |
| ~~0205 O-1~~ | ~~D-03 Google yüzeyi kararı (bloklayıcı)~~ | PO + TL | ~~0205~~ → **KAPANDI** (21.07.2026) |

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | PY rolünün ortaklık tarafındaki muhatap tanımı | Ortaklık çerçevesi netleştiğinde rol eşlemesi yapılacak; kişi adı dokümana girmez. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: roller ve RACI, iki tipli karar süreci, D-01 karar kaydı, doküman yaşam döngüsü, ADR pratiği, kadanslar, faz kapısı kriterleri, Faz 0 kapanış tablosu ve konsolide bekleyen kararlar. |
| 1.1 | 21.07.2026 | D-03 karar kaydı eklendi; 0205 O-1 kapandı olarak işaretlendi. |
| 1.2 | 21.07.2026 | §8 kapanış tablosu düzeltildi: tüm durumlar Review olarak güncellendi (PO onayı bekleniyor). D-02 Kapandı olarak güncellendi (0304 ADR-002 onayıyla). Konsolide bekleyen kararlar listesinden D-02 çıkartıldı. |
| 1.3 | 21.07.2026 | D-04 karar kaydı eklendi (segment önceliği). 0002 O-1 kapandı olarak işaretlendi. |
| 1.4 | 21.07.2026 | D-05 karar kaydı eklendi (Mentiq ürün adı seçimi). 0006 O-3 kısmi kapandı (seçim yapıldı, protokol yürütülüyor). |
| 1.5 | 21.07.2026 | D-06 karar kaydı eklendi (marka tescil sahibi ve kapsamı). 0006 O-1 ve O-2 kapandı. Konsolide listede güncellendi. |
| 1.6 | 21.07.2026 | D-07 karar kaydı eklendi (self-serve kayıt politikası). 0201 O-2 ve 0202 O-2 kapandı. |
| 1.7 | 21.07.2026 | D-08 (benchmark kapsamı) ve D-09 (rol modeli) karar kayıtları eklendi. 0203 O-1 ve O-3 kapandı. |
| 1.8 | 21.07.2026 | D-10 (API kapsamı), D-11 (ilk değer eşikleri), D-12 (uyarı eşikleri) karar kayıtları eklendi. 0203 O-4, 0202 O-1 ve O-4 kapandı. |
| 1.9 | 21.07.2026 | D-13 (GitHub Projects), D-14 (GitHub Actions), D-15 (VM self-host) karar kayıtları eklendi. 0401 O-1, 0403 O-1 kapandı; 0304 O-1 ve 0402 O-1 yön kararı verildi. |
| 1.10 | 21.07.2026 | D-16 (SendGrid), D-17 (hotfix sınırları), D-18 (imaj imzalama), D-19 (RTO/RPO) karar kayıtları eklendi. 0304 O-2, 0401 O-4, 0403 O-3, 0402 O-2/O-3 kapandı. |
| 1.11 | 21.07.2026 | D-20 (kapsam eşikleri), D-21 (fixture kadansı), D-22 (yük testi), D-23 (mutasyon testi) karar kayıtları eklendi. 0404 O-1/O-2/O-3/O-4 kapandı. |
| 1.12 | 21.07.2026 | D-24 (self-serve ödeme UC-26), D-25 (görüşme erişimi), D-26 (grafik kütüphaneleri) karar kayıtları eklendi. 0203 O-2, 0201 O-3, 0304 O-3 kapandı. |
| 1.13 | 21.07.2026 | D-27 (MFA), D-28 (oturum süreleri), D-29 (üyelik erişim kapsamı) karar kayıtları eklendi. 0310 O-1/O-2, 0302 O-1 kapandı. |
| 1.14 | 21.07.2026 | D-30 (örnekleme), D-31 (anlamlılık), D-32 (devralma), D-33 (alarm) karar kayıtları eklendi. 0309 O-1/O-3, 0307 O-2, 0311 O-1 kapandı. |
| 1.15 | 21.07.2026 | D-34 (trend sınırı), D-35 (ULID), D-36 (çalışma alanı path), D-37 (oapi-codegen) karar kayıtları eklendi. 0302 O-2/O-4, 0306 O-1/O-4 kapandı. |
| 1.16 | 21.07.2026 | D-38 (Flutter reeval), D-39 (Gemini URI), D-40 (log saklama), D-41 (bölümleme), D-42 (dilim 1 bağdaştırıcısı) karar kayıtları eklendi. 0304 O-4, 0308 O-2, 0311 O-2/O-4, 0401 O-2 kapandı. |
| 1.17 | 21.07.2026 | D-43 (faz geçiş), D-44 (GTM), D-45 (SOC 2), D-46 (anında iletim), D-47 (sızma testi), D-48 (doküman kadansı) karar kayıtları eklendi. 0000 O-2, 0104 O-1/O-2, 0307 O-3, 0310 O-4, 0401 O-3 kapandı. |
| 1.18 | 21.07.2026 | D-49 (Evertune inceleme), D-50 (analist raporu), D-51 (bot listesi), D-52 (öneri kural kütüphanesi) karar kayıtları eklendi. 0103 O-2, 0105 O-1, 0308 O-3, 0309 O-4 kapandı. |
| 1.19 | 21.07.2026 | D-53 (walking skeleton), D-54 (saklama), D-55 (telafi/özet), D-56 (partial eşik), D-57 (dondurma) karar kayıtları eklendi. 0301 O-1, 0303 O-2, 0307 O-1, 0309 O-2, 0406 O-4 kapandı. |
| 1.20 | 21.07.2026 | D-58 (KVKK silme) karar kaydı eklendi. 0303 O-3 kapandı (0310 §6 kararı). 0308 O-1/O-4 takipte kalmaya devam ediyor. |
| 1.21 | 21.07.2026 | D-59 (perf eşikleri), D-60 (benchmark gizlilik), D-61 (okuma API), D-62 (saklama), D-63 (v1.1 turu), D-64 (hesap yeri), D-65 (engines modül), D-66 (monorepo), D-67 (lint) karar kayıtları eklendi. 0204 O-1/O-2/O-3/O-4, 0206 O-4, 0305 O-1/O-2/O-3/O-4 kapandı. |
| 1.22 | 21.07.2026 | D-68 (uptime SLO), D-69 (tarihçe eşiği), D-70 (EN tetikleyici), D-71 (güvenlik kadansı) karar kayıtları eklendi. 0004 O-3, 0206 O-1/O-3, 0405 O-2 kapandı. |
| 1.23 | 21.07.2026 | D-72 (worker ölçekleme), D-73 (kilit kaybı), D-74 (ADR-005 Streams), D-75 (DLQ oynatma), D-76 (izolasyon alt kümesi), D-77 (tren günü) karar kayıtları eklendi. 0301 O-2/O-3, 0303 O-1, 0307 O-4, 0403 O-2, 0406 O-2 kapandı. |
| 1.24 | 21.07.2026 | D-78 eklendi: AN araştırma eylem planı (6 soru, pilot öncesi, 0201 görüşmeleri + masabaşı). |
| 1.25 | 21.07.2026 | D-79 (pilot profili), D-80 (MVP motorlar), D-81 (sentiment V1 dışı), D-82 (1.0.0 tanımı) karar kayıtları eklendi. 0003 O-1/O-2, 0004 O-1, 0005 O-2, 0406 O-1 kapandı. |
| 1.26 | 21.07.2026 | D-83 (token ömrü), D-84 (Önem-SLA), D-85 (VM sağlayıcı), D-86 (staging yedek) karar kayıtları eklendi. 0306 O-2, 0405 O-1, 0402 O-1, 0403 O-4 kapandı. |
| 1.27 | 21.07.2026 | D-87 (coğrafi odak), D-88 (örnekleme), D-89 (skor bileşen adları), D-90 (sızma testi), D-91 (sürüm notları) karar kayıtları eklendi. 0002 O-2, 0004 O-2, 0405 O-3 kapandı. 0005 O-1, 0406 O-3 eylem planı olarak kaydedildi. |
