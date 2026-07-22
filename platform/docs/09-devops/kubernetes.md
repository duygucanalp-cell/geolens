# Kubernetes (Rezerve)

| Alan | Değer |
|---|---|
| Doküman ID | 09-devops/kubernetes |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 09-devops/*, 0509, 0510, 0206 |

---

## 1. Amaç

Bu doküman GeoLens Platform için Kubernetes (K8s) dağıtım yapılandırmasını tanımlar. **MVP'de Kubernetes kullanılmayacaktır.** Docker Compose + VM ile başlanır, K8s ihtiyacı pilot sonrası değerlendirilir.

---

## 2. K8s Geçiş Kriteri

| Kriter | MVP Çözümü | K8s Geçişi |
|--------|:----------:|:----------:|
| Worker sayısı | 2-3 Docker container | 5+ worker |
| Ortam sayısı | 2 (dev + prod) | 3+ (dev/staging/prod) |
| Yük dengeleme | Nginx reverse proxy | Ingress + Service |
| Otomatik ölçekleme | Manuel | HPA |
| Sıfır kesinti dağıtım | — | Rolling update |

---

## 3. Rezerve Manifest Yapısı (Gelecek)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: geo-api
spec:
  replicas: 2
  selector:
    matchLabels:
      app: geo-api
  template:
    metadata:
      labels:
        app: geo-api
    spec:
      containers:
      - name: api
        image: geolens/api:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
```

---

## 4. K8s Bileşenleri (Rezerve)

| Bileşen | Açıklama |
|:-------:|----------|
| **Deployment** | api, scheduler, worker (3 deployment) |
| **Service** | ClusterIP (api), Headless (worker) |
| **Ingress** | TLS sonlandırma, path routing |
| **ConfigMap** | Uygulama yapılandırması |
| **Secret** | DB, Redis, S3, API anahtarları |
| **HPA** | Worker CPU bazlı otomatik ölçekleme |
| **PVC** | PostgreSQL ve Redis veri kalıcılığı |

---

## 5. Alternatif: Docker Compose + VM

MVP'de K8s yerine Docker Compose kullanılır:

| Bileşen | MVP | K8s |
|---------|:---:|:---:|
| Orkestrasyon | Docker Compose | Kubernetes |
| Load balancer | Nginx reverse proxy | Ingress |
| Secret yönetimi | .env dosyası | Kubernetes Secrets |
| Monitoring | Docker logs | Prometheus + Grafana |
| Ölçekleme | Manuel | HPA |

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-85 | VM sağlayıcı stratejisi: küresel bulut TR bölge (self-host VM + yönetilen PG/KMS). MVP'de K8s yok, Docker Compose + VM ile başlanır. | AVIP 0402 O-1 (TL 21.07.2026) |
| D-86 | Staging yedek/geri dönüş: DB dump (pg_dump) + imaj geri terfisi. Deployment öncesi otomatik pg_dump alınır. | AVIP 0403 O-4 (TL 21.07.2026) |
| D-19 | RTO/RPO hedefleri: RPO 1 saat, RTO 8 saat (MVP tasarım hedefi, pilot verisiyle kalibre edilir). | AVIP 0402 O-3 (TL 21.07.2026) |

---

## Kaynaklar

- 09-devops/docker — Docker Compose yapısı
- 09-devops/ci-cd — CI/CD pipeline
- 0509 Scalability — ölçeklenebilirlik
- 0510 Deployment — dağıtım mimarisi
- 0206 Roadmap — HT1/HT2 pencereleri

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: K8s rezervasyonu, geçiş kriterleri, manifest yapısı, Docker Compose alternatifi. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-85 (VM stratejisi), D-86 (staging yedek), D-19 (RTO/RPO). |
