package auth

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httputil"
)

// ---- Request/Response Types ----

type registerRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	Name     string `json:"name"`
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type authResponse struct {
	Token     string `json:"token"`
	ExpiresAt string `json:"expires_at"`
	UserID    string `json:"user_id"`
	TenantID  string `json:"tenant_id"`
	Role      string `json:"role"`
}



// Handler holds dependencies for auth HTTP handlers.
type Handler struct {
	pool  *db.Pool
	jwt   *JWTService
}

// NewHandler creates a new auth handler.
func NewHandler(pool *db.Pool, jwt *JWTService) *Handler {
	return &Handler{pool: pool, jwt: jwt}
}

// Register handles POST /v1/auth/register
// Self-serve kayıt: e-posta + şifre + isim, ödeme bilgisi istenmez (D-07).
// Yeni kiracı + yönetici kullanıcı oluşturur.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	if req.Email == "" || req.Password == "" || req.Name == "" {
			httputil.WriteError(w, http.StatusBadRequest, "e-posta, şifre ve isim zorunludur")
		return
	}

	if len(req.Password) < 8 {
			httputil.WriteError(w, http.StatusBadRequest, "şifre en az 8 karakter olmalıdır")
		return
	}

	// Hash password
	hashedPW, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		slog.Error("şifre hash hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	// Transaction: create tenant + user
	ctx := r.Context()
	tx, err := h.pool.Begin(ctx)
	if err != nil {
		slog.Error("transaction başlatma hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}
	defer tx.Rollback(ctx)

	var tenantID, userID string
	err = tx.QueryRow(ctx, `
		INSERT INTO identity.tenants (id, name, slug, tier)
		VALUES (gen_random_uuid()::text, $1, lower(regexp_replace($1, '[^a-z0-9]', '', 'g')), 'free')
		RETURNING id
	`, req.Name).Scan(&tenantID)
	if err != nil {
		slog.Error("kiracı oluşturma hatası", "error", err)
			httputil.WriteError(w, http.StatusConflict, "bu isimle kayıt yapılamaz")
		return
	}

	err = tx.QueryRow(ctx, `
		INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name)
		VALUES (gen_random_uuid()::text, $1, $2, $3, 'admin', $4)
		RETURNING id
	`, tenantID, req.Email, string(hashedPW), req.Name).Scan(&userID)
	if err != nil {
		slog.Error("kullanıcı oluşturma hatası", "error", err)
			httputil.WriteError(w, http.StatusConflict, "bu e-posta zaten kayıtlı")
		return
	}

	if err := tx.Commit(ctx); err != nil {
		slog.Error("transaction commit hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	// Generate JWT
	token, expiresAt, err := h.jwt.GenerateToken(userID, tenantID, "admin")
	if err != nil {
		slog.Error("jwt oluşturma hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "kayıt başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, authResponse{
		Token:     token,
		ExpiresAt: expiresAt.Format(time.RFC3339),
		UserID:    userID,
		TenantID:  tenantID,
		Role:      "admin",
	})
}

// Login handles POST /v1/auth/login
func (h *Handler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	if req.Email == "" || req.Password == "" {
		httputil.WriteError(w, http.StatusBadRequest, "e-posta ve şifre zorunludur")
		return
	}

	ctx := r.Context()
	var userID, tenantID, passwordHash, role string
	err := h.pool.QueryRow(ctx, `
		SELECT u.id, u.tenant_id, u.password_hash, u.role
		FROM identity.users u
		WHERE u.email = $1 AND u.is_active = true
	`, req.Email).Scan(&userID, &tenantID, &passwordHash, &role)

	if errors.Is(err, pgx.ErrNoRows) {
			httputil.WriteError(w, http.StatusUnauthorized, "geçersiz e-posta veya şifre")
		return
	}
	if err != nil {
		slog.Error("kullanıcı sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "giriş başarısız")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(req.Password)); err != nil {
			httputil.WriteError(w, http.StatusUnauthorized, "geçersiz e-posta veya şifre")
		return
	}

	token, expiresAt, err := h.jwt.GenerateToken(userID, tenantID, role)
	if err != nil {
		slog.Error("jwt oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "giriş başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, authResponse{
		Token:     token,
		ExpiresAt: expiresAt.Format(time.RFC3339),
		UserID:    userID,
		TenantID:  tenantID,
		Role:      role,
	})
}

// Logout handles POST /v1/auth/logout
// İleride Redis blacklist ile desteklenebilir (HT1).
func (h *Handler) Logout(w http.ResponseWriter, r *http.Request) {
	// TODO(HT1): Token blacklist / Redis
	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "logged_out"})
}


