// Package metrics provides Prometheus metric definitions for the GeoLens platform.
// Bu paket, import cycle'ı kırmak için platform/telemetry'den ayrılmıştır.
// Sadece Prometheus client ve stdlib import eder — proje içi bağımlılığı YOKTUR.
package metrics

import (
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

// ---- Metric Labels ----
const (
	LabelMethod    = "method"
	LabelPath      = "path"
	LabelStatus    = "status"
	LabelEngine    = "engine"
	LabelTenant    = "tenant"
	LabelStream    = "stream"
	LabelType      = "type"
	LabelComponent = "component"
)

// ---- API Metrics ----

var (
	// HTTPRequestTotal counts total HTTP requests by method, path, and status.
	HTTPRequestTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_http_requests_total",
		Help: "Toplam HTTP istek sayısı (method, path, status ayrımıyla)",
	}, []string{LabelMethod, LabelPath, LabelStatus})

	// HTTPRequestDuration tracks HTTP request latency distribution.
	HTTPRequestDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "geolens_http_request_duration_seconds",
		Help:    "HTTP istek süresi dağılımı (saniye)",
		Buckets: prometheus.DefBuckets,
	}, []string{LabelMethod, LabelPath})

	// HTTPRequestInFlight tracks currently in-flight requests.
	HTTPRequestInFlight = promauto.NewGauge(prometheus.GaugeOpts{
		Name: "geolens_http_requests_in_flight",
		Help: "Anlık işlenmekte olan HTTP istek sayısı",
	})
)

// ---- Queue Metrics ----

var (
	// QueueMessagesProduced counts messages produced to Redis Streams.
	QueueMessagesProduced = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_queue_messages_produced_total",
		Help: "Kuyruğa eklenen toplam mesaj sayısı (stream ayrımıyla)",
	}, []string{LabelStream})

	// QueueMessagesConsumed counts messages consumed from Redis Streams.
	QueueMessagesConsumed = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_queue_messages_consumed_total",
		Help: "Kuyruktan okunan toplam mesaj sayısı (stream ayrımıyla)",
	}, []string{LabelStream})

	// QueueMessagesFailed counts messages that failed processing.
	QueueMessagesFailed = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_queue_messages_failed_total",
		Help: "İşlenemeyen toplam mesaj sayısı (stream ayrımıyla)",
	}, []string{LabelStream})

	// QueueMessageProcessingDuration tracks message processing time.
	QueueMessageProcessingDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "geolens_queue_processing_duration_seconds",
		Help:    "Mesaj işleme süresi dağılımı (saniye)",
		Buckets: prometheus.DefBuckets,
	}, []string{LabelStream})

	// QueueDepth tracks current Redis Stream length.
	QueueDepth = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_queue_depth",
		Help: "Kuyruk derinliği (stream ayrımıyla)",
	}, []string{LabelStream})

	// QueueDeadLetterSize tracks dead letter queue size.
	QueueDeadLetterSize = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_queue_dead_letter_size",
		Help: "Dead letter queue boyutu (stream ayrımıyla)",
	}, []string{LabelStream})
)

// ---- Engine Metrics ----

var (
	// EngineCallsTotal counts total engine calls by engine name and tenant.
	EngineCallsTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_engine_calls_total",
		Help: "Motor çağrı sayısı (engine, tenant ayrımıyla)",
	}, []string{LabelEngine, LabelTenant})

	// EngineCallsFailed counts failed engine calls.
	EngineCallsFailed = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_engine_calls_failed_total",
		Help: "Başarısız motor çağrı sayısı (engine, tenant ayrımıyla)",
	}, []string{LabelEngine, LabelTenant})

	// EngineCallDuration tracks engine call latency.
	EngineCallDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "geolens_engine_call_duration_seconds",
		Help:    "Motor çağrı süresi dağılımı (saniye)",
		Buckets: []float64{0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0},
	}, []string{LabelEngine})

	// EngineResponseSize tracks engine response size in bytes.
	EngineResponseSize = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "geolens_engine_response_size_bytes",
		Help:    "Motor yanıt boyutu dağılımı (bayt)",
		Buckets: prometheus.ExponentialBuckets(100, 10, 5),
	}, []string{LabelEngine})
)

// ---- Account / Business Metrics ----
// Bu metrikler, worker'daki runAccountMetricsCollector goroutine'i tarafından
// her 5 dakikada bir periyodik DB sorguları ile populate edilir.
// Gauge tipi kullanılır çünkü her poll'da anlık snapshot alınır.

var (
	// ActiveUsers tracks the number of active users per tenant.
	ActiveUsers = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_active_users",
		Help: "Aktif kullanıcı sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// TotalBrands tracks total brands per tenant.
	TotalBrands = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_total_brands",
		Help: "Toplam marka sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// MeasurementsCompleted tracks completed measurements per tenant.
	MeasurementsCompleted = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_measurements_completed",
		Help: "Tamamlanan ölçüm sayısı (tenant ayrımıyla, periyodik snapshot)",
	}, []string{LabelTenant})

	// AuditsCompleted tracks completed site audits per tenant.
	AuditsCompleted = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_audits_completed",
		Help: "Tamamlanan site denetim sayısı (tenant ayrımıyla, periyodik snapshot)",
	}, []string{LabelTenant})

	// RecommendationsGenerated tracks generated recommendations per tenant.
	// Worker'daki collectAccountMetrics tarafından periyodik populate edilir (recommendation.results).
	RecommendationsGenerated = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_recommendations_generated",
		Help: "Oluşturulan öneri sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// EmailsSent tracks sent emails per tenant.
	// Delivery handler'daki başarılı SendEmail çağrıları tarafından increment edilir.
	EmailsSent = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_emails_sent",
		Help: "Gönderilen e-posta sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// ---- AI Analysis Metrics (0416-0419) ----

	// SentimentAnalysesCompleted tracks completed sentiment analyses per tenant.
	SentimentAnalysesCompleted = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_sentiment_analyses_completed_total",
		Help: "Tamamlanan duygu analizi sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// HallucinationsDetected tracks detected hallucinations per tenant.
	HallucinationsDetected = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_hallucinations_detected_total",
		Help: "Tespit edilen hallüsinasyon sayısı (tenant, severity ayrımıyla)",
	}, []string{LabelTenant, "severity"})

	// ConversationSnapshotsCreated tracks conversation replay snapshots.
	ConversationSnapshotsCreated = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_conversation_snapshots_total",
		Help: "Oluşturulan conversation replay snapshot sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// ResponseArchiveEntriesCreated tracks response archive entries.
	ResponseArchiveEntriesCreated = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_response_archive_entries_total",
		Help: "Arşivlenen yanıt girişi sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// TechnicalGEOAnalysesCompleted tracks completed technical GEO analyses.
	TechnicalGEOAnalysesCompleted = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_technical_geo_analyses_total",
		Help: "Tamamlanan teknik GEO analizi sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// ContentGEOAnalysesCompleted tracks completed content GEO analyses.
	ContentGEOAnalysesCompleted = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_content_geo_analyses_total",
		Help: "Tamamlanan içerik GEO analizi sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// CompetitiveGapAnalysesCompleted tracks completed competitive gap analyses.
	CompetitiveGapAnalysesCompleted = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_competitive_gap_analyses_total",
		Help: "Tamamlanan competitive gap analizi sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// GapAlertsTriggered tracks triggered gap alerts.
	GapAlertsTriggered = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_gap_alerts_triggered_total",
		Help: "Tetiklenen gap alert sayısı (gap_type ayrımıyla)",
	}, []string{"gap_type"})

	// GovernanceEventsTotal counts Faz 4 governance events consumed from q:governance (O-6).
	// GuardrailViolation, GateCheckDecision, IncidentOpened, DriftAlertTriggered, RedTeamRunCompleted.
	GovernanceEventsTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_governance_events_total",
		Help: "Faz 4 yönetişim olayı sayısı (event_type, tenant ayrımıyla)",
	}, []string{LabelType, LabelTenant})

	// WebhookDeliveriesTotal counts governance event webhook deliveries (O-6 bildirim tüketicisi).
	// status: sent, failed, no_target.
	WebhookDeliveriesTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_webhook_deliveries_total",
		Help: "Governance olay webhook gönderim sayısı (event_type, status ayrımıyla)",
	}, []string{LabelType, "status"})
)

// ---- ML Serving Circuit Breaker Metrics (0421 M-4) ----

var (
	// MLBreakerFailuresTotal counts circuit breaker trips per component.
	// Sentiment ve measure servisleri kendi breaker'larıyla (component: sentiment,
	// measure) serving hatalarını sayar — cooldown'a kaç kez girildiği izlenir.
	MLBreakerFailuresTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_ml_breaker_failures_total",
		Help: "ML serving devre kesici hata sayısı (component ayrımıyla)",
	}, []string{LabelComponent})

	// MLBreakerInCooldown tracks whether the component's breaker is currently
	// open (1=cooldown aktif, 0=normal). Serving erişilemezliği görünür kılar.
	MLBreakerInCooldown = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Name: "geolens_ml_breaker_in_cooldown",
		Help: "ML serving devre kesici cooldown durumu (1=aktif, 0=normal)",
	}, []string{LabelComponent})
)

// ---- Benchmark Sector Stats Metrics (FR-D5/C, NFR-13) ----

var (
	// BenchmarkMinTenants is the configured NFR-13 differential privacy threshold
	// (BENCHMARK_MIN_TENANTS env). Aggregator tarafından NewAggregator'da set
	// edilir — sektör istatistiklerinin yayınlanması için gereken minimum kiracı.
	// Grafana'da geolens_benchmark_tenant_count >= bu değer koşulu sufficient_data
	// durumunu gösterir (0422 pilot kalibrasyonu).
	BenchmarkMinTenants = promauto.NewGauge(prometheus.GaugeOpts{
		Name: "geolens_benchmark_min_tenants",
		Help: "NFR-13 sektör benchmark eşiği — minimum kiracı sayısı (BENCHMARK_MIN_TENANTS env)",
	})

	// BenchmarkTenantCount is the tenant count of the latest sector aggregation.
	// Aggregator tarafından her Aggregate koşusunda güncellenir.
	BenchmarkTenantCount = promauto.NewGauge(prometheus.GaugeOpts{
		Name: "geolens_benchmark_tenant_count",
		Help: "Son sektör toplulaştırmasındaki kiracı sayısı (eşikle karşılaştırılabilir)",
	})
)

// ---- SEO Sync Metrics (FR-B8 / HT2 sertleştirme) ----

var (
	// SEOSyncsTotal counts completed/failed SEO sync runs per platform and tenant.
	// HT2: worker telemetrisi — her sync işlemi için başarı/başarısızlık sayısı.
	SEOSyncsTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_seo_syncs_total",
		Help: "SEO sync koşusu sayısı (platform, tenant, sonuç ayrımıyla)",
	}, []string{LabelType, LabelTenant, LabelStatus})

	// SEOSyncDuration tracks SEO sync run duration per platform.
	SEOSyncDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "geolens_seo_sync_duration_seconds",
		Help:    "SEO sync süresi dağılımı (saniye)",
		Buckets: []float64{0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0},
	}, []string{LabelType})

	// SEOSyncRows tracks rows written per sync run (data volume).
	SEOSyncRows = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_seo_sync_rows_total",
		Help: "SEO sync'te yazılan satır sayısı (platform ayrımıyla)",
	}, []string{LabelType})
)
