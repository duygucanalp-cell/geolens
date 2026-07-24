# syntax=docker/dockerfile:1
# GeoLens Platform — Multi-stage Dockerfile
# Targets: api, scheduler, worker

# ============================================================
# Stage 1: Build all Go binaries
# ============================================================
FROM golang:1.26-alpine AS builder

RUN apk add --no-cache gcc musl-dev

WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o /build/api ./cmd/api && \
    CGO_ENABLED=0 go build -ldflags="-s -w" -o /build/scheduler ./cmd/scheduler && \
    CGO_ENABLED=0 go build -ldflags="-s -w" -o /build/worker ./cmd/worker

# ------------------------------------------------------------
# Secrets management: age + sops yüklü temel katman
# ------------------------------------------------------------
FROM alpine:3.20 AS secrets-base

RUN apk add --no-cache \
    ca-certificates \
    tzdata \
    age \
    sops

# Entrypoint script (decrypts .env.secrets.enc if AGE_KEY provided)
COPY docker/entrypoint.sh /usr/local/bin/geolens-entrypoint
RUN chmod +x /usr/local/bin/geolens-entrypoint

# ============================================================
# Stage 2: API server
# ============================================================
FROM secrets-base AS api

COPY --from=builder /build/api /usr/local/bin/geolens-api

EXPOSE 8080

ENTRYPOINT ["geolens-entrypoint", "geolens-api"]

# ============================================================
# Stage 3: Scheduler
# ============================================================
FROM secrets-base AS scheduler

COPY --from=builder /build/scheduler /usr/local/bin/geolens-scheduler

ENTRYPOINT ["geolens-entrypoint", "geolens-scheduler"]

# ============================================================
# Stage 4: Worker
# ============================================================
FROM secrets-base AS worker

RUN apk add --no-cache curl

COPY --from=builder /build/worker /usr/local/bin/geolens-worker

ENTRYPOINT ["geolens-entrypoint", "geolens-worker"]
