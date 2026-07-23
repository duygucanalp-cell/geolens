package httpmw

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/geolens/platform/platform/db"
)

type contextKey string

const (
	CtxKeyRequestID   contextKey = "request_id"
	CtxKeyTenantID    contextKey = "tenant_id"
	CtxKeyWorkspaceID contextKey = "workspace_id"
	CtxKeyUserID      contextKey = "user_id"
	ctxKeyUserRole    contextKey = "user_role"
)

// PanicRecovery catches panics and returns 500.
func PanicRecovery(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rec := recover(); rec != nil {
				slog.Error("panik kurtarıldı", "panic", rec)
				http.Error(w, `{"error":"internal_server_error"}`, http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// RequestID injects a unique request ID into context and response header.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get("X-Request-ID")
		if id == "" {
			id = uuid.New().String()
		}
		w.Header().Set("X-Request-ID", id)
		ctx := context.WithValue(r.Context(), CtxKeyRequestID, id)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// Logger logs each request with structured fields.
func Logger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		wrapped := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}
		next.ServeHTTP(wrapped, r.WithContext(r.Context()))
		slog.Info("http istek",
			"method", r.Method,
			"path", r.URL.Path,
			"status", wrapped.statusCode,
			"duration_ms", time.Since(start).Milliseconds(),
			"request_id", GetRequestID(r.Context()),
		)
	})
}

// CORS sets cross-origin headers.
func CORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Request-ID, X-CSRF-Token")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// Role constants — config.membership_role enum ile uyumlu.
// Hiyerarşi: admin (3) > editor (2) > viewer (1).
const (
	RoleAdmin  = "admin"
	RoleEditor = "editor"
	RoleViewer = "viewer"
)

// roleWeights maps roles to numeric weights for hierarchy comparison.
var roleWeights = map[string]int{
	RoleAdmin:  3,
	RoleEditor: 2,
	RoleViewer: 1,
}

// TokenValidator validates a JWT token string and returns (userID, tenantID, role, error).
// Bu callback yaklaşımı, httpmw'nin auth paketine bağımlı olmasını engeller (import cycle).
type TokenValidator func(tokenStr string) (userID, tenantID, role string, err error)

// Authenticate validates the JWT token from the Authorization header.
// Public routes (health, auth register/login) are skipped via the router grouping.
func Authenticate(validate TokenValidator) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				http.Error(w, `{"error":"authorization_required"}`, http.StatusUnauthorized)
				return
			}

			tokenStr := authHeader
			if len(authHeader) > 7 && authHeader[:7] == "Bearer " {
				tokenStr = authHeader[7:]
			}

			userID, tenantID, _, err := validate(tokenStr)
			if err != nil {
				http.Error(w, `{"error":"invalid_token"}`, http.StatusUnauthorized)
				return
			}

			ctx := context.WithValue(r.Context(), CtxKeyUserID, userID)
			ctx = context.WithValue(ctx, CtxKeyTenantID, tenantID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// TenantContext sets the PostgreSQL session variable for RLS (SET LOCAL app.tenant_id).
func TenantContext(pool *db.Pool) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			tenantID := GetTenantID(r.Context())
			if tenantID != "" {
				// Her istekte tenant context'i PG session variable olarak ayarla (ADR-004)
				_, err := pool.Exec(r.Context(), "SELECT set_config('app.tenant_id', $1, true)", tenantID)
				if err != nil {
				slog.Warn("app.tenant_id session variable ayarlanamadı, RLS devre dışı", "tenant_id", tenantID, "error", err)
				}
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RequireRole returns a middleware that requires the authenticated user to have at least the specified role.
// Role hierarchy: admin > editor > viewer.
func RequireRole(minimumRole string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			role := GetUserRole(r.Context())
			if role == "" {
				http.Error(w, `{"error":"authentication_required"}`, http.StatusUnauthorized)
				return
			}

			if !hasSufficientRole(role, minimumRole) {
				http.Error(w, `{"error":"insufficient_permissions"}`, http.StatusForbidden)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

// RequireWorkspaceAccess checks that the user has a membership in the requested workspace.
func RequireWorkspaceAccess(pool *db.Pool) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			workspaceID := GetWorkspaceID(r.Context())
			userID := GetUserID(r.Context())
			tenantID := GetTenantID(r.Context())

			if workspaceID == "" || userID == "" {
				http.Error(w, `{"error":"access_denied"}`, http.StatusForbidden)
				return
			}

			// Membership kontrolü
			var role string
			err := pool.QueryRow(r.Context(), `
				SELECT role FROM config.memberships
				WHERE workspace_id = $1 AND user_id = $2 AND tenant_id = $3
			`, workspaceID, userID, tenantID).Scan(&role)

			if err != nil {
				http.Error(w, `{"error":"workspace_access_denied"}`, http.StatusForbidden)
				return
			}

			// Rolü context'e ekle (diğer middleware'ler için)
			ctx := context.WithValue(r.Context(), ctxKeyUserRole, role)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// hasSufficientRole checks if the user's role meets the minimum requirement.
// Kullanıcı rolünün ağırlığı, minimum rolün ağırlığından büyük veya eşit olmalıdır.
func hasSufficientRole(userRole, minimumRole string) bool {
	userWeight, userOk := roleWeights[userRole]
	minWeight, minOk := roleWeights[minimumRole]
	if !userOk || !minOk {
		return false
	}
	return userWeight >= minWeight
}

// RequireWorkspace checks that the workspace parameter exists.
func RequireWorkspace(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ws := r.PathValue("ws")
		if ws == "" {
			http.Error(w, `{"error":"workspace_required"}`, http.StatusBadRequest)
			return
		}
		ctx := context.WithValue(r.Context(), CtxKeyWorkspaceID, ws)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// GetTenantID returns the tenant ID from context.
func GetTenantID(ctx context.Context) string {
	if id, ok := ctx.Value(CtxKeyTenantID).(string); ok {
		return id
	}
	return ""
}

// GetWorkspaceID returns the workspace ID from context.
func GetWorkspaceID(ctx context.Context) string {
	if id, ok := ctx.Value(CtxKeyWorkspaceID).(string); ok {
		return id
	}
	return ""
}

// GetUserID returns the user ID from context.
func GetUserID(ctx context.Context) string {
	if id, ok := ctx.Value(CtxKeyUserID).(string); ok {
		return id
	}
	return ""
}

// GetUserRole returns the user's role from context (set by RequireWorkspaceAccess).
func GetUserRole(ctx context.Context) string {
	if role, ok := ctx.Value(ctxKeyUserRole).(string); ok {
		return role
	}
	return ""
}

// GetRequestID returns the request ID from context.
func GetRequestID(ctx context.Context) string {
	if id, ok := ctx.Value(CtxKeyRequestID).(string); ok {
		return id
	}
	return ""
}

// responseWriter wraps http.ResponseWriter to capture the status code.
type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}
