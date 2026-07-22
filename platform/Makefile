# GeoLens Platform Makefile

.PHONY: help build test lint dev db-up db-down migrate-up migrate-down clean

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

## === Tools ===

tools: ## Install required tools
	go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
	go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest

sqlc: ## Generate Go code from SQL
	sqlc generate
