# GeoLens Java

Spring Boot 3 + JDK 25 tek ikili — üç süreç aynı jar'ın **Spring profilleri** ile çalışır.

| Profil | Görev |
|---|---|
| `api` (varsayılan) | REST API — 42 controller (:8080) |
| `scheduler` | Outbox dispatcher (event_outbox → Redis Stream) + panel cron taraması + haftalık digest |
| `worker` | Redis Stream tüketicileri (q:measure → ölçüm/skor/analiz, q:governance → webhook) |

Şema **Flyway** ile yönetilir: `src/main/resources/db/migration/V*__*.sql` (52 migration);
ilk başlayan süreç uygular.

## Geliştirme ortamı

Altyapı (PostgreSQL + Redis + MinIO + ML serving + web) compose ile ayağa kaldırılır:

```bash
# platform/ altından
make dev        # docker compose up -d
```

Üç süreç ayrı terminalde:

```bash
# 1) API (REST, :8081 — web/ proxy'si /v1 → 8080 için PORT=8080)
cd java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=api -Dspring-boot.run.arguments=--PORT=8080

# 2) Worker (Redis Stream tüketicileri)
cd java && SPRING_PROFILES_ACTIVE=worker ./mvnw spring-boot:run

# 3) Scheduler (outbox dispatcher + panel cron + digest)
cd java && SPRING_PROFILES_ACTIVE=scheduler ./mvnw spring-boot:run
```

Env değişkenleri: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`,
`REDIS_HOST/REDIS_PORT`, `CONSUMER_GROUP`, `S3_*`, `*_API_KEY`, `SENDGRID_*`,
`ML_SERVING_URL`, `JWT_SECRET`. Ayrıntılar `src/main/resources/application*.yml` içinde.

## Docker

```bash
cd platform
docker compose up -d --build
```

- `java-api` :8080 — REST (web/ proxy'si `/v1` → 8080)
- `java-worker` / `java-scheduler` — HTTP açmaz (web-application-type: none)
- Şemayı **Flyway** yönetir (initdb mount yok; ilk başlayan Java süreci 52 migration'ı uygular).

## Yapılandırma özeti

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `PORT` | 8081 | API portu (compose'ta 8080 — web/ proxy'si) |
| `queue.poll-ms` / `QUEUE_POLL_MS` | 30000 | Outbox dispatcher tarama aralığı |
| `queue.panel-scan-ms` / `QUEUE_PANEL_SCAN_MS` | 60000 | Panel cron tarama aralığı |
| `queue.consumer-group` / `CONSUMER_GROUP` | cg:measure | Redis Stream consumer group |
| `queue.consumer-name` / `CONSUMER_NAME` | worker-1 | Worker consumer adı |
| `queue.block-ms` / `QUEUE_BLOCK_MS` | 5000 | XREADGROUP BLOCK süresi |
| `queue.batch-size` / `QUEUE_BATCH_SIZE` | 10 | Her okumada alınan mesaj sayısı |
| `queue.dispatcher-enabled` / `QUEUE_DISPATCHER_ENABLED` | true | Dispatcher açık/kapalı |
| `queue.workers-enabled` / `QUEUE_WORKERS_ENABLED` | true | Worker tüketicileri açık/kapalı |

## Test

```bash
cd java
./mvnw test          # tüm testler (integration grubu hariç)
./mvnw test -Dsurefire.groups=integration   # Docker ile entegrasyon testleri
```

## Bilinen farklar

- Worker'da Prometheus metrikleri (queue depth, engine calls, breaker) henüz toplanmıyor —
  `q:measure`/`q:governance` tüketimi ve outbox akışı çalışır durumda.
- Scheduler ayrı `/metrics` sunucusu açmaz (web-application-type: none).
- `GuardrailServiceException` kendine özgü tasarımı korur; ortak `ServiceException` dışında tutulur.

---

## Tarihçe — Go → Java geçişi (arşiv)

GeoLens Platform, Go (chi/pgx/Redis Streams; 3 ayrı süreç: api/scheduler/worker) ile
geliştirilmişti. Ağustos 2026'da uygulama tamamen Spring Boot 3 (JDK 25) tek jar'a taşındı;
Go kod tabanı (`cmd/`, `internal/`, `engine/`, `platform/`), kök Dockerfile ve
`migrations/` repodan kaldırıldı. Geçiş dönemindeki süreç eşlemesi:

| Go süreci | Java profili |
|---|---|
| `cmd/api` (:8080) | `api` (varsayılan) |
| `cmd/scheduler` | `scheduler` |
| `cmd/worker` | `worker` |

**Rota parity:** Go `cmd/api/main.go` (175 endpoint) ile Java controller'ları birebir eşleşir
(path parametreleri normalize edilerek otomatik karşılaştırıldı). Geçişte hizalananlar:
- `/v1/agents/traces*` — kökte (eskiden workspaces altındaydı)
- `/v1/workspaces/{ws}/hallucination*` — sentiment'in kardeşi olarak ayrı kök (eskiden /sentiment altındaydı)
- `/v1/workspaces/{ws}/retention*` — workspace altına taşındı
- `GET /v1/workspaces/{ws}/benchmark/context` — eklendi (T2 anonim kıyas + NFR-13 DP eşiği)
