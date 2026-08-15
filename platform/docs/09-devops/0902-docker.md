# 0902 · Docker

| Alan | Değer |
|---|---|---|
| Doküman ID | 0902 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0510, 0900–0905, 0402, ADR-014 |

---

## 1. Amaç

Bu doküman GeoLens Platform Docker konteyner yapılandırmasını tanımlar: Dockerfile yapısı, Docker Compose ortamları ve çok aşamalı derleme stratejisi.

---

## 2. Dockerfile Yapısı (Backend)

Java backend tek jar üretir; profiller `java/Dockerfile` multi-stage target'larıyla seçilir (api/worker/scheduler). SOPS+Age secrets çözümü `docker/entrypoint.sh` ile runtime'da yapılır.

```dockerfile
# java/Dockerfile (context: platform/)
# Stage 1: Maven build (jOOQ codegen + Spring Boot repackage)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY java/pom.xml ./pom.xml
RUN mvn -q -B dependency:go-offline
COPY java/src ./src
RUN mvn -q -B package -DskipTests

# Stage 2: Runtime (secrets entrypoint dahil)
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /build/target/geolens-*.jar /app/app.jar
COPY docker/entrypoint.sh /usr/local/bin/geolens-entrypoint

# Stage 3-5: api / worker / scheduler (SPRING_PROFILES_ACTIVE env'i ile)
FROM runtime AS api
ENV SPRING_PROFILES_ACTIVE=api
EXPOSE 8080
ENTRYPOINT ["geolens-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]
```

> Build komutu: `docker build -f java/Dockerfile --target api .` (worker/scheduler için `--target worker|scheduler`).

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
      dockerfile: java/Dockerfile
      target: api
    ports:
      - "8080:8080"
    depends_on: [postgres, redis, minio]
    environment:
      SPRING_PROFILES_ACTIVE: api
      DATABASE_URL: jdbc:postgresql://postgres:5432/geolens
      DATABASE_USER: geolens
      DATABASE_PASSWORD: devpassword
      REDIS_HOST: redis
      REDIS_PORT: 6379
      S3_ENDPOINT: http://minio:9000

volumes:
  pgdata:
```

---

## 5. Çok Aşamalı Derleme Avantajları

| Avantaj | Açıklama |
|---------|----------|
| **Tek imaj** | eclipse-temurin:25-jre, tüm profiller tek jar + tek Dockerfile |
| **Güvenlik** | Derleme araçları runtime imajında yok; secrets entrypoint ile çözülür |
| **Hızlı derleme** | Maven bağımlılık önbelleği (go-offline) ile katmanlı |
| **Profil seçimi** | api/worker/scheduler target'larıyla ayrı konteynerler |

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
| 1.2 | 15.08.2026 | **Java geçişi:** Backend Dockerfile `java/Dockerfile` (eclipse-temurin:25, api/worker/scheduler target) ile güncellendi; compose örneği Spring env'leriyle yenilendi; çok aşamalı derleme tablosu Java'ya göre yeniden ifade edildi. ADR-014 ilişkili listesine eklendi. |
