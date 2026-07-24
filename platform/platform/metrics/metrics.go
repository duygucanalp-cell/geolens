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
// TODO(H14): Bu metrikler periyodik DB sorguları ile populate edilmelidir.
// Örn: her 5 dakikada bir cron job ile DB'den aktif kullanıcı sayısı okunup ActiveUsers.Set() çağrılmalıdır.
// Şu anda sadece tanımlıdır (register), değer atanmamıştır.

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

	// MeasurementsCompleted counts completed measurements.
	MeasurementsCompleted = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_measurements_completed_total",
		Help: "Tamamlanan ölçüm sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// AuditsCompleted counts completed site audits.
	AuditsCompleted = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_audits_completed_total",
		Help: "Tamamlanan site denetim sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// RecommendationsGenerated counts generated recommendations.
	RecommendationsGenerated = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_recommendations_generated_total",
		Help: "Oluşturulan öneri sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})

	// EmailsSent counts sent emails.
	EmailsSent = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "geolens_emails_sent_total",
		Help: "Gönderilen e-posta sayısı (tenant ayrımıyla)",
	}, []string{LabelTenant})
)
