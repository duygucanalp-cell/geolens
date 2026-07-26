package apikey

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for API key management.
type Handler struct {
	pool dbiface.DB
}

// NewHandler creates a new API key Handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: dbiface.NewAdapter(pool)}
}

// List handles GET /v1/api-keys
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, key_prefix, role, is_active, last_used_at, expires_at, created_at
		FROM identity.api_keys
		WHERE tenant_id = $1
		ORDER BY created_at DESC
	`, tenantID)
	if err != nil {
		slog.Error("api key listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"keys": []interface{}{}})
		return
	}
	defer rows.Close()

	type keyRow struct {
		ID         string     `json:"id"`
		Name       string     `json:"name"`
		KeyPrefix  string     `json:"key_prefix"`
		Role       string     `json:"role"`
		IsActive   bool       `json:"is_active"`
		LastUsedAt *time.Time `json:"last_used_at,omitempty"`
		ExpiresAt  *time.Time `json:"expires_at,omitempty"`
		CreatedAt  time.Time  `json:"created_at"`
	}

	keys := make([]keyRow, 0)
	for rows.Next() {
		var k keyRow
		if err := rows.Scan(&k.ID, &k.Name, &k.KeyPrefix, &k.Role, &k.IsActive, &k.LastUsedAt, &k.ExpiresAt, &k.CreatedAt); err != nil {
			slog.Warn("api key satır okuma hatası", "error", err)
			continue
		}
		keys = append(keys, k)
	}

	if rows.Err() != nil {
		slog.Warn("api key rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"keys": keys})
}

// Create handles POST /v1/api-keys
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		Name      string     `json:"name"`
		Role      string     `json:"role"`
		ExpiresAt *time.Time `json:"expires_at,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.Name == "" {
		httputil.WriteError(w, http.StatusBadRequest, "isim zorunludur")
		return
	}
	if req.Role == "" {
		req.Role = "viewer"
	}
	validRoles := map[string]bool{"viewer": true}
	if !validRoles[req.Role] {
		httputil.WriteError(w, http.StatusBadRequest, "rol yalnızca viewer olabilir")
		return
	}

	// API anahtarı oluştur: gls_{random}
	rawBytes := make([]byte, 24)
	if _, err := rand.Read(rawBytes); err != nil {
		slog.Error("api key random oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "anahtar oluşturulamadı")
		return
	}
	rawKey := "gls_" + hex.EncodeToString(rawBytes)
	keyPrefix := rawKey[:12] // gls_ + ilk 8 hex

	hash, err := bcrypt.GenerateFromPassword([]byte(rawKey), bcrypt.DefaultCost)
	if err != nil {
		slog.Error("api key hash hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "anahtar oluşturulamadı")
		return
	}

	var id string
	err = h.pool.QueryRow(r.Context(), `
		INSERT INTO identity.api_keys (id, tenant_id, name, key_hash, key_prefix, role, expires_at)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, $5, $6)
		RETURNING id
	`, tenantID, req.Name, string(hash), keyPrefix, req.Role, req.ExpiresAt).Scan(&id)
	if err != nil {
		slog.Error("api key kaydetme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "anahtar oluşturulamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]string{
		"id":         id,
		"api_key":    rawKey,
		"key_prefix": keyPrefix,
		"warning":    "anahtar yalnızca bir kez gösterilir; kopyalayın",
	})
}

// Delete handles DELETE /v1/api-keys/{keyId}
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	keyID := chi.URLParam(r, "keyId")

	result, err := h.pool.Exec(r.Context(), `
		DELETE FROM identity.api_keys WHERE id = $1 AND tenant_id = $2
	`, keyID, tenantID)
	if err != nil {
		slog.Error("api key silme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "anahtar silinemedi")
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteError(w, http.StatusNotFound, "anahtar bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
}
