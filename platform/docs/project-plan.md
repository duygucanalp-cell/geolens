# Proje Planı — GeoLens Platform

| Alan | Değer |
|---|---|
| Doküman ID | project-plan |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0000 (Master Plan), 0205 (MVP), 0206 (Roadmap), archive/avip-v1/0401 |

---

## 1. Amaç

Bu doküman GeoLens Platform geliştirme takvimini, ekip sorumluluklarını ve çıkış kapılarını tanımlar. Kaynağı AVIP arşivindeki 0401-development-process.md'dir; GeoLens Platform'un mevcut doküman yapısına uyarlanmıştır.

---

## 2. Ekip Yapısı

| Kişi | Rol | Dilim 1-2 | Dilim 3-4 | Pilot açılış |
|:----:|:----:|:---------:|:---------:|:------------:|
| **Siz** | TL + CEO | 🟢 | 🟢 | 🟢 |
| **Backend #1** | Platform & Identity | 1 | 1 | 1 |
| **Backend #2** | Geniş (Go+React temel) → Insight → Sertleştirme | 1 | 1 | 1 |
| **Frontend** | React/TypeScript SPA | — | 1 | 1 |
| **DevOps/SRE** | Ortam, CI/CD, monitoring | — | — | 1 |
| **Analist (AN)** | Araştırma, dokümantasyon, görüşmeler | 1 | 1 | 1 |
| **Toplam** | | **4** | **5** | **6** |

---

## 3. Genel Bakış — 4 Dilim, 16 Hafta

| Dilim | Haftalar | Ekip | Renk | Çıktı | Tarih Aralığı |
|:-----:|:--------:|:----:|:----:|-------|:-------------:|
| **🏗️ 1 · İskelet** | H0–H4 | 4 kişi | 🏗️ | Tek motorlu demo (Perplexity) | 3 Ağu — 4 Eyl 2026 |
| **📡 2 · Ölçüm Tam** | H5–H8 | 4 kişi | 📡 | 3 motorlu skor + site denetimi | 7 Eyl — 2 Eki 2026 |
| **💌 3 · Değer Halkası** | H9–H12 | 5 kişi 🧑‍💻 | 💌 | E-posta + PDF + öneri akışı | 5 Eki — 30 Eki 2026 |
| **🔒 4 · Sertleştirme** | H13–H16 | 5 kişi | 🔒 | Pilot çıkış kapısı yeşil | 2 Kas — 27 Kas 2026 |

> **Not:** Başlangıç tarihleri varsayımsaldır. Kesin başlangıç işe alımların tamamlanmasına bağlıdır.

---

## 4. Dilim 1 · İskelet (H0–H4)

**Hedef:** Uçtan uca demo — kullanıcı kaydolur → panel oluşturur → ölçüm tetiklenir → Perplexity yanıtı döner → 4 bileşenli skor hesaplanır → panoda görünür.

**Bağımlılık zinciri:** platform/db → platform/httpmw → identity → config → measure → governance

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (Geniş) | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:------------------:|:------------:|:----------------:|
| **H0** | Go modül iskeleti; measure arayüzü + engines kayıt defteri tasarımı | platform/db: PostgreSQL havuz + sqlc kurulumu; ilk migration (kiracı, kullanıcı); Docker Compose (PG, Redis, S3) | cmd/api iskeleti; platform/telemetry: OTel kurulumu; Makefile + golangci-lint yapılandırması | Tüm doküman setini okuma; ajans görüşme takvimi oluşturma; Evertune başlangıç | 🟢 Çalışan dev ortamı + ilk migration |
| **H1** | platform/httpmw: panik kurtarma, request ID; measure api.go tamamlama; Perplexity bağdaştırıcı iskeleti (Execute) | identity: kullanıcı kaydı, JWT oturum, giriş/çıkış uçları; httpmw: kimlik doğrulama, kiracı bağlamı | cmd/api: httpmw zincirini bağlama; config: marka tanımı, panel iskeleti; cmd/scheduler iskeleti | İlk 3 ajans görüşmesi (Sheltron, Cremicro, Seobaz); güncelleme notları | 🟢 Çalışan API + kimlik doğrulama + Perplexity istek |
| **H2** | Perplexity bağdaştırıcı tam (alıntı çıkarma, hata sınıfları); measure/calc: calculation_run + temel skor (varlık payı + konum + kaynak) | identity: RBAC tam, RLS politikaları; platform/queue: Redis Streams + outbox dağıtıcı; S3 storage sarmalayıcı | config: panel tanımı + prompt seti yönetimi; scheduler: izleme planı tarama, idempotent iş üretimi; cmd/worker iskeleti | Ajans görüşmeleri devam (Webtures, Zeo); skor bileşen adları; dokümantasyon | 🟢 Ölçüm işi kuyruğa atılabiliyor |
| **H3** | Scoring engine tam: 4 bileşen (varlık, konum, kaynak, rakip) + GA + fidelite; ham yanıt → skor pipeline | governance: denetim yazıcısı, kota iskeleti, usage_records; platform hardening (hata yönetimi, timeouts) | Worker: kuyruktan iş okuma + measure çağrısı + sonuç kalıcılaştırma; web/ SPA: React iskeleti + skor kartı prototipi | Ajans görüşmeleri analizi; sürüm notu şablonları; README güncelleme | 🟢 Skor hesaplanıyor, governance temel hazır |
| **H4** | Uçtan uca pipeline entegrasyonu; hata ayıklama; demo senaryosu hazırlığı | Testler (birim + testcontainers); CI/CD ilk versiyon; doküman-kod senkronu | web/ SPA: skor kartı + trend grafiği; demo ortamı; API dokümantasyonu | Demo desteği; v1.1 kuyruğu kayıtları; Dilim 1 dokümantasyon kapanışı | 🔷 **Canlıda uçtan uca demo — tek ölçüm, etiketli skor** |

### İlk Çıktı Takvimi

| Ne zaman | Ne çıktı | Kullanılabilirlik |
|:--------:|----------|:-----------------:|
| H0 sonu | Dev ortamı + migration | Geliştirici iç kullanım |
| H1 sonu | API + auth + Perplexity istek | API tüketicileri |
| H2 sonu | Ölçüm işi → kuyruk | Scheduler çalışıyor |
| H3 sonu | Skor pipeline | Measure çalışıyor |
| **H4 sonu** | **Uçtan uca demo** | **Canlı gösterim** |

### Çıkış Kapısı Kriteri

Kullanıcı kaydolur → panel oluşturur → ölçüm tetiklenir → Perplexity yanıtı başarıyla döner → 4 bileşenli skor hesaplanır → panoda görünür. Korelasyon zinciri (request_id → job_id → calculation_run_id) logda izlenebilir.

---

## 5. Dilim 2 · Ölçüm Tam (H5–H8)

**Hedef:** Üç motor (Perplexity + ChatGPT + Gemini) için ayrı ayrı skor. Site denetim bulguları saniyeler içinde.

**Bağımlılık zinciri:** engines kayıt defteri (Dilim 1) → ChatGPT → Gemini → GA tam → site denetimi

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (Geniş) | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:------------------:|:------------:|:----------------:|
| **H5** | ChatGPT bağdaştırıcısı (OpenAI Responses API + web araması; alıntı çıkarma, hata sınıfları, kayıt defteri) | GA mekaniği tamamlama: GA hesaplama, fidelite etiketleme, partial yayın kuralları | Pano: skor kartı bileşeni + motor kırılım sekmeleri + panel seçici | Ajans görüşmeleri (Aora Digital, Digipeak); öneri kural kütüphanesi içerik başlangıç | 🟢 ChatGPT çalışıyor, GA mekaniği hazır |
| **H6** | Gemini bağdaştırıcısı (Gemini API + Google Search grounding; URI çözümleme, yönlendirme takibi, kayıt defteri) | Örnekleme altyapısı tam: n=3, temp=0, bayraklı oran eşiği; örnekleme birim testleri | Pano: trend grafiği (Recharts), motor karşılaştırma görünümü | Ajans görüşmeleri analizi; Evertune tamamlama | 🟢 Gemini çalışıyor, 3 motor kayıtlı |
| **H7** | Site denetim bileşeni: robots.txt bot izinleri, SSR sinyalleri, SSRF korumaları, bot listesi | Üç motorlu pipeline entegrasyonu; entegrasyon testleri (testcontainers); CI/CD güncelleme | Denetim bulguları ekranı; site denetim API uçları; pano detay görünümleri | Skor bileşen adları tamamlama; sürüm notu şablonları başlangıç | 🟢 Site denetimi çalışıyor, 3 motor entegre |
| **H8** | Uçtan uca test (3 motorlu panel → skor → pano); hata ayıklama; demo senaryosu hazırlığı | Performans testi; hardening; doküman-kod senkronu; v1.1 kuyruğu kayıtları | Demo ortamı; API dokümantasyonu; pano son rötuşlar + kullanıcı testi | Demo desteği; Dilim 2 dokümantasyon kapanışı; v1.1 kuyruğu kayıtları | 🔷 **Üç motorlu panel skoru + saniyeler içinde denetim bulgusu** |

### Çıkış Kapısı Kriteri

Kullanıcı panelinde üç motor (Perplexity + ChatGPT + Gemini) için ayrı ayrı skor görünür. Site denetimi çalıştırılır ve bulgular saniyeler içinde panoda listelenir. Korelasyon zinciri her motor için ayrı izlenebilir.

---

## 6. Dilim 3 · Değer Halkası (H9–H12)

**🧑‍💻 Frontend ekibe katılır.** Ekip 5 kişi. Backend #2 React sorumluluğunu Frontend'e devreder, **insight** (öneri motoru) ağırlıklı çalışır.

**Hedef:** Öneri akışı, derin bağlantılı e-posta özeti, PDF rapor, anlık uyarı, NG10 filtresi.

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (İnsight) | Frontend (Yeni) | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:--------------------:|:---------------:|:------------:|:----------------:|
| **H9** | Delivery çekirdek: kanal yönetimi, bildirim tipleri, e-posta gönderim altyapısı (SMTP/API) | Governance raporlama uzantıları: usage_records sorguları, kota limit raporları; PDF render altyapısı (şablon motoru) | Insight iskeleti: kural tabanlı öneri motoru (koşul deseni → öneri şablonu), kural kayıt defteri | Ortam kurulumu; kod tabanını öğrenme; bildirim/uyarı ayarları sayfası (React) | Öneri kural kütüphanesi içerik tamamlama; NG10 uygunluk denetimi başlangıç | 🟢 E-posta gönderimi + öneri iskeleti |
| **H10** | Haftalık özet/digest pipeline; e-posta şablonları (derin bağlantılı: skor, trend, öneri linkleri) | PDF rapor motoru: şablon + veri birleştirme, S3 depolama, imzalı URL üretimi | Öneri motoru tam: kural değerlendirme, NG10 filtresi, tekilleştirme, öneri API uçları | Öneri akışı bileşeni (skor kartı altında); rapor görüntüleme/indirme sayfası | NG10 denetimi tamamlama; kullanıcı dokümantasyonu taslak | 🟢 Haftalık özet e-postası + öneri API |
| **H11** | Uyarı sistemi: anlık bildirim tetikleme, kanal dağıtımı (e-posta/pano), uyarı tercihleri entegrasyonu | Delivery API uçları tamamlama; scheduler entegrasyonu (zamanlanmış gönderim); CI/CD güncelleme | Insight API tam: öneri işaretleme (uygulandı/reddedildi), M4 telemetri yazımı; hata ayıklama | White-label PDF önizleme; uyarı tercihleri sayfası; bildirim geçmişi görünümü | Sürüm notu taslağı; dokümantasyon güncelleme | 🟢 Uyarı sistemi + white-label PDF |
| **H12** | Uçtan uca test (ölçüm → öneri → uyarı → e-posta özeti → PDF rapor); demo senaryosu hazırlığı | Entegrasyon testleri (delivery + insight); CI/CD pipeline olgunlaştırma; doküman-kod senkronu | Hata ayıklama; performans iyileştirme; API dokümantasyonu | Demo ortamı; son rötuşlar; kullanıcı testi (iç) | Demo desteği; Dilim 3 dokümantasyon kapanışı; v1.1 kuyruğu kayıtları | 🔷 **Derin bağlantılı e-posta özeti + PDF rapor + öneri akışı canlı** |

### Çıkış Kapısı Kriteri

Kullanıcı panoda öneri akışını görür, haftalık özet e-postası derin bağlantılarla gelir, PDF rapor indirilebilir, anlık uyarı tetiklenebilir. Öneriler NG10 filtresinden geçmiş ve iddia dili kurallarına uygundur.

---

## 7. Dilim 4 · Sertleştirme (H13–H16)

**Hedef:** Pilot çıkış kapısı (0205 §8 — 7 kriter) yeşil. Güvenlik sertleştirmesi, alarm seti, kalibrasyon provası.

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (Sertleştirme) | Frontend | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:-------------------------:|:--------:|:------------:|:----------------:|
| **H13** | Kripto-silme altyapısı: zarf anahtarı oluşturma, S3 şifreleme entegrasyonu, anahtar yönetim arayüzü | Denetim zinciri doğrulama rutini: zincir tarama, kök karma saklama; izolasyon negatif test paketi | Sır yönetimi ve rotasyon altyapısı: kasa entegrasyonu, çift anahtar penceresi, rotasyon runbook kodlaması | Güvenlik ayarları sayfası; KVKK veri silme talebi arayüzü | Pilot çıkış kapısı kontrol listesi hazırlığı; güvenlik dokümantasyonu | 🟢 Kripto-silme + zincir doğrulama |
| **H14** | 0311 alarm seti kurulumu: kritik alarmlar (izolasyon reddi, zincir kopukluğu, determinizm, bütçe tavanı, DLQ); alarm → runbook bağlantısı | Metrik kataloğu implementasyonu: API, kuyruk, motor, hesap metrikleri; Prometheus metrik uçları | Rotasyon prosedürleri: oturum/derin bağlantı anahtarı rotasyonu; sır hijyeni log kontrolü; güvenlik CI/CD kapıları | Alarm ve metrik panosu (temel); sistem durumu sayfası | Operasyon runbook'ları taslağı; v1.1 kuyruğu kayıtları | 🟢 Alarm seti aktif, metrikler akıyor |
| **H15** | Kalibrasyon provası: örnekleme parametreleri (n=3, temp=0), alarm eşikleri, GA doğrulama, partial yayın, anlamlılık eşikleri | Cache stratejisi: Redis pano önbelleği, ETag desteği; yedekleme/DR çerçevesi (PITR, outbox yeniden inşa); performans testi | Güvenlik testleri: RBAC matrisi, izolasyon negatif testleri, sızma testi; CI/CD güvenlik kapıları | Kullanıcı kabul testi ortamı; son kullanıcı dokümantasyonu; onboarding akış prototipi | Pilot dokümantasyonu; kullanıcı kılavuzu; pilot kiracı onboarding planı | 🟢 Kalibrasyon provası yeşil, güvenlik testleri tam |
| **H16** | Pilot çıkış kapısı: 0205 §8'deki 7 kriterin tamamının doğrulanması; pilot onboarding hazırlığı; eksik kalan son işlerin kapatılması | Son güvenlik taraması; doküman-kod senkronu; v1.1 kuyruğu nihai kayıtları; PO onayına hazırlık | Tüm dokümanların Review → Approved geçişi için PO'ya hazırlık; kalan son açık soruların kapatılması | Pilot kullanıcı arayüzü son kontrolleri; onboarding yardım sayfaları | Pilot hazırlık: kiracı davetleri, onboarding dokümanları, v1.1 düzeltme turu kapanışı | 🔷 **Pilot çıkış kapısı ön kontrol listesi (0205 §8) yeşil — pilota hazır** |

### Çıkış Kapısı Kriteri (0205 §8)

| # | Kriter |
|:-:|--------|
| 1 | Sert kural ihlali sıfır: NFR-1, NFR-6, NFR-7 pilot boyunca istisnasız sağlandı |
| 2 | Kalibre edilen performans hedefleri (NFR-9) ardışık son iki haftada karşılandı |
| 3 | P2 ve P3 persona kartları saha verisiyle doğrulandı |
| 4 | K1 maliyet gerçekleşmesi panel modeli öngörüsüyle uyumlu |
| 5 | Motor kapsamı üretimde karara uygun çalışıyor (ChatGPT + Gemini + Perplexity) |
| 6 | Pilot kiracılarından en az bir P3 ve bir P2 referans sinyali alındı |
| 7 | Güvenlik kapanışı tamamlandı; açık kritik/yüksek bulgu sıfır |

---

## 8. Bağımlılık Zinciri (Kritik Yol)

```
H0: platform/db ──→ H1: httpmw → identity ──→ H2: RBAC/RLS
                                                      │
H0: measure ──→ H1: Perplexity ──→ H2: calc ──→ H3: scoring ──→ H4: demo
                                                                      │
H5: ChatGPT ──→ H6: Gemini ──→ H7: 3 motor ──→ H8: demo
                                                      │
H9: delivery ──→ H10: digest ──→ H11: uyarı ──→ H12: demo
                                                          │
H13: kripto-silme ──→ H14: alarm ──→ H15: kalibrasyon ──→ H16: pilot kapısı
```

### Kritik Karar Noktaları

| Zaman | Karar | Blokaj |
|:------|:------|:-------|
| **H0 öncesi** | Backend #1 ve Analist işe alımı tamam | Dilim 1 başlayamaz |
| **H4–H5 arası** | ChatGPT/Gemini API anahtarları hazır | Dilim 2 başlayamaz |
| **H8–H9 arası** | Frontend işe alımı tamam + e-posta servisi seçilmiş | Dilim 3 başlayamaz |
| **H12–H13 arası** | Kasa/KMS kararları alınmış | Sertleştirme başlayamaz |
| **H15–H16 arası** | PO tüm dokümanları Approved yapmış | Pilot kapısı açılamaz |

---

## 9. Ekip Büyüme Takvimi

| Tarih | Olay |
|:-----:|------|
| **H0** (3 Ağu) | Backend #1 + Analist işe başlar — 4 kişi |
| **H9** (5 Eki) | Frontend katılır — 5 kişi |
| **Pilot öncesi** (Ara) | DevOps/SRE katılır — 6 kişi |

---

## 10. Çıkış Kapıları Özeti

| Kapı | Hf | Kriter |
|:----:|:--:|--------|
| **Dilim 1** | H4 | Kaydol → panel → ölçüm → Perplexity → 4 bileşenli skor → panoda. Korelasyon zinciri logda. |
| **Dilim 2** | H8 | 3 motor ayrı skor. Site denetim bulguları saniyeler içinde. Her motor için korelasyon. |
| **Dilim 3** | H12 | Öneri akışı. Derin bağlantılı e-posta özeti. PDF. Anlık uyarı. NG10 filtresi. |
| **Dilim 4** | H16 | 0205 §8: 7 kriterin tamamı yeşil. Pilot başlangıcı. |

---

## 11. Açık Sorular

| ID | Soru | Not |
|:--:|------|-----|
| O-1 | E-posta gönderim servisi seçimi (SendGrid, AWS SES, Resend) | Dilim 3 öncesi (H8–H9 arası) karar verilmeli. TL kararı. |
| O-2 | Sır kasası/KMS seçimi (HashiCorp Vault, AWS KMS, cloud-native) | Dilim 4 öncesi (H12–H13 arası) karar verilmeli. TL+DevOps. |
| O-3 | PDF render motoru (chromedp, wkhtmltopdf, Go şablon) | Dilim 3 öncesi karar verilmeli. Backend #1. |

---

## Kaynaklar

- AVIP Development Process: `archive/avip-v1/0401-development-process.md`
- GeoLens Master Plan: `platform/docs/00-overview/0000-master-plan.md`
- MVP Kapsamı: `platform/docs/02-product/0205-mvp.md`
- Yol Haritası: `platform/docs/02-product/0206-roadmap.md`

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform proje planı. 4 dilim (16 hafta), haftalık kişi bazlı sorumluluk tabloları, ekip yapısı, bağımlılık zinciri, çıkış kapısı kriterleri. AVIP arşivinden uyarlanmıştır. |
