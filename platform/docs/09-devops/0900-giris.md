# 0900 · DevOps Katmanı

| Alan | Değer |
|---|---|
| Doküman ID | 0900 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | 0901–0905, 0510, 0404 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un DevOps katmanı dokümantasyonuna giriş niteliğindedir. Altyapı ve operasyon prensiplerini tanımlar.

---

## 2. Dizin Kapsamı

| # | Doküman | Konu |
|:-:|---------|------|
| 0901 | CI/CD | GitHub Actions, build pipeline, kalite kapıları |
| 0902 | Docker | Container yapısı, Dockerfile, multi-stage build |
| 0903 | Kubernetes | K8s manifestleri, Helm chart, scaling |
| 0904 | Monitoring | Prometheus, Grafana, alerting |
| 0905 | Yedekleme | Veritabanı yedekleme, felaket kurtarma |

---

## 3. Ortamlar

| Ortam | Amaç | Altyapı |
|-------|------|---------|
| Development | Yerel geliştirme | Docker Compose |
| Demo | Müşteri gösterimi | Tek node (Docker) |
| Pilot | Gerçek kullanıcı | VPS / Cloud |
| Production | Canlı | Kubernetes (HT1) |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: DevOps katmanı giriş ve ortam yapısı |
