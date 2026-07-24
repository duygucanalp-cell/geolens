package httpmw

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"
)

// CacheConfig holds cache middleware configuration.
type CacheConfig struct {
	TTL time.Duration
	RDB *redis.Client
}

// cacheBuffer only buffers the response — does NOT write to the real ResponseWriter.
// Bu sayede header'lar (ETag, Cache-Control) body'den önce gönderilebilir.
type cacheBuffer struct {
	statusCode int
	header     http.Header
	body       bytes.Buffer
}

func (cb *cacheBuffer) WriteHeader(code int) {
	cb.statusCode = code
}

func (cb *cacheBuffer) Write(b []byte) (int, error) {
	return cb.body.Write(b)
}

func (cb *cacheBuffer) Header() http.Header {
	return cb.header
}

func computeETag(data []byte) string {
	h := sha256.Sum256(data)
	return fmt.Sprintf(`"%s"`, hex.EncodeToString(h[:8]))
}

// CacheMiddleware caches GET responses in Redis and supports ETag-based conditional requests.
// Buffer-only write: yanıt önce buffer'a yazılır, header'lar set edildikten sonra client'a gönderilir.
func CacheMiddleware(cfg CacheConfig) func(http.Handler) http.Handler {
	ttl := cfg.TTL
	if ttl == 0 {
		ttl = 30 * time.Second
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodGet {
				next.ServeHTTP(w, r)
				return
			}

			cacheKey := fmt.Sprintf("cache:http:%s:%s", r.Method, r.URL.RequestURI())

			// Cache HIT: Redis'te varsa direkt döndür
			if cfg.RDB != nil {
				ifNoneMatch := r.Header.Get("If-None-Match")
				cached, err := cfg.RDB.Get(r.Context(), cacheKey).Result()
				if err == nil && cached != "" {
					cachedData := []byte(cached)
					cachedETag := computeETag(cachedData)

					w.Header().Set("ETag", cachedETag)
					w.Header().Set("Cache-Control", fmt.Sprintf("public, max-age=%d", int(ttl.Seconds())))

					if ifNoneMatch == cachedETag {
						w.WriteHeader(http.StatusNotModified)
						return
					}

					w.Header().Set("X-Cache", "HIT")
					w.Write(cachedData)
					return
				}
			}

			// Cache MISS: önce buffer'a yaz, header'ları set et, SONRA client'a gönder
			w.Header().Set("X-Cache", "MISS")
			buf := &cacheBuffer{
				statusCode: http.StatusOK,
				header:     make(http.Header),
			}
			next.ServeHTTP(buf, r)

			// Header'ları gerçek ResponseWriter'a kopyala
			for k, v := range buf.header {
				w.Header()[k] = v
			}

			if buf.statusCode == http.StatusOK && cfg.RDB != nil && buf.body.Len() > 0 {
				bodyData := buf.body.Bytes()
				etag := computeETag(bodyData)

				w.Header().Set("ETag", etag)
				w.Header().Set("Cache-Control", fmt.Sprintf("public, max-age=%d", int(ttl.Seconds())))

				// Redis'e kaydet
				if err := cfg.RDB.Set(r.Context(), cacheKey, string(bodyData), ttl).Err(); err != nil {
					slog.Warn("cache: redis kaydetme hatası", "error", err)
				}
			}

			// Header'ları gönder (ilk WriteHeader veya Write tetikler)
			w.WriteHeader(buf.statusCode)
			if buf.body.Len() > 0 {
				w.Write(buf.body.Bytes())
			}
		})
	}
}
