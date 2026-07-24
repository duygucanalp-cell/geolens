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

# ============================================================
# Stage 2: API server
# ============================================================
FROM alpine:3.20 AS api

RUN apk add --no-cache ca-certificates tzdata

COPY --from=builder /build/api /usr/local/bin/geolens-api

EXPOSE 8080

ENTRYPOINT ["geolens-api"]

# ============================================================
# Stage 3: Scheduler
# ============================================================
FROM alpine:3.20 AS scheduler

RUN apk add --no-cache ca-certificates tzdata

COPY --from=builder /build/scheduler /usr/local/bin/geolens-scheduler

ENTRYPOINT ["geolens-scheduler"]

# ============================================================
# Stage 4: Worker
# ============================================================
FROM alpine:3.20 AS worker

RUN apk add --no-cache ca-certificates tzdata curl

COPY --from=builder /build/worker /usr/local/bin/geolens-worker

ENTRYPOINT ["geolens-worker"]
