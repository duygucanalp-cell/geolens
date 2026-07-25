# 0902 · Docker

| Alan | Değer |
|---|---|---|
| Doküman ID | 0902 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0510, 0900–0905, 0402 |

---

## 1. Amaç

Bu doküman GeoLens Platform Docker konteyner yapılandırmasını tanımlar: Dockerfile yapısı, Docker Compose ortamları ve çok aşamalı derleme stratejisi.

---

## 2. Dockerfile Yapısı (Backend)

```dockerfile
# Build stage
FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/api ./cmd/api
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/scheduler ./cmd/scheduler
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/worker ./cmd/worker

# Runtime stage
FROM alpine:3.19
RUN apk --no-cache add ca-certificates tzdata
COPY --from=builder /app/api /app/scheduler /app/worker /usr/local/bin/
EXPOSE 8080
CMD ["api"]
```

---

## 3. Dockerfile Yapısı (Frontend)

```dockerfile
# Build stage
FROM node:20-alpine AS builder
WORKDIR /app
COPY web/package*.json ./
RUN npm ci
COPY web/ .
RUN npm run build

# Runtime stage (Nginx)
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY web/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## 4. Docker Compose (Dev Ortamı)

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: geolens
      POSTGRES_USER: geolens
      POSTGRES_PASSWORD: devpassword
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  minio:
    image: minio/minio
    environment:
      MINIO_ROOT_USER: geolens
      MINIO_ROOT_PASSWORD: devpassword
    ports:
      - "9000:9000"
      - "9001:9001"
    command: server /data --console-address ":9001"

  api:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime
    command: api
    ports:
      - "8080:8080"
    depends_on: [postgres, redis, minio]
    environment:
      DB_URL: postgres://geolens:devpassword@postgres:5432/geolens
      REDIS_URL: redis://redis:6379
      S3_ENDPOINT: http://minio:9000

volumes:
  pgdata:
```

---

## 5. Çok Aşamalı Derleme Avantajları

| Avantaj | Açıklama |
|---------|----------|
| **Küçük imaj** | Alpine tabanlı, ~20MB (Go) / ~50MB (Nginx) |
| **Güvenlik** | Derleme araçları runtime imajında yok |
| **Hızlı derleme** | Modül önbelleği ile katmanlı |
| **Tek Dockerfile** | Tüm Go süreçleri aynı Dockerfile'dan |

---

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-85** | **VM sağlayıcı stratejisi:** Küresel bulut TR bölge (self-host VM + yönetilen PG/KMS). TL 21.07.2026. | AVIP 0402 O-1 |
| **D-19** | **RTO/RPO hedefleri:** RPO 1 saat, RTO 8 saat. PO+TL 21.07.2026. | AVIP 0402 O-3 |
| **D-16** | **E-posta sağlayıcısı:** SendGrid (Docker Compose dışı — API tabanlı). TL 21.07.2026. | AVIP 0402 O-2 |

## Kaynaklar

- 0510 Deployment — dağıtım mimarisi
- 0901 — CI/CD entegrasyonu
- 0402 Environments — ortam yönetimi
- archive/avip-v1/0402-environments-docker.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: Dockerfile yapısı (backend/frontend), Docker Compose dev ortamı, çok aşamalı derleme. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-85 (VM stratejisi), D-19 (RTO/RPO), D-16 (SendGrid). Devralınan Kararlar eklendi. |
