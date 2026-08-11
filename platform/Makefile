# GeoLens Platform Makefile

.PHONY: help build test lint dev db-up db-down migrate-up migrate-down clean ml-serve ml-test

APP_NAME := geolens

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

## === Development ===

dev: ## Start dev environment (Docker + hot-reload)
	docker compose up -d
	@echo "PostgreSQL: localhost:5432"
	@echo "Redis:      localhost:6379"
	@echo "MinIO:      localhost:9000 (console: 9001)"
	@echo ""
	@echo "Run 'go run ./cmd/api' to start API server"
	@echo "Run 'make ml-serve' to start ML serving (Python gerekli)"

db-up: ## Start database services only
	docker compose up -d postgres redis minio

db-down: ## Stop database services
	docker compose down

migrate-up: ## Run database migrations
	@echo "Migrations are auto-executed from ./migrations/ via docker-compose"
	@echo "For manual: cat migrations/*.sql | docker compose exec -T postgres psql -U geolens"

migrate-down: ## Rollback (placeholder — add golang-migrate or similar later)
	@echo "Rollback not yet implemented"

## === Build ===

build: ## Build all binaries
	go build -o bin/$(APP_NAME)-api ./cmd/api
	go build -o bin/$(APP_NAME)-scheduler ./cmd/scheduler
	go build -o bin/$(APP_NAME)-worker ./cmd/worker

build-api: ## Build API only
	go build -o bin/$(APP_NAME)-api ./cmd/api

build-scheduler: ## Build scheduler only
	go build -o bin/$(APP_NAME)-scheduler ./cmd/scheduler

build-worker: ## Build worker only
	go build -o bin/$(APP_NAME)-worker ./cmd/worker

ml-serve: ## Start ML serving API (Aşama 0 — Python gerekli; modeller ./ml/models'dan yüklenir)
	cd ml && MODELS="sentiment=models/sentiment/sentiment.onnx,prompt_intent=models/prompt_classifier/prompt_intent.onnx,prompt_topic=models/prompt_classifier/prompt_topic.onnx,prompt_persona=models/prompt_classifier/prompt_persona.onnx,prompt_funnel=models/prompt_classifier/prompt_funnel.onnx" python -m uvicorn geolens.serving.app:app --port 8900

ml-test: ## Run ML tests (Python gerekli)
	cd ml && python -m pytest

## === Test ===

test: ## Run all tests
	go test ./... -v -count=1

test-race: ## Run tests with race detection
	go test ./... -race -count=1

test-integration: ## Run integration tests (requires docker)
	go test ./... -tags=integration -v -count=1

coverage: ## Run tests with coverage
	go test ./... -coverprofile=coverage.out
	go tool cover -html=coverage.out -o coverage.html

## === Lint ===

lint: ## Run golangci-lint
	golangci-lint run ./... --timeout=5m

lint-fix: ## Run golangci-lint with auto-fix
	golangci-lint run ./... --fix --timeout=5m

## === Clean ===

clean: ## Clean build artifacts
	rm -rf bin/ coverage.out coverage.html

## === Secrets Management (SOPS+Age) ===

SECRETS_PLAIN := .env.secrets
SECRETS_ENC := docker/.env.secrets.enc
AGE_KEY_FILE := docker/age-key.txt

encrypt-secrets: ## Encrypt .env.secrets → docker/.env.secrets.enc
ifneq ($(shell command -v sops 2>/dev/null),)
	sops encrypt $(SECRETS_PLAIN) > $(SECRETS_ENC)
else ifneq ($(shell docker --version 2>/dev/null),)
	@echo "sops yerelde yok, Docker ile şifreleniyor..."
	docker run --rm -i -v "$(PWD):/work" alpine:3.20 sh -c \
	  'apk add --no-cache age sops > /dev/null 2>&1 && \
	   export SOPS_AGE_KEY=$$(cat /work/$(AGE_KEY_FILE) | grep -v "^#" | tr -d "\n\r") && \
	   sops --config /work/.sops.yaml --encrypt /work/$(SECRETS_PLAIN) > /work/$(SECRETS_ENC)'
	@echo "$(SECRETS_ENC) şifrelendi"
else
	@echo "HATA: sops veya docker gerekli"
	exit 1
endif

decrypt-secrets: ## Decrypt docker/.env.secrets.enc → .env.secrets
ifneq ($(shell command -v sops 2>/dev/null),)
	sops decrypt $(SECRETS_ENC) > $(SECRETS_PLAIN)
else ifneq ($(shell docker --version 2>/dev/null),)
	@docker run --rm -i -v "$(PWD):/work" alpine:3.20 sh -c \
	  'apk add --no-cache age sops > /dev/null 2>&1 && \
	   export SOPS_AGE_KEY=$$(cat /work/$(AGE_KEY_FILE) | grep -v "^#" | tr -d "\n\r") && \
	   sops --config /work/.sops.yaml --decrypt /work/$(SECRETS_ENC) > /work/$(SECRETS_PLAIN)'
	@echo "$(SECRETS_PLAIN) çözüldü (gitignore'da, commit ETMEYİN)"
else
	@echo "HATA: sops veya docker gerekli"
	exit 1
endif

edit-secrets: ## Edit encrypted secrets (opens in editor)
ifneq ($(shell command -v sops 2>/dev/null),)
	sops $(SECRETS_ENC)
else
	@echo "HATA: sops gerekli (şifreli dosyayı direkt düzenleyemezsiniz)"
	@echo "      1. decrypt-secrets"
	@echo "      2. .env.secrets'i düzenle"
	@echo "      3. encrypt-secrets"
	exit 1
endif

show-key: ## Show age public key
	@echo "Age Public Key:"
	@grep "public key" $(AGE_KEY_FILE) 2>/dev/null || cat $(AGE_KEY_FILE) | grep -v "^#" | head -1

## === Tools ===

tools: ## Install required tools
	go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest

# sqlc: deferred to Dilim 2 — codebase uses raw pgx
