# GeoLens Platform

**AI görünürlüğü ölçüm ve zekâ platformu.**

GeoLens Platform, kurumların AI yanıt motorlarında (ChatGPT, Gemini, Perplexity, Claude, Copilot, Grok ve benzeri) nasıl temsil edildiklerini ölçmelerini, anlamalarını ve iyileştirmelerini sağlayan kurumsal SaaS platformudur.

---

## Hızlı Bağlantılar

| Kaynak | Açıklama |
|--------|----------|
| [Doküman Ağacı](docs/00-overview/0000-master-plan.md) | Tüm dokümantasyonun anayasası ve dizini |
| [Vizyon](docs/00-overview/0001-vision.md) | 10 yıllık ürün vizyonu |
| [PRD](docs/02-product/0204-prd.md) | Ürün gereksinimleri (40 FR + 16 NFR) |
| [MVP Kapsamı](docs/02-product/0205-mvp.md) | MVP kesiti, pilot tanımı |
| [Yol Haritası](docs/02-product/0206-roadmap.md) | HT1/HT2/Kurumsal/Ufuk pencereleri |
| [GAVF Standardı](docs/04-ai-framework/0401-ai-visibility-standard.md) | GeoLens AI Visibility Framework |

---

## Mimari Bakış

```
cmd/api          → REST API + HTTP middleware
cmd/scheduler    → Zamanlanmış iş yönetimi
cmd/worker       → Arka plan işlemci (measure/report/notify)
web/             → React SPA

PostgreSQL 16    → Birincil veritabanı + RLS multi-tenancy
Redis 7+         → Kuyruk (Redis Streams) + önbellek
S3-compatible    → Ham yanıt arşivi + rapor dosyaları
```

Detaylı mimari: [0501 - System Architecture](docs/05-architecture/0501-system-architecture.md)

---

## Geliştirme Ortamı

Gereksinimler: Go 1.22+, Node.js 20+, Docker

```bash
# Geliştirme ortamını başlat
docker compose -f deploy/docker-compose.dev.yml up -d

# API'yi çalıştır
go run ./cmd/api

# Worker'ı çalıştır
go run ./cmd/worker
```

Detaylı kurulum: [0510 - Deployment](docs/05-architecture/0510-deployment.md)

---

## Repo Yapısı

```
platform/
├── cmd/              # Uygulama giriş noktaları
├── internal/         # Uygulama kodu
│   ├── platform/     # Altyapı katmanı
│   ├── identity/     # Kimlik ve erişim
│   ├── measure/      # Ölçüm motoru
│   ├── config/       # Yapılandırma
│   ├── insight/      # Analiz
│   └── governance/   # Denetim ve yönetim
├── web/              # React SPA
├── docs/             # Dokümantasyon
└── deploy/           # Dağıtım yapılandırması
```

---

## GeoLens Specification

GeoLens iki varlıktan oluşur. Bu repo **ticari ürünü**, aşağıdaki repo ise **açık standardı** barındırır:

➡️ [GeoLens Specification](https://github.com/u2ai/geolens-specification) — GAVF standardı, whitepaper'lar, uyumluluk testleri

---

## Lisans

© 2026 U2 AI Studio. Tüm hakları saklıdır. Bu özel depo ticari bir ürün içermektedir.
