# AGENTS.md — GeoLens

## Repository State

This is a **design-and-architecture workspace** for the GeoLens project. No application code exists yet. The project is in **Faz 0 (Phase 0)** — the complete product is being designed from scratch before any code is written.

The workspace contains two logical repositories:

| Directory | GitHub Repo | Visibility |
|-----------|-------------|------------|
| `platform/` | `geolens-platform` | Private (ticari ürün) |
| `specification/` | `geolens-specification` | Public (açık standart) |

An agent should look for source of truth within the appropriate subdirectory.

## Document Language

All documentation is written in **Turkish**. Preserve Turkish in any edits.

## GeoLens Platform — Document Tree

```
platform/docs/
├── 00-overview/        # Vision, problem, goals, metrics, principles, glossary, FAQ
├── 01-business/        # Market analysis, competitor analysis, SWOT, business model, pricing, GTM, sales playbook, investor thesis
├── 02-product/         # Personas, user journeys, use cases, PRD, MVP, roadmap, feature catalog
├── 03-domain/          # Core concepts, domain model, aggregates, domain events, bounded contexts, domain services
├── 04-ai-framework/    # [Ticari know-how] Prompt generator, prompt weighting, answer parser, entity recognition, topic classification, opportunity engine, recommendation engine, trend analysis, AI observability
├── 05-architecture/    # System architecture, service architecture, event-driven, API architecture, plugin system, worker design, multi-tenancy, security, scalability, deployment
├── 06-data/            # Data model, PostgreSQL schema, ClickHouse schema, Elasticsearch, data retention, data quality
├── 07-api/             # REST API, GraphQL, webhooks, authentication, rate limits
├── 08-ui/              # Design system, dashboard, navigation, onboarding, accessibility
├── 09-devops/          # CI/CD, Docker, Kubernetes, monitoring, backup
├── 10-engineering/     # Coding standards, testing, git flow, branching, code review, definition of done
└── adr/                # Architecture Decision Records
```

## GeoLens Specification — Document Tree

```
specification/docs/
├── 00-overview/        # Standard vision, problem statement, glossary
├── 01-standard/        # GAVF (GeoLens AI Visibility Framework), Citation Standard, Visibility Score
├── 02-methodology/     # Prompt Taxonomy, Authority Score, Share of Voice
└── 03-whitepaper/      # Academic papers, technical reports, conference talks
```

## AI Framework — Dağılım (Karma Model)

AI Framework dokümanları specification (açık standart) ve platform (ticari know-how) arasında bölünmüştür. Numara aralıkları her repositoride kendi içinde sıralıdır.

### platform/docs/04-ai-framework/ (0401-0409)

| No | Doküman | Alan |
|----|---------|------|
| 0401 | prompt-generator.md | Prompt mühendisliği ve optimizasyonu |
| 0402 | prompt-weighting.md | Prompt ağırlıklandırma stratejileri |
| 0403 | answer-parser.md | Motor yanıtı ayrıştırma |
| 0404 | entity-recognition.md | Varlık tanıma ve çıkarımı |
| 0405 | topic-classification.md | Konu sınıflandırma |
| 0406 | opportunity-engine.md | Fırsat tespit motoru |
| 0407 | recommendation-engine.md | Öneri motoru |
| 0408 | trend-analysis.md | Trend analizi |
| 0409 | ai-observability.md | AI gözlemlenebilirlik |

### specification/docs/

| Doküman | Dizin | Açıklama |
|---------|-------|----------|
| gavf.md (AI Visibility Standard) | 01-standard | GAVF çekirdek standart |
| citation-framework.md | 01-standard | Alıntı analizi standardı |
| visibility-score.md | 01-standard | Görünürlük skoru formülü |
| prompt-taxonomy.md | 02-methodology | Prompt sınıflandırma taksonomisi |
| authority-score.md | 02-methodology | Kaynak otorite skoru |
| share-of-voice.md | 02-methodology | Payda söz hakkı hesaplaması |

## Document Structure Convention

Every document starts with a metadata table containing: Doküman ID, Proje (always "GeoLens"), Versiyon, Durum, Sahip, Tarih, İlişkili. All documents end with a Changelog table. Status flows: `Draft → Review → Approved`.

## Design Philosophy

Each decision must pass these filters:
- Will this still be correct **5 years from now**?
- Can it handle **10M prompts/day**?
- Can it support **1,000 enterprise customers**?
- Is it **patentable**?
- Is it **hard for competitors to copy**?
- Does it strengthen the product's **economic moat**?

## What NOT to Do

- Do not create code files, tests, or build configs — none are expected yet
- Do not translate documents to English unless explicitly instructed
- Do not change document status to Approved — that requires PO approval
- Do not speculate on implementation details not yet decided
