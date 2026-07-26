# 0209 · Ürün Beklentisi (Backlog)

| Alan | Değer |
|---|---|
| Doküman ID | 0209 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 26 Temmuz 2026 |
| İlişkili | 0205, 0206, 0207, ADR-001–012 |

---

## 1. Amaç

Bu doküman, 0207 özellik kataloğu ile mevcut kod tabanı arasındaki gerçek farkı kaydeder. 
0205 MVP kesitinde olup henüz tamamlanmamış maddeleri, daraltılmış kapsamların MVP çıkışı 
için gereken asgari seviyesini ve yol haritasındaki (0206) gelecek pencerelerin içeriğini 
tek bir bakışla görünür kılar.

**Kullanım:** Pilot çıkış kapısı değerlendirmesi, sprint planlaması ve PO önceliklendirmesi 
için girdi sağlar.

---

## 2. MVP Çıkışı İçin Tamamlanması Gerekenler

Pilot kiracıların (P3 ajans + P2 KOBİ) uçtan uca ölçüm ve raporlama döngüsünü 
çalıştırabilmesi için MVP'de olması gerektiği halde eksik olan maddeler.

### 2.1 Güvenlik ve Veri Bütünlüğü (MVP Zorunlu)

| # | Madde | FR/NFR | Mevcut Durum | Yapılacak |
|:-:|-------|--------|:------------:|-----------|
| **M1** | `recommendation.results` RLS | NFR-1 | ❌ Eksik | `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` + tenant isolation policy (011_fix_migrations.sql hazır, deploy edilmeli) |
| **M2** | `update_updated_at()` trigger fonksiyonu | NFR-1 | ❌ Eksik | `CREATE OR REPLACE FUNCTION` (011_fix_migrations.sql hazır, deploy edilmeli) |
| **M3** | RBAC editor/viewer route koruması | NFR-2 | 🟡 Kısmi | Admin kontrolü var, editor/viewer route'ları eklendi (kod hazır, deploy edilmeli) |

### 2.2 Eksik Özellikler (MVP 🟢 Ama Kod Yok)

| # | Madde | FR | Mevcut Durum | Yapılacak |
|:-:|-------|:--:|:------------:|-----------|
| **M4** | Üye davet akışı (e-posta ile davet + kabul) | FR-A2 | 🟡 Kısmi | Listeleme/rol değiştirme var. **Eksik:** davet tablosu migration'ı, e-posta davet gönderme, davet kabul endpoint'i |
| **M5** | Paket hakları denetimi (tier bazlı özellik kısıtlama) | FR-A5 | 🟡 Kısmi | `RequireTier` middleware'i var. **Eksik:** route'lara uygulanmamış, tier-tabanlı özellik haritası yok |
| **M6** | Kurulum sihirbazı (workspace + brand + panel + prompt set) | FR-B3 | 🔴 Yok | İlk girişte adım adım yönlendirme akışı (frontend + backend). Seed data ile atlatılabilir |
| **M7** | Alıntı/kaynak analizi endpoint'i + tıklanabilir link UI | FR-D2 | 🔴 Yok | Citation verisi raw_responses'ta var. **Eksik:** GET /citations endpoint'i, frontend'de kaynak gösterimi |
| **M8** | Zaman serisi trend verisi (frontend TrendChart beslemesi) | FR-D4 | 🟢 Hazır | `GET /trends` endpoint'i eklendi, frontend TrendChart mevcut. Entegrasyon test edilmeli |

### 2.3 Daraltılmış Kapsamların MVP Seviyesi

| # | Madde | FR | MVP Beklentisi | Durum |
|:-:|-------|:--:|---------------|:-----:|
| **M9** | Rakip kıyası (tanımlı rakip setiyle temel skor karşılaştırması) | FR-D3 | Skor tablosunda rakip kolonu, "+" butonu yan yana kıyas | 🔴 Hiç başlanmamış |
| **M10** | Öneri üretimi (kural tabanlı, kanıt derecesi etiketli) | FR-E1 | 8 kural + DB sorguları çalışıyor. Kanıt etiketi (deneysel/korelasyonel/denenebilir) eksik | 🟡 Kısmi |
| **M11** | Uyarı ayarları (varsayılan eşikler + kanal seçimi) | FR-F2 | `notification_settings` tablosu var. `alert_rules` tablosu ve CRUD endpoint'leri yok | 🟡 Kısmi |
| **M12** | Zamanlanmış rapor | FR-F5 | Haftalık digest cron'u var (Pazartesi 09:00). Async rapor (POST + GET status + download) yok | 🟡 Kısmi |

---

## 3. Hızlı Takip 1 — HT1

Pilot çıkış kapısı sonrası açılır. Masa bahisi kapanışları ve daraltılmış kapsam genişlemeleri.

### 3.1 Yeni Özellikler

| # | Madde | FR | Öncelik | Tahmin |
|:-:|-------|:--:|:-------:|:------:|
| **H1** | REST API erişimi (`/public/v1`, API anahtarı) | FR-F6 | Yüksek | 3-5 gün |
| **H2** | Zamanlanmış rapor üretimi (cron + async job + download) | FR-F5 | Yüksek | 2-3 gün |
| **H3** | Öneri-etki takibi (uygulanan önerinin skor değişimini gösterme) | FR-E4 | Orta | 2-3 gün |
| **H4** | Müşteri arşivleme ve devretme | FR-G3 | Düşük | 1-2 gün |
| **H5** | Çok müşteri panoraması (ajans görünümü) | FR-D6 | Orta | 3-5 gün |

### 3.2 Daraltılmış Kapsam Genişletmeleri

| # | Madde | FR | Mevcut | Genişletme |
|:-:|-------|:--:|:------:|-----------|
| **H6** | Site denetimi bulgu kataloğu (detaylı SSR + security + bot check) | FR-B4 | Tek endpoint | Kategorize edilmiş bulgu listesi, PDF çıktı |
| **H7** | Derin rakip kıyası (segment/sektör bazında) | FR-D3 | Temel skor | Radar grafiği, motor bazında rakip karşılaştırması |
| **H8** | Öneri kütüphanesi genişletmesi (kanıt derecesi + sektör kural setleri) | FR-E1 | 8 kural | Sektör bazında kural paketleri, kanıt etiketi |
| **H9** | Uyarı kural editörü (eşik + kanal + zaman) | FR-F2 | notification_settings | `alert_rules` tablosu + CRUD + kural değerlendirme motoru |

### 3.3 İkinci Halka Motorlar

| # | Madde | Kademe | Öncelik |
|:-:|-------|:------:|:-------:|
| **H10** | Claude (Anthropic) adapter | Kademe 2 | Orta |
| **H11** | Grok (xAI) adapter | Kademe 2 | Düşük |
| **H12** | Copilot (Microsoft) adapter | Kademe 3 | Düşük |

---

## 4. Hızlı Takip 2 — HT2

Genel açılış tamamlayıcıları.

| # | Madde | FR | Tahmin |
|:-:|-------|:--:|:------:|
| **T1** | Self-serve paket yükseltme (ödeme altyapısı entegrasyonu) | FR-A6 | 5-8 gün |
| **T2** | Benchmark bağlamı (anonim sektör kıyası, %5 kiracı) | FR-D5 | 2-3 gün |
| **T3** | Denetim izi (yöneticiye aktarılabilir kayıt) | FR-H2 | 2-3 gün |
| **T4** | Elasticsearch entegrasyonu (log/arama) | NFR-12 | 3-5 gün |
| **T5** | ClickHouse analytics (performans/raporlama) | NFR-11 | 3-5 gün |

---

## 5. Kurumsal Kapı

| # | Madde | FR | Not |
|:-:|-------|:--:|-----|
| **K1** | SSO/SAML (kurumsal tek oturum) | FR-A4 | SOC 2 ön koşulu |
| **K2** | SOC 2 Tip 1 hazırlığı | NFR-17 | Denetim günlüğü, erişim kontrolü, şifreleme kanıtı |
| **K3** | Genişletilmiş veri saklama (12 ay+) | — | Soğuk depolama katmanı |
| **K4** | Kurumsal pilot programı | — | Referans müşteri programı |

---

## 6. Teknik Borç ve Altyapı

### 6.1 CI/CD ve Altyapı

| # | Madde | Öncelik | Durum |
|:-:|-------|:-------:|:-----:|
| **X1** | CI pipeline: trivy+gosec+integration test stage eklendi | Yüksek | 🟢 Hazır (commit edildi, test edilmeli) |
| **X2** | Crypto-shredding: `EncryptedClient` entrypoint'lere bağlandı (`STORAGE_MASTER_KEY`) | Yüksek | 🟢 Hazır (commit edildi, deploy edilmeli) |
| **X3** | Grafana dashboard JSON | Orta | 🟢 Hazır — `deploy/grafana/` (provisioning + 18 panelli overview dashboard) |
| **X4** | API benchmark script (`hey`/`wrk`) | Düşük | 🟢 Hazır — `deploy/benchmark.sh` (6 endpoint, login→token, -z/-q parametreli) |

### 6.2 Kod Kalitesi

| # | Madde | Öncelik | Durum |
|:-:|-------|:-------:|:-----:|
| **X5** | Migration 004: `config.prompt_sets` duplicate CREATE (001'de var, 004'te tekrar) | Yüksek | 🟢 Fix'lendi — 004 artık `ADD COLUMN IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS` |
| **X6** | ULID tutarsızlığı: DB `gen_random_uuid()`, Go `oklog/ulid`, worker `generateID()` | Düşük | 🟢 Standardize — `internal/id` paketi (`id.New()`), 4 duplicate fonksiyon temizlendi |
| **X7** | Handler test eksikleri: config, pdf, auth (Login/Register için unit test var) | Orta | 🟢 Yeni: config (7), pdf (3), alert (3), apikey (3), public (2) — 39/39 suite passing |

### 6.3 API Dokümantasyon Uyumu

| # | Madde | Öncelik | Durum |
|:-:|-------|:-------:|:-----:|
| **X8** | `POST /privacy/delete` path mismatch (kod: `/account/deletion`) | Düşük | 🟢 Her iki path de çalışır — alias eklendi `r.Post("/privacy/delete", ...)` + ADR güncellendi |
| **X9** | Async job pattern: `TriggerMeasurement` 202 + Location header | Orta | 🟢 Location header + `run_id` + `GET /measurements/{runId}/status` endpoint |
| **X10** | `GET /v1/tenant` endpoint'i yok (tenant bilgisi dönmeli) | Düşük | 🟢 `GET /v1/tenant` — `authHandler.GetTenant` (name, slug, tier, created_at) |

---

## 7. Öncelik Sırası (PO Kararı Bekler)

### MVP Çıkış Blocker'ları (Pilot Açılmaz)
1. **M1** — recommendation.results RLS (veri sızıntısı)
2. **M2** — update_updated_at() trigger (UPDATE crash)
3. **M4** — Üye davet akışı (ajans kullanıcı ekleyemez)

### MVP Yüksek (Pilot Çalışır Ama Eksik)
4. **M9** — Rakip kıyası (P3 ajans için kritik)
5. **M11** — Uyarı ayarları (P2 KOBİ için kritik)
6. **M7** — Alıntı/kaynak analizi (FR-D2)

### HT1 Giriş Öncelikli
7. **H1** — REST API (FR-F6, masa bahisi)
8. **H10** — Claude adapter (motor çeşitliliği)
9. **H6** — Site denetimi genişletmesi (FR-B4)

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 26 Temmuz 2026 | İlk sürüm — 0207 + kod analizi çıktısı |
