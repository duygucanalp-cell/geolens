# AGENTS.md — AVIP (AI Visibility Intelligence Platform)

## Repository State

This is a **documentation-only repository**. No application code exists yet. The project is in **Faz 0 (Phase 0)** — design and architecture are being finalized; Faz 4 (development phase) begins after brand/domain decisions close. An agent should not look for source code, tests, or build scripts; the source of truth is the `docs/` directory.

## Document Language and Naming

All documentation is written in **Turkish**. Preserve Turkish in any edits. Document filenames follow the pattern `NNNN-title.md` where the numeric prefix groups documents by concern:

| Prefix | Group |
|--------|-------|
| `000x` | Meta: vision, problem, governance, metrics |
| `010x` | Market research: landscape, competitors, SWOT |
| `020x` | Product: personas, PRD, MVP scope, roadmap |
| `030x` | Architecture: domain model, DB design, tech selection, services, API, jobs, connectors, scoring, security, observability |
| `040x` | Engineering: dev process, environments, CI/CD, test strategy, security review, release |

## Document Structure Convention

Every document starts with a metadata table (künye) containing: Doküman ID, Proje (always "AI Visibility Intelligence Platform (kod adı: AVIP)"), Versiyon, Durum, Sahip, Tarih, Karşıladığı madde, İlişkili. All documents end with a Changelog table. Status flows: `Draft → Review → Approved` (Approved requires PO approval per `0007`).

## Key Architectural Decisions (ADRs)

Before editing architecture docs, understand these accepted decisions:

- **ADR-001**: Go backend, PostgreSQL 16+, Redis 7+, S3-compatible storage
- **ADR-003**: Modular monolith + worker pool (not microservices, not serverless)
- **ADR-004**: Single schema + RLS for multi-tenancy (not schema-per-tenant)
- **ADR-005**: Redis Streams + consumer groups for job queue
- **ADR-002**: React + TypeScript SPA for dashboard (Flutter reserved for future mobile)

Tech details are in `docs/0304-technology-selection.md`. Dependency rules (D1–D7) and module boundaries are in `docs/0305-services-modules.md`.

## Document-Code Sync Rule

If a document change alters a design contract that will later be implemented in code, the change must either include a changelog update in the relevant document or register in the v1.1 correction queue (`0007` §6). This is enforced by the governance process, not automated yet.

## Decision Tracking

- **Architectural decisions** → ADR files under `docs/adr/` (referenced from `0304`)
- **Product decisions** → changelog of the relevant document
- **Pending questions** → tracked in "Açık Sorular" (O-x) tables within each document
- **Decision log** → `docs/0007-governance.md` §8 contains the consolidated pending decisions

## What NOT to Do

- Do not create code files, tests, or build configs — none are expected yet
- Do not translate documents to English unless explicitly instructed
- Do not change document status to Approved — that requires PO approval
- Do not add new top-level documents without following the `0007` Tip 1/Tip 2 decision process
- Do not speculate on implementation details not yet decided (marked [K] or O-x in docs)
