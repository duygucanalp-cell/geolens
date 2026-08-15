# 0510 · Dağıtım Mimarisi (Deployment)

| Alan | Değer |
|---|---|
| Doküman ID | 0510 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0509, 0402, 0403, 0204, ADR-014 |

---

## 1. Amaç

Bu doküman GeoLens Platform'un dağıtım mimarisini tanımlar: ortam yapısı, Docker konteyner düzeni, CI/CD entegrasyonu ve altyapı gereksinimleri.

---

## 2. Ortamlar

| Ortam | Amaç | Konfigürasyon |
|:-----:|------|:-------------:|
| **Dev** | Geliştirme | Docker Compose (PG + Redis + S3 mock) |
| **CI** | Test | Ephemeral (testcontainers) |
| **Staging** | Doğrulama | 1 VM + managed PG/Redis/S3 |
| **Production** | Canlı | 2+ VM + managed PG/Redis/S3 |
| **Pilot** | Erken erişim | Production ile aynı, ayrı tenant |

---

## 3. Docker Konteyner Düzeni

| Konteyner | Temel İmaj | Port | Sağlık Kontrolü |
|-----------|:----------:|:----:|:---------------:|
| java-api | eclipse-temurin:25-jre (java/Dockerfile, target api) | 8080 | GET /actuator/health |
| java-scheduler | eclipse-temurin:25-jre (java/Dockerfile, target scheduler) | — | Süreç canlılığı (keep-alive) |
| java-worker | eclipse-temurin:25-jre (java/Dockerfile, target worker) | — | Kuyruk tüketimi |
| java-web | node:20-alpine + Nginx | 3000 | Nginx statik |
| postgres | postgres:16-alpine | 5432 | pg_isready |
| redis | redis:7-alpine | 6379 | redis-cli ping |
| minio (dev) | minio/minio | 9000 | S3 mock |
| ml-serving | Python ONNX (ml/Dockerfile) | 8900 | GET /health |

---

## 4. Sistem Gereksinimleri (Minimum)

| Bileşen | CPU | RAM | Disk |
|---------|:---:|:---:|:----:|
| API | 1 vCPU | 1 GB | — |
| Worker | 1 vCPU | 2 GB | — |
| PostgreSQL | 2 vCPU | 4 GB | 50 GB SSD |
| Redis | 1 vCPU | 2 GB | — |
| S3 | — | — | 100 GB+ |

---

## 5. CI/CD Entegrasyonu

| Aşama | Araç | Açıklama |
|:-----:|:----:|----------|
| Derleme | mvn compile | Derleme kontrolü (D1-D7 bağımlılık kuralları) |
| Test | mvn test | Birim testler (mock tabanlı, docker gerektirmez) |
| Paketleme | mvn package | Tek jar (api/worker/scheduler profilleri) |
| İmaj | docker build | java/Dockerfile multi-stage |
| Yayın | docker push | Konteyner kaydı |
| Dağıtım | SSH/Ansible | Staging → Production |

---

## Kaynaklar

- 0501 System Architecture — konteyner modeli
- 0509 Scalability — kapasite planlaması
- 0402 Environments — Docker, ortam yönetimi
- 0403 CI/CD — boru hattı
- archive/avip-v1/0402-environments-docker.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: ortam yapısı, Docker düzeni, sistem gereksinimleri, CI/CD entegrasyonu. |
| 1.1 | 15.08.2026 | **Java geçişi:** Konteyner düzeni java-api/java-scheduler/java-worker + ml-serving (eclipse-temurin:25) ile güncellendi; CI/CD aşamaları Maven araçlarına çevrildi. ADR-014 ilişkili listesine eklendi. |
