# AGENTS.md — GeoLens

## Repository State

Active development workspace in **Faz 2-3 (Geliştirme + Sertleştirme)**. Contains two sub-repos:

| Directory | GitHub Repo | Visibility |
|-----------|-------------|------------|
| `platform/` | `geolens-platform` | Private (ticari ürün) |
| `specification/` | `geolens-specification` | Public (açık standart) |

`platform/` has **real compilable code** (~9K satır Go backend + React SPA, 14 test dosyası). `specification/` is pure markdown.

## Language

All documentation is in **Turkish**. Do not translate.

## Document Conventions

Every doc starts with a metadata table (Doküman ID, Proje="GeoLens", Versiyon, Durum, Sahip, Tarih, İlişkili) and ends with a Changelog table. Status flow: `Draft → Review → Approved`. Do not set Approved without PO approval.

## platform/ — Commands

Run everything from `platform/`:

```bash
# Dev environment (PostgreSQL + Redis + MinIO + hot-reload infra)
make dev                # docker compose up -d

# Manual runs after `make dev`
go run ./cmd/api         # REST API on :8080
go run ./cmd/scheduler   # measurement scheduler
go run ./cmd/worker      # background worker (measure/report/notify)
npm run dev             # from web/ — React SPA on :5173, proxies /v1 to :8080

# Build all binaries
make build              # go build ./cmd/api ./cmd/scheduler ./cmd/worker

# Test (add -tags=integration for docker-dependent tests)
go test ./... -v -count=1
go test ./... -tags=integration -v -count=1   # requires docker

# Lint
make lint               # golangci-lint run ./... --timeout=5m
make lint-fix

# Secrets (SOPS+Age)
make encrypt-secrets    # .env.secrets → docker/.env.secrets.enc
make decrypt-secrets    # reverse (output in .gitignore)
make edit-secrets       # sops edit wrapper
```

CI workflow: lint → test (`-short`) → build (api, scheduler, worker).

## platform/ — Architecture Notes

- **Three entrypoints** under `cmd/`: `api` (HTTP+chi), `scheduler` (timer+cron), `worker` (Redis Streams consumer)
- **Framework**: Go 1.26, chi/v5 router, pgx/v5 (PostgreSQL), redis/go-redis/v9, MinIO (S3)
- **Tech stack** (from docs): PostgreSQL 16+ (RLS multi-tenancy), ClickHouse (analytics), Redis 7+ (Streams + cache), Elasticsearch, OpenTelemetry + Prometheus/Grafana
- **Engine adapters** in `engine/` — pluggable AI provider wrappers (chatgpt, gemini, perplexity) implementing `engine.Adapter` interface (`Name()`, `Tier()`, `Execute(prompt)`)
- **Internal packages** in `internal/` — audit, auth, config, delivery, governance, measure, pdf, privacy, recommendation
- **Platform packages** in `platform/` — db, httpmw, httputil, metrics, queue, storage, telemetry
- **Migrations** in `migrations/` — auto-executed by docker-compose (mounted to `/docker-entrypoint-initdb.d`)
- **Secrets**: SOPS+Age encrypted file at `docker/.env.secrets.enc`, decrypted at container start by `docker/entrypoint.sh`
- **Frontend**: React 18 + TypeScript + Vite + Recharts, proxies `/v1` → `localhost:8080`
- **Go version note**: `go.mod` says `go 1.26.1`, CI workflow pins `GO_VERSION: "1.23"` — the CI config may need updating

## specification/ — Pure Docs

No code, no tests, no build config. Standard documents under `docs/`: GAVF core, measurement/response/scoring/action standards, methodology, compliance, whitepapers, integration guides.

## What NOT to Do

- Do not change document status to Approved (requires PO approval)
- Do not translate docs to English unless explicitly instructed
- Do not speculate on implementation details not yet decided (docs capture decisions, not guesses)
- Do not create code outside `platform/` — `specification/` is markdown only
- Do not edit existing Go or TypeScript source files unless explicitly asked — codebase is in active development by the engineering team
- Do not change the AGENTS.md or README without explicit request
