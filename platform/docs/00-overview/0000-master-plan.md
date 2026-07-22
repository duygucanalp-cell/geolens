# 0000 · Master Plan

| Alan | Değer |
|---|---|
| Doküman ID | 0000 |
| Proje | GeoLens Platform |
| Versiyon | 1.4 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0001–0007, 0101–0108, 0201–0207, 0301–0306, 0401–0415, 0501–0510, specification/docs/00-overview/0000-master-plan, specification/docs/00-overview/0005-version-sync-plan |

---

## 1. Amaç

Bu doküman GeoLens Platform'un anayasasıdır. Tüm ürün kararları, mimari seçimler ve dokümantasyon bu plana referansla bağlanır. Değişiklikler ancak bu dokümanın changelog'una işlenerek geçerlilik kazanır.

GeoLens iki ayrı varlıktan oluşur:

| Varlık | Repo | Hedef |
|--------|------|-------|
| **GeoLens Platform** | `geolens-platform` (private) | Ticari ürün — Backend, Frontend, Worker, SaaS |
| **GeoLens Specification** | `geolens-specification` (public) | Açık standart — GAVF, Citation Standard, Whitepaper'lar |

Bu doküman **yalnızca GeoLens Platform**'u kapsar. Specification için ayrı master plan (`specification/docs/00-overview/0000-master-plan.md`) hazırlanmıştır.

---

## 2. Tasarım Felsefesi

GeoLens'te her karar aşağıdaki altı filtreyle test edilir:

| # | Filtre | Anlamı |
|---|--------|--------|
| F1 | **5 yıl testi** | Bu karar 5 yıl sonra hâlâ doğru olur mu? Trendlere ters mi? |
| F2 | **Ölçek testi** | 10 milyon prompt/gün, 1.000 kurumsal müşteri çalıştırabilir mi? |
| F3 | **Kurumsal test** | Multi-tenancy, RBAC, denetim, KVKK/GDPR, SOC 2 uyumlu mu? |
| F4 | **Patent testi** | Patentlenebilir bir yaklaşım içeriyor mu? |
| F5 | **Moat testi** | Rakiplerin kopyalaması zor mu? Ekonomik hendek güçleniyor mu? |
| F6 | **Kategori testi** | Bu karar bizi "özellik" olmaktan çıkarıp "kategori adı" olmaya yaklaştırıyor mu? |

Bir karar bu filtrelerden en az 4'ünden geçemiyorsa yeniden tasarlanır.

---

## 3. Stratejik Hedef

> **GeoLens'in uzun vadeli hedefi sadece "en iyi AI Visibility aracı" olmak değildir.**
>
> Hedef: AI görünürlüğü denildiğinde akla gelen **ilk standart** ve **ilk platform** olmak.

Bunun için dört stratejik sütun üzerine inşa edilir:

| # | Sütun | Açıklama |
|---|-------|----------|
| S1 | **Açık Standart** | GAVF, AI görünürlüğü ölçümü için evrensel referans olur |
| S2 | **Metodoloji** | Alıntı analizi, skorlama, fırsat motoru — taklidi zor bilgi birikimi |
| S3 | **Platform** | Kurumsal SaaS, multi-tenancy, entegrasyonlar — operasyonel Hendek |
| S4 | **Ekosistem** | SDK'lar, geliştirici araçları, akademik yayınlar — ağ etkisi |

Başarabilirsek GeoLens bir özellik değil, **bir kategori adı** haline gelir.

---

## 4. Proje Kapsamı (Platform)

GeoLens Platform, kurumların AI yanıt motorlarında (ChatGPT, Gemini, Perplexity, Claude, Copilot, Grok ve benzeri) nasıl temsil edildiklerini ölçmelerini, anlamalarını ve iyileştirmelerini sağlar.

### 4.1 Faz Yapısı

| Faz | Kapsam | Çıktı | Tahmini Süre |
|-----|--------|-------|--------------|
| **Faz 0 · Foundation** | Vizyon, master plan, tasarım felsefesi, tüm doküman ağacı | Eksiksiz doküman seti | 2 hafta |
| **Faz 1 · Product Design** | Persona, PRD, MVP, roadmap, feature catalog, UI/UX tasarımı | Ürün spesifikasyonu | 4 hafta |
| **Faz 2 · Architecture** | Sistem mimarisi, domain model, data model, API, security | Teknik blueprint | 4 hafta |
| **Faz 3 · AI Framework** | Prompt generator, answer parser, entity recognition, scoring engine, recommendation engine | AI altyapı tasarımı | 6 hafta |
| **Faz 4 · Development** | Walking skeleton, dilimler, CI/CD, test, deployment | Çalışan ürün | 12 hafta |
| **Faz 5 · Growth** | Pilot, onboarding, ecosystem SDK, whitepaper, GTM | Pazar girişi | Sürekli |

### 4.2 North Star Metriği

> **Aylık Aktif Ölçüm Sayısı (MAQM)** — Müşterilerin platform üzerinde gerçekleştirdiği toplam AI görünürlüğü ölçümü.

Destekleyici metrikler:

| Metrik | Hedef (12 ay) | Kaynak |
|--------|---------------|--------|
| Pilot müşteri sayısı | 10 | 0205-mvp.md |
| Aylık aktif kullanıcı (MAU) | 50 | 0206-roadmap.md |
| Ölçüm başına ortalama gelir (ARPM) | $0.50+ | 0105-pricing.md |
| GAVF uyumlu müşteri | 5 | specification/docs/01-standard/ |

### 4.3 Geçiş Kapıları

Faz geçişleri aşağıdaki olaylarla tetiklenir (tarih bazlı değil, event-driven):

| Geçiş | Tetikleyici |
|-------|-------------|
| Faz 0 → Faz 1 | Tüm overview dokümanları tamamlandı |
| Faz 1 → Faz 2 | PRD + MVP kapsamı approved |
| Faz 2 → Faz 3 | Mimari blueprint PO onayı aldı |
| Faz 3 → Faz 4 | AI Framework tasarımı tamamlandı |
| Faz 4 → Faz 5 | Pilot kiracı bulundu |

---

## 5. Doküman Ağacı

```
platform/docs/
├── 00-overview/        # Anayasa
│   ├── 0000-master-plan.md
│   ├── 0001-vision.md
│   ├── 0002-problem-statement.md
│   ├── 0003-goals-and-non-goals.md
│   ├── 0004-success-metrics.md
│   ├── 0005-core-principles.md
│   ├── 0006-glossary.md
│   └── 0007-faq.md
│
├── 01-business/        # İş
│   ├── 0101-market-analysis.md
│   ├── 0102-competitor-analysis.md
│   ├── 0103-swot.md
│   ├── 0104-business-model.md
│   ├── 0105-pricing.md
│   ├── 0106-go-to-market.md
│   ├── 0107-sales-playbook.md
│   └── 0108-investor-thesis.md
│
├── 02-product/         # Ürün
│   ├── 0201-personas.md
│   ├── 0202-user-journeys.md
│   ├── 0203-use-cases.md
│   ├── 0204-prd.md
│   ├── 0205-mvp.md
│   ├── 0206-roadmap.md
│   └── 0207-feature-catalog.md
│
├── 03-domain/          # Domain
│   ├── 0301-core-concepts.md
│   ├── 0302-domain-model.md
│   ├── 0303-aggregates.md
│   ├── 0304-domain-events.md
│   ├── 0305-bounded-contexts.md
│   └── 0306-domain-services.md
│
├── 04-ai-framework/    # [Ticari know-how] AI Çerçevesi
│   ├── 0401-ai-visibility-standard.md
│   ├── 0402-prompt-taxonomy.md
│   ├── 0403-prompt-generator.md
│   ├── 0404-prompt-weighting.md
│   ├── 0405-citation-framework.md
│   ├── 0406-answer-parser.md
│   ├── 0407-entity-recognition.md
│   ├── 0408-topic-classification.md
│   ├── 0409-visibility-score.md
│   ├── 0410-authority-score.md
│   ├── 0411-share-of-voice.md
│   ├── 0412-opportunity-engine.md
│   ├── 0413-recommendation-engine.md
│   ├── 0414-trend-analysis.md
│   └── 0415-ai-observability.md
│
├── 05-architecture/    # Mimari
│   ├── 0501-system-architecture.md
│   ├── 0502-service-architecture.md
│   ├── 0503-event-driven.md
│   ├── 0504-api-architecture.md
│   ├── 0505-plugin-system.md
│   ├── 0506-worker-design.md
│   ├── 0507-multi-tenancy.md
│   ├── 0508-security.md
│   ├── 0509-scalability.md
│   └── 0510-deployment.md
│
├── 06-data/            # Veri
│   ├── 0601-data-model.md
│   ├── 0602-postgresql-schema.md
│   ├── 0603-clickhouse-schema.md
│   ├── 0604-elasticsearch.md
│   ├── 0605-data-retention.md
│   └── 0606-data-quality.md
│
├── 07-api/             # API
│   ├── rest-api.md
│   ├── graphql.md
│   ├── webhooks.md
│   ├── authentication.md
│   └── rate-limits.md
│
├── 08-ui/              # Arayüz
│   ├── design-system.md
│   ├── dashboard.md
│   ├── navigation.md
│   ├── onboarding.md
│   └── accessibility.md
│
├── 09-devops/          # Operasyon
│   ├── ci-cd.md
│   ├── docker.md
│   ├── kubernetes.md
│   ├── monitoring.md
│   └── backup.md
│
├── 10-engineering/     # Mühendislik
│   ├── coding-standards.md
│   ├── testing.md
│   ├── git-flow.md
│   ├── branching.md
│   ├── code-review.md
│   └── definition-of-done.md
│
└── adr/                # Mimari Karar Kayıtları
    ├── 0001-ddd.md
    ├── 0002-event-driven.md
    ├── 0003-postgresql.md
    ├── 0004-kafka.md
    ├── 0005-plugin-architecture.md
    └── ...
```

---

## 6. Doküman Tanımını Tamamlama Ölçütleri (DoD)

| # | Ölçüt | Doğrulama |
|---|-------|-----------|
| D1 | Künye tablosu mevcut ve dolu | Kontrol |
| D2 | Amaç ve kapsam bölümü tanımlı | Kontrol |
| D3 | İçerik, ilgili bölümleri kapsıyor (her bölüm en az bir paragraf) | Gözden geçirme |
| D4 | Açık sorular tablosu mevcut (en az bir satır veya "Açık soru yok" notu) | Kontrol |
| D5 | Changelog tablosu mevcut ve ilk satır yazılı | Kontrol |
| D6 | İlişkili referanslar doğru ve güncel | Çapraz kontrol |
| D7 | Felsefe filtresi (F1-F6) geçerli | Onay |
| D8 | Durum "Review" veya "Approved" statüsünde | Onay akışı |

---

## 7. Risk Kaydı

| ID | Risk | Olasılık | Etki | Öncelik | Sahip | Durum |
|----|------|----------|------|---------|-------|-------|
| R-01 | AI motorları API erişim şartlarını değiştirir | Yüksek | Orta | Yüksek | Engineering | Açık |
| R-02 | Rakip açık standardı benimseyip liderliği kapar | Orta | Yüksek | Yüksek | Product | Açık |
| R-03 | GAVF standardı sektörde adoptasyon bulamaz | Orta | Yüksek | Yüksek | Product | Açık |
| R-04 | Müşteri verisi KVKK/GDPR kapsamında sınıflandırma sorunu | Düşük | Yüksek | Orta | Hukuk | Açık |
| R-05 | Pazar araştırması verileri hızla eskir | Yüksek | Düşük | Orta | Product | Açık |
| R-06 | Ekip kapasitesi mimari karmaşıklığı karşılayamaz | Orta | Yüksek | Yüksek | Engineering | Açık |
| R-07 | Pilot kiracılar North Star metriğinde eşiği karşılayamaz | Orta | Orta | Orta | Product | Açık |

---

## 8. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| ~~O-1~~ | ~~Kafka mı, Redis Streams mi?~~ | ✅ **KAPANDI** (AVIP D-74, ADR-005): **Redis Streams + tüketici grupları.** Liste elendi. §12'ye işlendi. |
| O-2 | ClickHouse mu, TimescaleDB mi analytics için? | ⏳ Açık — Faz 2 kararı. AVIP'te karşılığı yok. |
| O-3 | Plugin sistemi WASM mı, gRPC mi? | ⏳ Açık — Faz 3 kararı. |
| O-4 | Specification reposu ne zaman public açılacak? (43 doküman tamamlandı) | ⏳ Faz 5 kararı — pilot sonrası. AVIP D-82: 1.0.0 = GA ile hizalanır. |

---

## 9. Felsefe Filtresi Uygulaması

Bu dokümanın kendisi altı filtreyle test edilmiştir:

| Filtre | Sonuç | Gerekçe |
|--------|-------|---------|
| F1 · 5 yıl | ✅ Geçer | AI görünürlüğü uzun vadeli bir gereksinim; trendlere ters değil |
| F2 · Ölçek | ✅ Geçer | Event-driven mimari ve worker tasarımı 10M prompt/gün hedefini destekler |
| F3 · Kurumsal | ✅ Geçer | Multi-tenancy, RBAC, KVKK/GDPR planlaması baştan dahil |
| F4 · Patent | ✅ Geçer | Skorlama metodolojisi ve fırsat motoru patentlenebilir |
| F5 · Moat | ✅ Geçer | Açık standart + kapalı platform ikili stratejisi güçlü hendek yaratır |
| F6 · Kategori | ✅ Geçer | "AI görünürlüğü = GeoLens" eşleşmesi hedefleniyor |

---

## 10. Paydaşlar ve Roller

| Rol | Sorumluluk | Kişi/Ekip |
|-----|------------|-----------|
| **Product Owner** | Vizyon sahipliği, prioriteler, onay akışı | U2 AI Studio · Product |
| **Tech Lead** | Mimari kararlar, teknik kalite, kod incelemesi | U2 AI Studio · Engineering |
| **AI Lead** | Prompt mühendisliği, model seçimi, skorlama tasarımı | U2 AI Studio · AI |
| **Design Lead** | UI/UX, design system, erişilebilirlik | U2 AI Studio · Design |
| **DevOps** | CI/CD, altyapı, izleme, güvenlik | U2 AI Studio · Engineering |
| **Hukuk** | KVKK/GDPR uyumu, sözleşme şablonları | Dış danışman |
| **Yatırımcı İlişkileri** | Finansal raporlama, tur planlama | U2 AI Studio · Founders |

---

## 11. Kilit Bağımlılıklar

| ID | Bağımlılık | Tip | Etkilenen Faz | Durum |
|----|------------|-----|---------------|-------|
| D-1 | GAVF standardının specification repo'sunda tamamlanması | İç | Faz 1 | **tamamlandı** (43 doküman, specification/docs/) |
| D-6 | Specification versiyon senkronizasyonunun sürdürülmesi | İç | Faz 3+ | specification/docs/00-overview/0005-version-sync-plan ile yönetiliyor |
| D-2 | AI motorları API erişim izinleri (ChatGPT, Gemini, Perplexity) | Dış | Faz 3 | beklemede |
| D-3 | Pilot kiracı adayı anlaşması | Dış | Faz 5 | beklemede |
| D-4 | KVKK uyumlu veri işlenme izni çerçeve sözleşmesi | Dış | Faz 4 | beklemede |
| D-5 | Ekip yetiştirme (DDD, event-driven mimari) | İç | Faz 2 | devam ediyor |

---

## 12. Devralınan AVIP Kararları

AVIP arşivinden GeoLens'e devralınan kapalı kararlar:

| ID | AVIP Kararı | GeoLens Etkisi |
|----|-------------|----------------|
| D-43 | **Event-driven faz geçişi:** Pilot kiracı bulununca Faz 4 başlar. Tarih bazlı değil, olay bazlı. (PO 21.07.2026) | GeoLens §4.3 ile uyumlu. Devralındı. |
| D-74 | **Redis Streams + tüketici grupları.** Liste elendi. (TL 21.07.2026, ADR-005) | GeoLens O-1 kapandı. |
| D-82 | **1.0.0 = ticari genel açılış (GA).** Pilot çıkış kapısı sonrası. Pilot dönemi 0.x. (PO 21.07.2026) | GeoLens sürümleme politikasına temel. |
| D-04 | **Segment önceliği:** P3 (ajans) + P2 (KOBİ) V1 odağı. (PO 21.07.2026) | GeoLens 0201 §6 ile zaten uyumlu. |
| D-87 | **Coğrafi odak:** TR+EN paralel GTM. TR-first, baştan iki dilde. (PO 21.07.2026) | GeoLens 0106 ile zaten uyumlu. |

---

## 13. Kaynak ve Bütçe Çerçevesi

| Kalem | Faz 0-1 | Faz 2-3 | Faz 4 | Faz 5 | Toplam |
|-------|---------|---------|-------|-------|--------|
| **İnsan kaynağı** | 3 kişi | 4 kişi | 6 kişi | 8 kişi | — |
| **Bulut altyapısı** | — | $500/ay | $2.000/ay | $5.000/ay | ~$100K/yıl |
| **AI API maliyetleri** | — | $1.000/ay | $5.000/ay | $15.000/ay | ~$250K/yıl |
| **Dış hizmetler** | — | Hukuk danışmanı | — | Pazarlama | ~$50K |

Not: Bu çerçeve üst düzey bir tahmindir. Detaylı bütçe Faz 1'de hazırlanacaktır.

---

## Kaynaklar

- GeoLens Specification — ana sayfa: `specification/docs/`
- GeoLens Specification — master plan: `specification/docs/00-overview/0000-master-plan.md`
- GeoLens Specification — versiyon senkronizasyon planı: `specification/docs/00-overview/0005-version-sync-plan.md`
- GeoLens Specification — GAVF uyumluluk matrisi: `specification/docs/03-compliance/0305-gavf-compliance-matrix.md`
- GeoLens Sözlük: `platform/docs/00-overview/0006-glossary.md`
- Altı filtreli tasarım felsefesi: Bu doküman §2
- Vizyon dokümanı: `platform/docs/00-overview/0001-vision.md`

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.1 | 22.07.2026 | Eksik bölümler eklendi: Felsefe filtresi uygulaması, paydaşlar, bağımlılıklar, kaynak çerçevelesi, North Star metriği. İlişkili alanı düzeltildi (0401–0409). R-01 typo düzeltildi. |
| 1.2 | 22.07.2026 | Tutarlılık düzeltmesi: §5 doküman ağacındaki 04-ai-framework listesi 15 GeoLens dosya adıyla güncellendi; İlişkili alanı 0401–0415 olarak düzeltildi. |
| 1.3 | 22.07.2026 | GAVF Specification referansları güncellendi: D-1 durumu 'tamamlandı' olarak değiştirildi, D-6 (versiyon senkronizasyonu) eklendi, İlişkili alanına spec doküman referansları eklendi, North Star metriği GAVF kaynağı düzeltildi, O-4 açıklaması güncellendi, Kaynaklar bölümüne spec referansları eklendi. |
| 1.4 | 22.07.2026 | AVIP kapalı kararları toplu taşındı: O-1 (Redis Streams) kapatıldı, §12 Devralınan Kararlar eklendi (D-43, D-74, D-82, D-04, D-87). D-1 sayısı 43 olarak güncellendi. |
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform master planı. Altı filtreli tasarım felsefesi, 5 fazlı yapı, 85+ dokümanlı ağaç, risk kaydı. |
