# GeoLens

> AI görünürlüğü denildiğinde akla gelen ilk standart ve ilk platform.

**GeoLens**, kurumların yapay zekâ destekli arama ve yanıt motorlarında (ChatGPT, Gemini, Perplexity, Claude, Copilot, Grok ve gelecekteki sistemler) nasıl temsil edildiklerini ölçmelerini, anlamalarını ve iyileştirmelerini sağlayan bir AI Görünürlük Platformudur.

Geleneksel SEO platformları Google sıralamalarına odaklanırken GeoLens, büyük dil modelleri (LLM) ve AI yanıt motorlarındaki görünürlüğe odaklanır. Amaç, AI çağında markaların görünmezleşmesini engellemek ve AI görünürlüğünü ölçülebilir, yönetilebilir ve optimize edilebilir bir disiplin haline getirmektir.

---

## Vizyon

GeoLens'in uzun vadeli hedefi sadece "en iyi AI Visibility aracı" olmak değil:

> **AI görünürlüğü denildiğinde akla gelen ilk standart ve ilk platform olmak.**

Bunun için teknik mimari kadar; metodoloji, açık standartlar, whitepaper'lar, SDK'lar ve geliştirici ekosistemi de inşa edilecektir. GeoLens bir özellik değil, bir kategori adı haline gelecektir.

---

## İki Repo Stratejisi

Proje iki ayrı GitHub reposu olarak yapılandırılmıştır:

| Repo | Amaç | Kapsam |
|------|------|--------|
| **geolens-platform** | Ticari ürün | Backend, Frontend, Worker, Dashboard, API, SaaS altyapısı |
| **geolens-specification** | Açık standart (public) | GAVF, Visibility Score, Citation Standard, Prompt Taxonomy, Whitepaper'lar |

Bu ayrışmanın iki büyük avantajı vardır:

1. **geolens-platform** ticari ürünümüz olur, tüm know-how ve rekabet avantajı buradadır.
2. **geolens-specification** sektör standardı olmayı hedefler — başka şirketler "GAVF 2.0 uyumluyuz" dediğinde artık ürün değil, ekosistem kurmuş oluruz.

---

## Tasarım Felsefesi

Her karar aşağıdaki filtrelerden geçirilir:

- Bu karar **5 yıl sonra hâlâ doğru** olur mu?
- **10 milyon prompt/gün** çalıştırabilir mi?
- **1.000 kurumsal müşteriyi** destekleyebilir mi?
- **Patentlenebilir** bir yaklaşım içeriyor mu?
- Rakiplerin kopyalaması **zor** mu?
- Ürünün ekonomik hendeğini (**moat**) güçlendiriyor mu?

---

## GeoLens Platform — Doküman Yapısı

```
platform/
└── docs/
    ├── 00-overview/        # Vizyon, problem, hedefler, metrikler, prensipler
    ├── 01-business/        # Pazar, rakip, SWOT, iş modeli, pricing, GTM, satış
    ├── 02-product/         # Persona, journey, use case, PRD, MVP, roadmap
    ├── 03-domain/          # Core concepts, domain model, aggregates, events
    ├── 04-ai-framework/    # Prompt generator, parser, entity recognition (ticari know-how)
    ├── 05-architecture/    # System, service, API, multi-tenancy, security
    ├── 06-data/            # Data model, PostgreSQL, ClickHouse, Elasticsearch
    ├── 07-api/             # REST, GraphQL, webhooks, auth, rate limits
    ├── 08-ui/              # Design system, dashboard, navigation, onboarding
    ├── 09-devops/          # CI/CD, Docker, K8s, monitoring, backup
    ├── 10-engineering/     # Standartlar, test, git flow, code review, DoD
    └── adr/                # Architecture Decision Records
```

## GeoLens Specification — Doküman Yapısı

```
specification/
└── docs/
    ├── 00-overview/        # Standard vizyonu, problem, glossary
    ├── 01-standard/        # GAVF, Citation Standard, Visibility Score, Fidelity Tiers
    ├── 02-methodology/     # Prompt Taxonomy, Authority Score, Share of Voice
    ├── 03-compliance/      # Uyumluluk seviyeleri ve sertifikasyon
    ├── 04-whitepapers/     # Akademik yayınlar, teknik raporlar
    ├── 05-integration/     # Üçüncü taraf entegrasyon rehberleri
    └── adr/                # Standart karar kayıtları
```

---

## Hızlı Başlangıç

```bash
# Bağımlılıkları ayağa kaldır (PostgreSQL + Redis + MinIO)
cd platform && make dev

# API'yi çalıştır (ayrı terminal)
cd platform && go run ./cmd/api

# Worker'ı çalıştır (ayrı terminal)
cd platform && go run ./cmd/worker

# Frontend'i çalıştır (ayrı terminal)
cd platform/web && npm run dev
```

Detaylı komutlar için: [`platform/Makefile`](platform/Makefile) ve [`AGENTS.md`](AGENTS.md).

## Temel Teknoloji Yığını

| Katman | Seçim |
|--------|-------|
| Backend | Go 1.26, chi/v5, pgx/v5 |
| Veritabanı | PostgreSQL 16+ (RLS ile çok kiracılı), ClickHouse (analytics) |
| Kuyruk/Önbellek | Redis 7+ (Streams + tüketici grupları) |
| Depolama | S3-uyumlu (MinIO) |
| Arama | Elasticsearch |
| Frontend | React 18 + TypeScript + Vite + Recharts |
| Gözlemlenebilirlik | OpenTelemetry + Prometheus/Grafana |
| Sır Yönetimi | SOPS + Age |

---

## Proje Durumu

Şu an **Faz 2-3 (Geliştirme + Sertleştirme)** aşamasındayız:
- ✅ GeoLens çift repo yapısı kuruldu
- ✅ `platform/` — Go backend (3 entrypoint: api, worker, scheduler) + React SPA çalışıyor
- ✅ Tüm dilimler (D1-D4) tamamlandı, pilot çıkış kapısı hazır
- 🔄 `specification/` — GAVF standardı dokümanları yazılıyor
- 🔄 Pilot onboarding ve saha doğrulaması bekliyor

---

*Bu proje, AI görünürlüğü kategorisini inşa etme vizyonuyla U2 AI Studio tarafından geliştirilmektedir.*
