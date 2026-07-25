package httpmw

import (
	"context"
	"net/http"
	"time"

	"log/slog"
)

// ---- Timeout Middleware ----

// RequestTimeout sets a maximum duration for request handling.
// Sunucu genel timeout'ının yanında handler bazında ince ayar sağlar.
func RequestTimeout(timeout time.Duration) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx, cancel := context.WithTimeout(r.Context(), timeout)
			defer cancel()

			done := make(chan struct{})
			go func() {
				next.ServeHTTP(w, r.WithContext(ctx))
				close(done)
			}()

			select {
			case <-done:
				return
			case <-ctx.Done():
				if ctx.Err() == context.DeadlineExceeded {
					slog.Warn("istek zaman aşımı",
						"method", r.Method,
						"path", r.URL.Path,
						"timeout", timeout,
					)
					http.Error(w, `{"error":"request_timeout"}`, http.StatusGatewayTimeout)
				}
			}
		})
	}
}

// ---- Request Validation Middleware ----

// ValidateContentType ensures the request has the expected Content-Type for POST/PUT/PATCH
// when the request has a body. Requests without a body (no Content-Length, no Transfer-Encoding) are allowed.
func ValidateContentType(allowedTypes ...string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodPost || r.Method == http.MethodPut || r.Method == http.MethodPatch {
				cl := r.Header.Get("Content-Length")
				te := r.Header.Get("Transfer-Encoding")
				if cl == "" && te == "" {
					next.ServeHTTP(w, r)
					return
				}

				contentType := r.Header.Get("Content-Type")
				if contentType == "" {
					http.Error(w, `{"error":"content_type_required"}`, http.StatusUnsupportedMediaType)
					return
				}

				for _, allowed := range allowedTypes {
					if len(contentType) >= len(allowed) && contentType[:len(allowed)] == allowed {
						next.ServeHTTP(w, r)
						return
					}
				}

				http.Error(w, `{"error":"unsupported_content_type"}`, http.StatusUnsupportedMediaType)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// ---- Body Size Limiter ----

// MaxBodySize limits the maximum request body size.
func MaxBodySize(maxBytes int64) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
			next.ServeHTTP(w, r)
		})
	}
}

// ---- Secure Headers Middleware ----

// SecureHeaders sets security-related HTTP headers.
func SecureHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("X-XSS-Protection", "1; mode=block")
		w.Header().Set("Referrer-Policy", "strict-origin-when-cross-origin")
		w.Header().Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		next.ServeHTTP(w, r)
	})
}
