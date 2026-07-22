# 0207 · Özellik Kataloğu

| Alan | Değer |
|---|---|
| Doküman ID | 0207 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0204, 0205, 0206, 0004, 0301-0311 |

---

## 1. Amaç

Bu doküman, GeoLens Platform V1'in tüm özelliklerini tek bir katalogda toplar. Her özellik;

- hangi gereksinime (FR/NFR) karşılık geldiğini,
- 0205 MVP kesitindeki durumunu (tam/daraltılmış/açık),
- 0206 yol haritasındaki hedef penceresini (MVP/HT1/HT2/Kurumsal/Ufuk),
- paket erişimini (Free/Pro/Business/Enterprise)

tek tabloda gösterir. Katalog, Faz 3 mimari dokümanlarına (0301-0311) girdi sağlar, test stratejisi (0404) için test edilecek yüzeyleri listeler ve roadmap kararlarının özellik düzeyindeki dağılımını görünür kılar.

> **Tasarım filtresi bağlantısı:** Bu doküman özellikle **F2** (ölçek — tek katalogda tüm özelliklerin izlenebilir olması) ve **F6** (kategori — katalogun GAVF standart bağlantısını göstermesi) filtrelerine kanıt sağlar.

---

## 2. Katalog Yapısı

Her özellik aşağıdaki alanlarla kaydedilir:

| Alan | Anlamı |
|------|--------|
| **ID** | FR-/NFR- kodu (0204) |
| **Özellik** | Kısa, kullanıcı odaklı ad |
| **Açıklama** | Bir cümlelik işlev tanımı |
| **MVP Durumu** | 🟢 Tam / 🟡 Daraltılmış / 🔴 Açık (0205) |
| **Pencere** | Hedef yol haritası penceresi (0206) |
| **Paket** | Free / Pro / Business / Enterprise |
| **Bağımlılık** | Varsa teknik veya ürün bağımlılığı |
| **UC** | İlgili kullanım senaryosu (0203) |

---

## 3. Özellik Kataloğu

### 3.1 Kimlik ve Kiracı Yönetimi

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-A1 | Self-serve kayıt | E-posta doğrulamalı kayıt, otomatik kiracı provizyonu, ödeme bilgisi istenmez | 🟢 | MVP | Free |
| FR-A2 | Üye davet/rol yönetimi | Kiracı yöneticisi üye davet edebilir, rol atayabilir (yönetici/editör/izleyici) | 🟢 | MVP | Pro+ |
| FR-A3 | Oturum yönetimi | Güvenli giriş/çıkış, parola sıfırlama akışı | 🟢 | MVP | Tümü |
| FR-A4 | SSO/SAML | Kurumsal tek oturum açma (SAML/SSO) | 🔴 | Kurumsal | Enterprise |
| FR-A5 | Paket hakları | Sunucu tarafında hak denetimi, kapsam dışı işlev çağrıları reddedilir | 🟢 | MVP | Tümü |
| FR-A6 | Self-serve yükseltme | Paket yükseltme akışı, ödeme altyapısı | 🔴 | HT2 | Tümü |

### 3.2 Yapılandırma ve Kurulum

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-B1 | Marka/rakip tanımı | Marka, rakip ve pazar (TR/EN) tanım arayüzü | 🟢 | MVP | Tümü |
| FR-B2 | Prompt seti kurulumu | TR-öncelikli sektör şablonlarından prompt seti kurulumu, etiketleme | 🟢 | MVP | Tümü |
| FR-B3 | Kurulum sihirbazı | Adım adım kurulum, yarım kalanı sürdürme | 🟢 | MVP | Tümü |
| FR-B4 | Site erişim denetimi | Bot izinleri, SSR, erişilebilirlik denetimi (<30s) | 🟡 | MVP (dar.) | Pro+ |
| FR-B5 | Motor kapsam seçimi | Paket haklarına göre motor seçimi, skor etiketine yansıma | 🟢 | MVP | Tümü |

### 3.3 Ölçüm ve Skor

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-C1 | Manuel ölçüm | Kullanıcının el ile ölçüm tetiklemesi | 🟢 | MVP | Free+ |
| FR-C2 | Zamanlanmış ölçüm | İzleme planına göre otomatik periyodik ölçüm | 🟢 | MVP | Pro+ |
| FR-C3 | Örneklemeli çalıştırma | Ölçümler n=3, temp=0 ile koşulur, ham yanıtlar arşivlenir | 🟢 | MVP | Tümü |
| FR-C4 | Deterministik skor | calculation_run_id, girdi ve faktör anlık görüntüsüyle yeniden hesaplanabilir skor | 🟢 | MVP | Tümü |
| **FR-C5** | **Fidelite etiketi** | **Her skor Kademe 1/2/3 etiketi taşır. Etiketsiz skor yayınlanmaz.** | **🟢** | **MVP** | **Tümü** |
| FR-C6 | Güven aralığı | Her skor istatistiksel güven aralığıyla sunulur | 🟢 | MVP | Tümü |
| FR-C7 | Tazelik damgası | Skorlar tazelik zaman damgası taşır, bayatlık eşiğinde uyarı | 🟢 | MVP | Tümü |

### 3.4 Analiz ve Görünürlük

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-D1 | Motor kırılımı | Skorların motor bazında ayrıştırılmış görünümü | 🟢 | MVP | Tümü |
| FR-D2 | Alıntı/kaynak analizi | Alıntı listesi, kaynak sayfaya tıklanabilir bağlantı | 🟢 | MVP | Tümü |
| FR-D3 | Rakip kıyası | Tanımlı rakiplerle temel skor karşılaştırması | 🟡 | MVP (dar.) | Pro+ |
| FR-D4 | Zaman serisi | Skor ve görünürlük zaman serisi grafiği | 🟢 | MVP | Tümü |
| FR-D5 | Benchmark bağlamı | Anonim toplulaştırılmış sektör kıyası (≥5 kiracı) | 🔴 | HT2 | Business+ |
| FR-D6 | Çok müşteri panoraması | Ajans görünümünde tüm müşterilerin grafik panoraması | 🔴 | HT1 | Business |

### 3.5 Öneri Motoru

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-E1 | Öneri üretimi | Kanıt derecesi etiketli öneriler (deneysel/korelasyonel/denenebilir) | 🟡 | MVP (dar.) | Tümü |
| FR-E2 | Politika filtresi | Motor politikalarına aykırı taktikler öneri hattında filtrelenir | 🟢 | MVP | Tümü |
| FR-E3 | Öneri işaretleme | Öneriler uygulandı/reddedildi olarak işaretlenebilir | 🟢 | MVP | Tümü |
| FR-E4 | Öneri-etki takibi | Uygulanan önerinin sonraki ölçümlerdeki etkisinin işaretli karşılaştırması | 🔴 | HT1 | Pro+ |

### 3.6 Bildirim ve Raporlama

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-F1 | Anlamlı uyarı | İstatistiksel eşikten geçen değişimlerde uyarı, aynı gün birleştirme, yanlış alarm geri bildirimi | 🟢 | MVP | Pro+ |
| FR-F2 | Uyarı ayarları | Uyarı eşikleri ve kanal yapılandırması | 🟡 | MVP (dar.) | Pro+ |
| FR-F3 | Haftalık e-posta özeti | Otomatik haftalık özet e-postası, panoya derin bağlantılar | 🟢 | MVP | Tümü |
| **FR-F4** | **White-label PDF rapor** | **Sunucu tarafında eşzamansız PDF üretimi, ajans logosu/renkleriyle** | **🟢** | **MVP** | **Business** |
| FR-F5 | Zamanlanmış rapor | Raporların zamanlanmış periyodik üretimi | 🟡 | MVP (dar.) | Business |
| FR-F6 | REST API erişimi | Okuma amaçlı `/public/v1` REST API, API anahtarı ile | 🔴 | HT1 | Business+ |
| FR-F7 | CSV/PDF dışa aktarım | Skor ve alıntı verilerinin temel CSV/PDF dışa aktarımı | 🟢 | MVP | Tümü |

### 3.7 Ajans Operasyonları

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| **FR-G1** | **Ajans çalışma alanı** | **Müşteri başına ayrık, izole marka/prompt/rapor alanı** | **🟢** | **MVP** | **Business** |
| **FR-G2** | **White-label şablon** | **Ajans logosu ve renkleriyle özelleştirilebilir rapor şablonu** | **🟢** | **MVP** | **Business** |
| FR-G3 | Müşteri arşivleme | Çalışma alanı arşivleme ve devretme | 🔴 | HT1 | Business |

### 3.8 Yönetim

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| FR-H1 | Kota görünürlüğü | Kullanım ve kota durumunun kullanıcıya gösterilmesi | 🟢 | MVP | Tümü |
| FR-H2 | Denetim izi | Yöneticiye görüntülenebilir ve dışa aktarılabilir denetim kaydı | 🔴 | HT2 | Business+ |

### 3.9 Güvenlik, Uyum ve Altyapı (NFR)

| ID | Özellik | Açıklama | MVP | Pencere | Paket |
|:--:|---------|----------|:---:|:-------:|:-----:|
| NFR-1 | Kiracı izolasyonu | Tüm veri erişimi kiracı bağlamında, kiracılar arası erişim engellenir | 🟢 | MVP | Tümü |
| NFR-2 | RBAC | Her uç nokta rol tabanlı yetkilendirme denetiminden geçer | 🟢 | MVP | Tümü |
| NFR-3 | Girdi doğrulama | Tüm girdiler sunucu tarafında doğrulanır, dosya tip/boyut/karma denetimi | 🟢 | MVP | Tümü |
| NFR-4 | Sır yönetimi | Sırlar ortam değişkeni/sır kasası ile yönetilir, koda gömülmez | 🟢 | MVP | Tümü |
| NFR-5 | Veri şifreleme | Veriler aktarımda (TLS) ve beklemede (AES-256) şifrelenir | 🟢 | MVP | Tümü |
| NFR-6 | Değişmez denetim izi | Tüm yazma işlemleri değiştirilemez denetim kaydına yazılır | 🟢 | MVP | Tümü |
| NFR-7 | Deterministik yeniden hesap | Aynı calculation_run girdileriyle yeniden hesap birebir aynı sonucu üretir | 🟢 | MVP | Tümü |
| NFR-8 | Zamanlanmış iş SLA'sı | Zamanlanmış işler planlanan pencerede tamamlanır | 🟢 | MVP | Tümü |
| NFR-9 | Yanıt süreleri | Pano <5s (p50), API <1s (p50), ölçüm <60s | 🟢 | MVP | Tümü |
| NFR-10 | Motor hata yönetimi | Hatalarda sınırlı yeniden deneme, kısmi sonuç etiketleme | 🟢 | MVP | Tümü |
| NFR-11 | Tarihçe saklama | Ölçüm/skor tarihçesi kayıpsız, ilk günden birikir | 🟢 | MVP | Tümü |
| NFR-12 | KVKK/GDPR | Veri dışa aktarımı ve silme, talep üzerine | 🟢 | MVP | Tümü |
| NFR-13 | Benchmark gizliliği | Toplulaştırma ≥5 kiracı eşiği altında sonuç döndürmez | 🟡 | HT2 (pasif) | Tümü |
| NFR-14 | Türkçe-öncelikli dil | Arayüz ve raporlar Türkçe-öncelikli, İngilizce altyapısı hazır | 🟢 | MVP | Tümü |
| NFR-15 | Erişilebilirlik | Temel web erişilebilirlik uyumu (WCAG 2.1 AA) | 🟢 | MVP | Tümü |
| NFR-16 | Kota ve hız sınırı | Kiracı ve platform düzeyinde kota, rate limit, bütçe tavanı | 🟢 | MVP | Tümü |

---

## 4. Motor Destek Matrisi

| Motor | Kademe | MVP | Pencere | Fidelite Etiketi |
|-------|:------:|:---:|:-------:|:----------------:|
| ChatGPT (Responses API) | 2 | 🟢 | MVP | `official_proxy` |
| Gemini (Google grounding) | 2 | 🟢 | MVP | `official_proxy` |
| Perplexity (Sonar) | 1 | 🟢 | MVP | `direct` |
| Claude (API) | 2 | 🔴 | HT1 | `official_proxy` |
| Grok (API) | 2 | 🔴 | HT1 | `official_proxy` |
| Copilot | 3 | 🔴 | HT1 | `directional` |

---

## 5. Pencere Dağılım Özeti

| Pencere | FR | NFR | Toplam |
|:-------:|:--:|:---:|:------:|
| **MVP** (Tam) | 27 | 15 | 42 |
| **MVP** (Daraltılmış) | 5 | — | 5 |
| **HT1** | 4 | — | 4 |
| **HT2** | 3 | 1 | 4 |
| **Kurumsal** | 1 | — | 1 |
| **Ufuk** | — | — | — |
| **Toplam** | **40** | **16** | **56** |

> Not: NFR-13 (benchmark gizliliği) FR-D5 MVP dışı olduğu için pasiftir; teknik olarak MVP'de kodlanır ancak çalışma zamanında etkisizdir. Toplam 16 NFR'den 15'i MVP'de aktif, 1'i (NFR-13) HT2'de pasif durumdadır.

---

## 6. Paket-Özellik Dağılımı

| Özellik Grubu | Free | Pro ($49/ay) | Business ($299/ay) | Enterprise |
|:-------------|:----:|:------------:|:------------------:|:----------:|
| Kayıt ve oturum | ✅ | ✅ | ✅ | ✅ |
| Marka/prompt yönetimi | 1 marka | 3 marka | 10+ marka | Sınırsız |
| Ölçüm motoru | Haftalık | Günlük | Günlük | Günlük |
| Motor sayısı | 1 | 2 | 3 | 3+ |
| Skor ve analiz | ✅ | ✅ | ✅ | ✅ |
| Öneri motoru | ✅ | ✅ | ✅ | ✅ |
| Haftalık e-posta özeti | ✅ | ✅ | ✅ | ✅ |
| White-label PDF rapor | — | — | ✅ | ✅ |
| Ajans çalışma alanı | — | — | ✅ | ✅ |
| API erişimi | — | — | ✅ | ✅ |
| SSO/SAML | — | — | — | ✅ |
| SOC 2 raporu | — | — | — | ✅ |

> Detaylı fiyatlandırma ve paket sınırları için: 0105-pricing.md

---

## 7. Katalog Kullanım Talimatı

Bu katalog aşağıdaki amaçlarla kullanılır:

| Amaç | Nasıl Kullanılır |
|------|-----------------|
| **Faz 3 mimari tasarım** (0301-0311) | MVP özellikleri (42 kalem) öncelikli implementasyon seti; her FR/NFR mimari kararı yönlendirir |
| **Sprint planlama** (0401) | MVP Tam → MVP Daraltılmış → HT1 sırası; bağımlılıklar sprint backlog'una yansıtılır |
| **Test stratejisi** (0404) | Her FR için en az bir test senaryosu; FR-C5, FR-F4, FR-G1 kritik yol |
| **Pilot planlama** (0205 §7) | Pilot kiracılara hangi özelliklerin açılacağı paket haklarına göre belirlenir |
| **Satış ve pazarlama** (0107) | Paket bazlı özellik matrisi; ajans satışında FR-G1/G2, KOBİ satışında FR-C5/FR-F3 öne çıkar |

---

## 8. GeoLens İçin Çıkarımlar

1. **MVP'de 42 özellik tam, 5 özellik daraltılmış.** Hiçbir Çekirdek gereksinim tamamen dışarıda değildir. Daraltılmış 5 özellik HT1'de genişletilir.
2. **Ajans özellikleri (FR-G1, FR-G2, FR-F4) Business paketinde kilitlenir.** Bu, ajansın white-label rapor ve çalışma alanına erişimini lisanslar; B2B2B çarpanı Business paketinin gelir omurgasını oluşturur.
3. **Fidelite etiketi (FR-C5) tüm paketlerde zorunludur.** Temel ürün vaadi olan fidelite, ücretsiz kademede bile kısıtlanmaz — bu güven inşasının olmazsa olmazıdır.
4. **Specification bağlantısı:** FR-C4, FR-C5, FR-C6, FR-C7 skorlama özellikleri GAVF standardına uygun üretilir. White-label raporda (FR-G2) GAVF uyumluluk ibaresi yer alır.
5. **Katalog, Faz 3 dokümanlarının girdisidir.** 0301 (System Architecture) her özellik için bir mimari bileşen atar. Test stratejisi (0404) her FR için test yöntemi tanımlar.

---

## 9. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Free kademede motor sayısı | ⏳ Pilot deneyiyle belirlenecek. AVIP D-07 (self-serve kayıt) devralındı. |
| O-2 | API erişim modeli | ⏳ FR-F6 HT1'de karara bağlanır. AVIP D-61 (okuma API /public/v1) referans alındı. |
| O-3 | Enterprise SLA/motor opsiyonu | ⏳ Kurumsal kapı ile birlikte değerlendirilecek. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-07** | **Self-serve kayıt:** Sürtünmesiz, ödeme bilgisi istenmez. PO 21.07.2026. | AVIP 0201 O-2 |
| **D-80** | **MVP motor kapsamı:** ChatGPT+Gemini+Perplexity. Claude+Grok HT1'de. PO 21.07.2026. | AVIP 0003 O-2 |
| **D-61** | **Okuma API:** Skor+trend+alıntı+rapor meta. /public/v1. TL 21.07.2026. | AVIP 0204 O-3 |
| **D-26** | **Grafik kütüphaneleri:** Recharts + TanStack Table. TL 21.07.2026. | AVIP 0304 O-3 |
| **D-16** | **E-posta sağlayıcısı:** SendGrid. TL 21.07.2026. | AVIP 0304 O-2 |

---

## Kaynaklar

- 0204 PRD — tüm FR/NFR tanımları, öncelik etiketleri
- 0205 MVP — MVP durumu (tam/daraltılmış/açık)
- 0206 Roadmap — pencere atamaları, tetikleyiciler
- 0105 Pricing — paket yapısı, fiyatlandırma
- 0004 Success Metrics — pilot ve büyüme metrikleri
- 0304 Technology Selection — motor kademe modeli

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform özellik kataloğu. 40 FR + 16 NFR + 6 motor olmak üzere 56 özellik, MVP durumu, pencere ve paket bilgileriyle kataloglanmıştır. 0204/0205/0206 dokümanlarından türetilmiştir. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-07 (self-serve), D-80 (MVP motorlar), D-61 (API), D-26 (grafik), D-16 (SendGrid). Devralınan Kararlar eklendi. |
