# GeoLens Java (Go → Java geçişi)

Spring Boot 3 + JDK 25 tek ikili — Go'daki **3 süreç** (api / scheduler / worker) aynı
jar'ın **Spring profilleri** ile modellenir. Mevcut durum ve karşılıklar:

| Go süreci | Java profili | Görev |
|---|---|---|
| `cmd/api` (:8080) | `api` (varsayılan) | REST API — 42 controller |
| `cmd/scheduler` | `scheduler` | Outbox dispatcher (event_outbox → Redis Stream) + panel cron taraması + haftalık digest |
| `cmd/worker` | `worker` | Redis Stream tüketicileri (q:measure → ölçüm/skor/analiz, q:governance → webhook) |

Şema kurulumu Go'daki `docker-entrypoint-initdb.d` yerine **Flyway** ile yapılır:
`platform/migrations/*.sql` (52 adet) `src/main/resources/db/migration/V*__*.sql`
olarak kopyalanır ve ilk başlayan süreç uygular.

## Geliştirme ortamı

Bağımlılıklar (PostgreSQL + Redis + MinIO) mevcut compose ile ayağa kaldırılır:

```bash
# platform/ altından — Go compose'u (postgres/redis/minio/ml-serving)
make dev

# Şema: migrations mount'u initdb üzerinden uygulanır (Flyway atlanabilir);
# alternatif olarak Flyway'i denemek için postgres mount'unu kaldırıp Java'yı çalıştırın.
```

Üç süreç ayrı terminalde:

```bash
# 1) API (REST, :8081 — web/ proxy'si /v1 → 8080 için PORT=8080)
cd platform/java && ./mvnw spring-boot:run -Dspring-boot.run.profiles=api -Dspring-boot.run.arguments=--PORT=8080

# 2) Worker (Redis Stream tüketicileri)
cd platform/java && SPRING_PROFILES_ACTIVE=worker ./mvnw spring-boot:run

# 3) Scheduler (outbox dispatcher + panel cron + digest)
cd platform/java && SPRING_PROFILES_ACTIVE=scheduler ./mvnw spring-boot:run
```

Env değişkenleri (Go ile birebir): `DATABASE_URL`, `REDIS_HOST/REDIS_PORT`,
`CONSUMER_GROUP`, `S3_*`, `*_API_KEY`, `SENDGRID_*`, `ML_SERVING_URL`, `JWT_SECRET`.
Ayrıntılar `src/main/resources/application*.yml` içinde.

## Docker

Java stack'i ana compose'da (Go api/scheduler/worker kaldırıldı):

```bash
cd platform
docker compose up -d --build
```

- `java-api` :8080 — REST (web/ proxy'si `/v1` → 8080)
- `java-worker` / `java-scheduler` — HTTP açmaz (web-application-type: none)
- `postgres` initdb migration mount'u yok — şemayı **Flyway** yönetir
  (ilk başlayan Java süreci 52 migration'ı uygular).

## Yapılandırma özeti

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `PORT` | 8081 | API portu (Go parity: 8080) |
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
cd platform/java
./mvnw test          # tüm testler (integration grubu hariç)
./mvnw test -Dsurefire.groups=integration   # Docker ile entegrasyon testleri
```

## Rota parity

Go `cmd/api/main.go` (175 endpoint) ile Java controller'ları birebir eşleşir:
path parametreleri normalize edilerek otomatik karşılaştırılır. Bu refactor'da hizalananlar:
- `/v1/agents/traces*` — kökte (Go ile aynı; eskiden workspaces altındaydı)
- `/v1/workspaces/{ws}/hallucination*` — sentiment'in kardeşi olarak ayrı kök (eskiden /sentiment altındaydı)
- `/v1/workspaces/{ws}/retention*` — workspace altına taşındı
- `GET /v1/workspaces/{ws}/benchmark/context` — eklendi (T2 anonim kıyas + NFR-13 DP eşiği)

## Bilinen farklar (parity)

- Worker Go'daki Prometheus metrikleri (queue depth, engine calls, breaker) henüz Java'da
  toplanmıyor — `q:measure`/`q:governance` tüketimi ve outbox akışı birebir port edildi.
- Scheduler'ın ayrı `/metrics` sunucusu yok (web-application-type: none).
- `GuardrailServiceException` Go tarafındaki özel tasarımı korur; ortak `ServiceException` dışında tutulur.
