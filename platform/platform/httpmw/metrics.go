package httpmw

import (
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/metrics"
)

// responseWriterWithStatus wraps http.ResponseWriter to capture status code.
// Bu, middleware.go'daki responseWriter'dan bağımsızdır çünkü metriklerin
// kendi writer wrapper'ına ihtiyacı vardır (import cycle olmaması için).
type metricsResponseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (mw *metricsResponseWriter) WriteHeader(code int) {
	mw.statusCode = code
	mw.ResponseWriter.WriteHeader(code)
}

// writeStatus returns the HTTP status code, defaulting to 200 if not set.
func (mw *metricsResponseWriter) writeStatus() int {
	if mw.statusCode == 0 {
		return http.StatusOK
	}
	return mw.statusCode
}

// MetricsMiddleware collects HTTP request metrics (count, duration, in-flight).
// Her istek için method, path ve status code ayrımıyla Prometheus metriklerini günceller.
func MetricsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		// İn-flight gauge artır
		metrics.HTTPRequestInFlight.Inc()
		defer metrics.HTTPRequestInFlight.Dec()

		// ResponseWriter wrapper ile status code yakala
		wrapped := &metricsResponseWriter{ResponseWriter: w, statusCode: http.StatusOK}
		next.ServeHTTP(wrapped, r)

		// Duration (saniye)
		duration := time.Since(start).Seconds()

		// Path: chi route pattern'ini kullan (ör: /v1/workspaces/{ws}/brands)
		path := r.URL.Path
		if r.Context().Value(chi.RouteCtxKey) != nil {
			if routePath := chi.RouteContext(r.Context()).RoutePattern(); routePath != "" {
				path = routePath
			}
		}

		method := r.Method
		status := strconv.Itoa(wrapped.writeStatus())

		// Metrikleri kaydet
		metrics.HTTPRequestTotal.WithLabelValues(method, path, status).Inc()
		metrics.HTTPRequestDuration.WithLabelValues(method, path).Observe(duration)
	})
}
