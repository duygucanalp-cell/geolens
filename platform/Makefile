# GeoLens Platform Makefile

.PHONY: help build test lint dev db-up db-down migrate-up migrate-down clean ml-serve ml-test

APP_NAME := geolens

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

## === Development ===

dev: ## Start dev environment (Docker + infrastructure)
	docker compose up -d
	@echo "PostgreSQL: localhost:5432"
	@echo "Redis:      localhost:6379"
	@echo "MinIO:      localhost:9000 (console: 9001)"
	@echo ""
	@echo "Run 'make api' to start the Java API (profil: api)"
	@echo "Run 'make ml-serve' to start ML serving (Python gerekli)"

api: ## Run Java API on :8080 (profil: api)
	cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=api

worker: ## Run Java worker (profil: worker)
	cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=worker

scheduler: ## Run Java scheduler (profil: scheduler)
	cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=scheduler

db-up: ## Start database services only
	docker compose up -d postgres redis minio

db-down: ## Stop database services
	docker compose down

migrate-up: ## Run database migrations
	@echo "Migrations Flyway ile yönetilir — her Java başlangıcında (api/worker/scheduler) otomatik uygulanır"
	@echo "Migration kaynağı: java/src/main/resources/db/migration/"

migrate-down: ## Rollback (placeholder — Flyway undo henüz yok)
	@echo "Rollback not yet implemented"

## === Build ===

build: ## Build Spring Boot jar (api/worker/scheduler profilleri tek jar)
	cd java && ./mvnw -q package -DskipTests
	@echo "Jar: java/target/$(APP_NAME)-0.1.0-SNAPSHOT.jar"

build-api: ## Build API jar only (paketleme tüm profilleri içerir)
	cd java && ./mvnw -q package -DskipTests

ml-serve: ## Start ML serving API (Aşama 0 — Python gerekli; modeller ./ml/models'dan yüklenir)
	cd ml && MODELS="sentiment=models/sentiment/sentiment.onnx,prompt_intent=models/prompt_classifier/prompt_intent.onnx,prompt_topic=models/prompt_classifier/prompt_topic.onnx,prompt_persona=models/prompt_classifier/prompt_persona.onnx,prompt_funnel=models/prompt_classifier/prompt_funnel.onnx" python -m uvicorn geolens.serving.app:app --port 8900

ml-test: ## Run ML tests (Python gerekli)
	cd ml && python -m pytest

## === Test ===

test: ## Run all tests
	cd java && ./mvnw test

test-integration: ## Run integration tests (docker tabanlı senaryolar için ayrı Testcontainers seti gerekir)
	@echo "Java testleri mock tabanlıdır (docker gerektirmez). Testcontainers entegrasyonu henüz eklenmedi."

coverage: ## Run tests with coverage
	cd java && ./mvnw test

## === Lint ===

lint: ## Compile check (derleme kontrolü)
	cd java && ./mvnw -q compile

lint-fix: ## Compile check (fix adımı yok — kod biçimi IDE/düzeltme ile)
	cd java && ./mvnw -q compile

## === Clean ===

clean: ## Clean build artifacts
	cd java && ./mvnw clean

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
