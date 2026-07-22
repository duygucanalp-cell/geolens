# 0402 · Environments & Docker

| Alan | Değer |
|---|---|
| Doküman ID | 0402 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.3 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman: ortam topolojisi, Docker düzeni, altyapı kararları |
| İlişkili | 0304 (O-1, O-2), 0307, 0308, 0310, 0311 (girdi); 0403, 0406 (çıktı); N4, N5, N12 |

---

## 1. Amaç ve Kapsam

Bu doküman çalışma ortamlarını, konteyner düzenini ve önceki fazlardan devredilen altyapı kararlarını sabitler: veri yerleşimi politikası, ortam topolojisi, imaj ve compose yapısı, sır yönetimi altyapısı, ağ/TLS, kapasite modeli, e-posta ve yedekleme altyapısı. Kapsam dışı: CI/CD boru hattı (0403), altyapı-kod dosyalarının kendisi (depoda deploy/), maliyet rakamları (set genel kuralı) ve sürüm/yayın süreci (0406).

## 2. Sağlayıcı ve TR Veri Yerleşimi Kararı (0304 O-1 kapanışı)

> **Politika kararı (bu doküman onayıyla, Tip 1):** AVIP'in birincil veri yerleşimi Türkiye'dir. Kişisel veriler ve kiracı içerik verileri (promptlar, ham yanıt arşivi, raporlar) Türkiye'de veya Türkiye veri yerleşimi taahhüdü veren bölgede barındırılır; KVKK duruşu ve kurumsal satış hazırlığı (P1 kapısı) gerekçedir. Sağlayıcı kısa listesi şu şartlarla değerlendirilir: S3-uyumlu depolama + nesne kilidi, yönetilen PostgreSQL (PITR dahil), KMS/kasa hizmeti, TR bölge veya eşdeğer taahhüt. Nihai sağlayıcı seçimi tedarik aşamasında ayrı Tip 1 kaydı olarak 0007 defterine işlenir (O-1); mimari bu karardan bağımsız kalır (ADR-001 arayüz bağlayıcılığı).

| Seçenek sınıfı | Güçlü | Dikkat |
|---|---|---|
| Küresel bulut, TR/taahhütlü bölge | Yönetilen PG + KMS olgunluğu, nesne kilidi hazır | Bölge yetenek eşitliği doğrulanmalı; maliyet |
| TR yerel bulut sağlayıcı | Yerleşim tartışmasız; kurumsal algı güçlü | Yönetilen PG/KMS derinliği kısa listede test edilir |
| Self-host (MinIO + öz yönetim PG) | Tam kontrol | W4 ekip için operasyon ve PITR/kilit yükü; yalnız yedek plan |

## 3. Ortam Topolojisi

Üç ortam vardır: local (geliştirici makinesi, compose), staging ve production. Ortam eşitliği ilkesi geçerlidir: aynı imajlar her ortamda koşar, fark yalnız yapılandırmadadır. Staging üretim verisi taşımaz; sentetik ve anonim demo veri setiyle çalışır (O-4) ve motor çağrıları düşük kotalı gerçek bağdaştırıcılarla veya kayıttan-oynatma sahteleriyle yapılır (0404 kararı). PR başına geçici ortam V1'de kurulmaz; staging tek doğrulama sahnesidir. Ortamlar arası sır ve anahtar kümeleri tamamen ayrıktır (0308 §9); production erişimi kısıtlı, kayıtlı ve ayrıcalıklı eylem olarak denetimlidir (0310).

## 4. Konteyner ve Compose Düzeni

| İmaj | İçerik ve kurallar |
|---|---|
| app (api/worker/scheduler) | Tek çok-aşamalı Dockerfile: Go derleyici aşaması → küçük çalışma imajı; üç süreç aynı imajdan giriş komutuyla ayrışır (ADR-003); imaj sürümü git etiketiyle |
| renderer | Headless Chromium ayrı imaj; worker:report tarafından kullanılır; izole süreç ve kaynak sınırı (FR-F4) |
| web | SPA statik derlemesi; hafif sunum imajı veya CDN dağıtımı (0403 kararı) |
| local bağımlılıklar | Compose: postgres, redis, minio, e-posta yakalayıcı (mailpit sınıfı), app süreçleri, web; tek komutla ayağa kalkar, seed komutu şablon kütüphanesini yükler; tüm servislerde sağlık kontrolü |

Local compose, migration'ları başlangıçta uygular ve 0403'teki entegrasyon test altyapısıyla (testcontainers) aynı sürümleri kullanır; ortamlar arası sürüm kayması tek listeden yönetilir.

## 5. Yapılandırma ve Sır Yönetimi

Yapılandırma on iki faktör modelindedir: tüm ayarlar ortam değişkeninden gelir, uygulama açılışta tek noktada şema doğrulaması yapar (0305 app; eksik/bozuk değişkende açılmaz). Ortam değişkeni envanteri depoda belgelenir ve local için gerçek sır içermeyen örnek dosya tutulur. Staging ve production sırları sağlayıcının kasa/KMS hizmetinden yüklenir (N4); zarf anahtarları (0310 §6) KMS'te yaşar ve uygulamaya yalnız kullanım arayüzüyle açılır. Sır rotasyonları 0310 §8 sınıflarına göre kasa üzerinden yürütülür; hiçbir sır imaja, koda veya loga girmez.

## 6. Ağ, TLS ve Erişim

Kenar katmanı yük dengeleyici/ters vekildir: TLS burada sonlanır, sertifikalar otomatik yenilenir ve süre yaklaşımı alarmı 0311 envanterindedir. Uygulama servisleri özel ağda koşar; PostgreSQL ve Redis dışa kapalıdır, yalnız uygulama ağından erişilir; S3 erişimi özel uç noktadan yapılır. Production veritabanına insan erişimi istisnadır: kayıtlı geçit üzerinden, gerekçeli ve denetim izli (0310 ayrıcalıklı eylem); rutin operasyon ihtiyaçları runbook'lara ve salt-okur telemetriye yönlendirilir. Giden trafik (motor API'leri, e-posta, webhook) kontrollü çıkış noktasından geçer; bu, bütçe ve anomali izlemesini (K1, 0311) kolaylaştırır.

## 7. Kapasite ve Ölçekleme Modeli

Ölçekleme süreç kopyasıyladır (ADR-003): api yatay kopyalarla LB arkasında; worker profilleri (measure, report, notify) bağımsız kopya sayılarıyla ölçeklenir; scheduler ve outbox dağıtıcısı tekil kalır (kilit korumalı). Chromium render eşzamanlılığı ayrı sınırlıdır [K] ve rapor kuyruğu yaşlanması 0311 sinyaliyle kapasite kararına bağlanır. PG bağlantı havuzu boyutu süreç kopya sayısıyla birlikte planlanır (havuz × kopya toplamı sunucu sınırının altında kalır). Başlangıç kopya sayıları [K] pilot yüküne göre belirlenir ve aylık kapasite raporuyla (0311 §9) gözden geçirilir; ölçek kararları trend tabanlıdır.

## 8. E-posta Altyapısı (0304 O-2 kapanışı)

Politika kararı: e-posta gönderimi yönetilen işlemsel sağlayıcı üzerinden yapılır; kendi SMTP sunucusunu işletmek teslim edilebilirlik riski nedeniyle reddedilir. Sağlayıcı şartları: API tabanlı gönderim, bounce/şikayet webhook geri bildirimi (delivery bağlamına düşer), TR alıcı performansı, alan doğrulama araçları. Gönderim alanı için SPF, DKIM ve DMARC zorunludur; alan ısındırma planı pilot öncesi başlar (düşük hacimden kademeli artış). Haftalık özet ve uyarı e-postaları derin bağlantı token'larıyla (0306 §8) bu altyapıdan çıkar; teslim hataları 0307 §7 yeniden deneme ayrımına tabidir. Sağlayıcı kısa listesi ve seçim TL yürütümünde tamamlanır (O-2); politika bu onayla kapanır.

## 9. Yedekleme Altyapısı ve RTO/RPO

0311 §8 çerçevesinin altyapı karşılığı: yönetilen PostgreSQL hizmetinde sürekli arşiv ve PITR şarttır (kısa liste eleme kriteri); otomatik geri yükleme testi ayrı ortamda kadanslı koşar [K]. S3 çapraz kopya hedefi veri yerleşimi politikasına uyar: ikinci kopya da Türkiye kapsamında veya taahhütlü bölgede tutulur. Başlangıç hedef önerisi: RPO 15 dakika sınıfı (PITR ile), RTO 4 saat sınıfı (platform bütünü); değerler [K] işaretlidir, 0311 O-3 kapsamında teyit edilir ve tatbikatla kanıtlanır. Zarf anahtarlarının KMS yedeği sağlayıcının anahtar dayanıklılık mekanizmasına ek olarak ayrı erişim politikasıyla korunur; anahtar imhası yalnız KVKK silme prosedüründen tetiklenebilir (0310 §6).

## 10. AVIP için Çıkarımlar

1. İki devir kapandı: veri yerleşimi politikası (0304 O-1) ve e-posta politika kararı (0304 O-2); sağlayıcı ve e-posta kısa liste seçimleri operasyonel Tip 1/Tip 2 kayıtları olarak 0007'ye işlenecek.
2. 0403 girdileri hazır: imaj adları ve derleme sınırları, ortam hedefleri, compose ile test altyapısının sürüm birliği.
3. 0311 bağları yerine oturdu: sertifika alarmı, kapasite raporu girdileri, geri yükleme tatbikat ortamı.
4. Pilot öncesi altyapı kontrol listesi türetilebilir durumda: sağlayıcı sözleşmesi, alan ısındırma, KMS kurulumu, staging sentetik seti, RTO/RPO tatbikatı.
5. Kalan Faz 4 sırası: 0403 CI/CD Pipeline → 0404 Test Strategy → 0405 Security Review & OWASP Checklist → 0406 Release & Versioning; ardından v1.1 birleşik düzeltme turu penceresi.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~Sağlayıcı kısa listesinin sonuçlandırılması~~ | ~~§2 şartlarıyla; Tip 1 kayıt; TL + PY.~~ |
| ~~O-1~~ | ~~Sağlayıcı ve altyapı yaklaşımı~~ | ✅ **KAPANDI**: Küresel bulut TR bölge (AWS/GCP/Azure). Self-host VM + yönetilen PG/KMS. Veri yerleşimi TR bölge veya eşdeğer taahhütle sağlanır. Nihai sağlayıcı seçimi TL yürütümünde Tip 1 karar olarak 0007'ye işlenir. 0007 D-85. |
| ~~O-2~~ | ~~E-posta sağlayıcı kısa listesi ve seçimi~~ | ~~§8 şartlarıyla; TL.~~ |
| ✅ O-2 | E-posta sağlayıcı seçimi | **KAPANDI** (21.07.2026): SendGrid. API tabanlı, bounce/şikayet webhookları. TR alan ısındırma planı pilot öncesi başlayacak. |
| ~~O-3~~ | ~~RTO/RPO başlangıç değerlerinin teyidi [K]~~ | ~~0311 O-3 ile birlikte; PO + TL.~~ |
| ✅ O-3 | RTO/RPO başlangıç değerleri [K] | **KAPANDI** (21.07.2026): RPO 1 saat, RTO 8 saat. VM self-host ile uyumlu. Pilot verisiyle kalibre edilecek. |
| O-4 | Staging sentetik veri setinin tasarımı | Demo senaryolarını kapsar; kişisel veri içermez; AN + TL. |

---

## Kaynaklar

- 0304 Technology Selection · O-1/O-2 devirleri, ADR-001 arayüz bağlayıcılığı
- 0310 Security & Multi-Tenancy · kasa/KMS, zarf anahtarları, ayrıcalıklı erişim kuralları
- 0311 Observability & Operations · yedekleme/DR çerçevesi, kapasite göstergeleri, alarm bağları
- 0307/0308 · işçi profilleri ve replika modeli, ortam bazlı anahtar ayrımı
- 0204 PRD · N4, N5, N12 (altyapı karşılıkları)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: TR veri yerleşimi politika kararı (Tip 1) ve sağlayıcı şart seti, üç ortamlı topoloji ve eşitlik ilkesi, dört imajlı konteyner/compose düzeni, kasa/KMS tabanlı sır yönetimi, ağ/TLS ve erişim kuralları, süreç kopyalı kapasite modeli, yönetilen e-posta politika kararı, PITR + çapraz kopya + RTO/RPO başlangıç önerileri. |
| 1.1 | 21.07.2026 | O-1 yön kararı: VM tabanlı self-host (TR sağlayıcı). GitHub Actions + GitHub Projects seçildi. |
| 1.2 | 21.07.2026 | O-2 kapandı: SendGrid. O-3 kapandı: RPO 1sa / RTO 8sa. 0007 D-16, D-19. |
| 1.3 | 21.07.2026 | O-1 kapandı: küresel bulut TR bölge (self-host VM + yönetilen PG/KMS). 0007 D-85. |