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
java/ (Spring Boot monolit)
├── api profil (varsayılan)  → REST API + JWT auth (chi → Spring MVC portu)
├── worker profil            → Redis Stream tüketicileri (q:measure, q:governance)
└── scheduler profil         → Outbox dispatcher + panel cron taraması + haftalık digest
web/                         → React SPA (Vite, /v1 → :8080 proxy)
ml/                          → Python ONNX serving (:8900)

PostgreSQL 16    → Birincil veritabanı + RLS multi-tenancy (şema: Flyway)
Redis 7+         → Kuyruk (Redis Streams) + önbellek
S3-compatible    → Ham yanıt arşivi + rapor dosyaları (MinIO)
```

Detaylı mimari: [0501 - System Architecture](docs/05-architecture/0501-system-architecture.md) ve [java/README.md](java/README.md)

---

## Geliştirme Ortamı

Gereksinimler: Java 25+, Node.js 20+, Docker

```bash
# Altyapıyı başlat (PostgreSQL + Redis + MinIO + ML serving + web)
docker compose up -d

# API'yi çalıştır (profil: api)
cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=api

# Worker'ı çalıştır (profil: worker)
cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=worker

# Scheduler'ı çalıştır (profil: scheduler)
cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=scheduler
```

Detaylı kurulum: [0510 - Deployment](docs/05-architecture/0510-deployment.md)

---

## Repo Yapısı

```
platform/
├── java/              # Spring Boot monolit (api/worker/scheduler profilleri)
│   └── src/main/resources/db/migration/  # Flyway şema migration'ları
├── web/               # React SPA
├── ml/                # Python ONNX serving + eğitim
├── docs/              # Dokümantasyon
└── deploy/            # Dağıtım yapılandırması
```

---

## GeoLens Specification

GeoLens iki varlıktan oluşur. Bu repo **ticari ürünü**, aşağıdaki repo ise **açık standardı** barındırır:

➡️ [GeoLens Specification](https://github.com/u2ai/geolens-specification) — GAVF standardı, whitepaper'lar, uyumluluk testleri

---

## Lisans

© 2026 U2 AI Studio. Tüm hakları saklıdır. Bu özel depo ticari bir ürün içermektedir.
