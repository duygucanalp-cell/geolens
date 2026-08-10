// Package privacy provides handlers and logic for privacy functionality.
package privacy

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// ---- Request/Response Types ----

type deletionRequest struct {
	Reason string `json:"reason"`
}

type deletionResponse struct {
	ID      string `json:"id"`
	Status  string `json:"status"`
	Message string `json:"message"`
}

type processRequest struct {
	Action string `json:"action"` // "approve" or "reject"
	Notes  string `json:"notes"`
}

// Handler holds dependencies for privacy/KVKK HTTP handlers.
type Handler struct {
	pool    dbiface.DB
	rawPool *db.Pool
}

// NewHandler creates a new privacy handler with the given DB interface.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new privacy handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool:    dbiface.NewAdapter(pool),
		rawPool: pool,
	}
}

// userRoleFromDB looks up the user's role from the membership table directly.
// DB sorgusu ile rolü bulur, httpmw middleware context'ine bağımlı değildir.
func (h *Handler) userRoleFromDB(ctx context.Context) string {
	userID := httpmw.GetUserID(ctx)
	tenantID := httpmw.GetTenantID(ctx)
	if userID == "" || tenantID == "" {
		return ""
	}

	var role string
	err := h.pool.QueryRow(ctx, `
		SELECT m.role FROM config.memberships m
		WHERE m.user_id = $1 AND m.tenant_id = $2
		ORDER BY m.created_at LIMIT 1
	`, userID, tenantID).Scan(&role)
	if err != nil {
		return ""
	}
	return role
}

// ExportData handles GET /v1/account/data
// GDPR veri taşınabilirliği: kiracıya ait kişisel verileri makine-okunur (JSON) olarak dışa aktarır.
// KVKK sağ unutulma (RequestDeletion) ile birlikte GDPR veri taşınabilirliği hakkını kapatır.
func (h *Handler) ExportData(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	tenantID := httpmw.GetTenantID(ctx)
	if tenantID == "" {
		httputil.WriteError(w, http.StatusUnauthorized, "kimlik doğrulama gerekli")
		return
	}

	payload := map[string]interface{}{
		"tenant_id":          tenantID,
		"exported_at":        time.Now().UTC().Format(time.RFC3339),
		"format_version":     1,
		"users":              []interface{}{},
		"memberships":        []interface{}{},
		"brands":             []interface{}{},
		"prompt_sets":        []interface{}{},
		"measurement_scores": []interface{}{},
	}

	// Kullanıcılar (kişisel veri)
	if rows, err := h.pool.Query(ctx, `
		SELECT u.id, u.email, u.full_name, u.created_at
		FROM identity.users u
		JOIN identity.user_tenants ut ON ut.user_id = u.id
		WHERE ut.tenant_id = $1
		ORDER BY u.created_at
	`, tenantID); err == nil {
		for rows.Next() {
			var id, email, name string
			var createdAt interface{}
			if err := rows.Scan(&id, &email, &name, &createdAt); err == nil {
				payload["users"] = append(payload["users"].([]interface{}), map[string]interface{}{
					"id": id, "email": email, "full_name": name, "created_at": fmt.Sprint(createdAt),
				})
			}
		}
		rows.Close()
	}

	// Üyelikler
	if rows, err := h.pool.Query(ctx, `
		SELECT user_id, role, created_at FROM config.memberships
		WHERE tenant_id = $1 ORDER BY created_at
	`, tenantID); err == nil {
		for rows.Next() {
			var userID, role string
			var createdAt interface{}
			if err := rows.Scan(&userID, &role, &createdAt); err == nil {
				payload["memberships"] = append(payload["memberships"].([]interface{}), map[string]interface{}{
					"user_id": userID, "role": role, "created_at": fmt.Sprint(createdAt),
				})
			}
		}
		rows.Close()
	}

	// Markalar
	if rows, err := h.pool.Query(ctx, `
		SELECT id, workspace_id, name, website_url, created_at
		FROM config.brands WHERE tenant_id = $1 AND is_active = true
		ORDER BY created_at
	`, tenantID); err == nil {
		for rows.Next() {
			var id, wsID, name, url string
			var createdAt interface{}
			if err := rows.Scan(&id, &wsID, &name, &url, &createdAt); err == nil {
				payload["brands"] = append(payload["brands"].([]interface{}), map[string]interface{}{
					"id": id, "workspace_id": wsID, "name": name, "website_url": url, "created_at": fmt.Sprint(createdAt),
				})
			}
		}
		rows.Close()
	}

	// Prompt setleri
	if rows, err := h.pool.Query(ctx, `
		SELECT id, name, category, created_at FROM config.prompt_sets
		WHERE tenant_id = $1 ORDER BY created_at
	`, tenantID); err == nil {
		for rows.Next() {
			var id, name, category string
			var createdAt interface{}
			if err := rows.Scan(&id, &name, &category, &createdAt); err == nil {
				payload["prompt_sets"] = append(payload["prompt_sets"].([]interface{}), map[string]interface{}{
					"id": id, "name": name, "category": category, "created_at": fmt.Sprint(createdAt),
				})
			}
		}
		rows.Close()
	}

	// Ölçüm skorları
	if rows, err := h.pool.Query(ctx, `
		SELECT brand_id, workspace_id, value, engine_name, freshness_at
		FROM measure.scores WHERE tenant_id = $1
		ORDER BY freshness_at DESC LIMIT 1000
	`, tenantID); err == nil {
		for rows.Next() {
			var brandID, wsID, engineName string
			var value float64
			var freshnessAt interface{}
			if err := rows.Scan(&brandID, &wsID, &value, &engineName, &freshnessAt); err == nil {
				payload["measurement_scores"] = append(payload["measurement_scores"].([]interface{}), map[string]interface{}{
					"brand_id": brandID, "workspace_id": wsID, "value": value,
					"engine_name": engineName, "freshness_at": fmt.Sprint(freshnessAt),
				})
			}
		}
		rows.Close()
	}

	// GDPR audit kaydı: veri dışa aktarımı loglanır
	if _, err := h.pool.Exec(ctx, `
		INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata)
		VALUES (gen_random_uuid()::text, $1, $2, 'privacy.data_exported', 'tenant', $3, 'export',
		        jsonb_build_object('format', 'json'))
	`, tenantID, httpmw.GetUserID(ctx), tenantID); err != nil {
		slog.Warn("GDPR dışa aktarım audit log kaydı başarısız", "error", err)
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Content-Disposition", `attachment; filename="geolens-data-export.json"`)
	httputil.WriteJSON(w, http.StatusOK, payload)
}
// KVKK kapsamında kullanıcının veri silme talebi.
// Admin: doğrudan anonimleştirme yapar.
// Editor/Viewer: talep oluşturur (admin onayı gerekir).
func (h *Handler) RequestDeletion(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	userID := httpmw.GetUserID(ctx)
	tenantID := httpmw.GetTenantID(ctx)

	if userID == "" || tenantID == "" {
		httputil.WriteError(w, http.StatusUnauthorized, "kimlik doğrulama gerekli")
		return
	}

	var req deletionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	// Veritabanından admin rolünü kontrol et
	role := h.userRoleFromDB(ctx)

	var requestID string
	if role == "admin" {
		// Admin: doğrudan anonimleştir
		tx, err := h.pool.Begin(ctx)
		if err != nil {
			slog.Error("transaction başlatma hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "silme işlemi başarısız")
			return
		}
		defer func() { _ = tx.Rollback(ctx) }()

		err = tx.QueryRow(ctx, `
			INSERT INTO privacy.deletion_requests (id, tenant_id, requested_by, status, reason, processed_at, processed_by)
			VALUES (gen_random_uuid()::text, $1, $2, 'processing', $3, now(), $2)
			RETURNING id
		`, tenantID, userID, req.Reason).Scan(&requestID)
		if err != nil {
			slog.Error("silme talebi kayıt hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "silme işlemi başarısız")
			return
		}

		_, err = tx.Exec(ctx, `SELECT privacy.anonymize_tenant($1)`, tenantID)
		if err != nil {
			slog.Error("anonimleştirme hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "silme işlemi başarısız")
			return
		}

		_, err = tx.Exec(ctx, `
			UPDATE privacy.deletion_requests
			SET status = 'completed', notes = 'KVKK kapsamında anonimleştirildi'
			WHERE id = $1
		`, requestID)
		if err != nil {
			slog.Warn("talep güncelleme hatası", "error", err)
		}

		if err := tx.Commit(ctx); err != nil {
			slog.Error("transaction commit hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "silme işlemi başarısız")
			return
		}

		httputil.WriteJSON(w, http.StatusOK, deletionResponse{
			ID:      requestID,
			Status:  "completed",
			Message: "Hesabınız ve tüm kişisel verileriniz başarıyla anonimleştirildi.",
		})
		return
	}

	// Editor/viewer: talep oluştur
	err := h.pool.QueryRow(ctx, `
		INSERT INTO privacy.deletion_requests (id, tenant_id, requested_by, status, reason)
		VALUES (gen_random_uuid()::text, $1, $2, 'pending', $3)
		RETURNING id
	`, tenantID, userID, req.Reason).Scan(&requestID)
	if err != nil {
		slog.Error("silme talebi kayıt hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "talep oluşturulamadı")
		return
	}

	// Audit log
	if _, err := h.pool.Exec(ctx, `
		INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata)
		VALUES (gen_random_uuid()::text, $1, $2, 'privacy.deletion_requested', 'tenant', $3, 'request',
		        jsonb_build_object('reason', $4, 'status', 'pending'))
	`, tenantID, userID, tenantID, req.Reason); err != nil {
		slog.Warn("KVKK silme audit log kaydı başarısız", "error", err)
	}

	httputil.WriteJSON(w, http.StatusAccepted, deletionResponse{
		ID:      requestID,
		Status:  "pending",
		Message: "Veri silme talebiniz alındı. Admin kullanıcı talebinizi değerlendirecektir.",
	})
}

// ListDeletionRequests handles GET /v1/deletion-requests
// Admin kullanıcılar için bekleyen silme taleplerini listeler.
func (h *Handler) ListDeletionRequests(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	tenantID := httpmw.GetTenantID(ctx)
	role := h.userRoleFromDB(ctx)

	if role != "admin" {
		httputil.WriteError(w, http.StatusForbidden, "bu işlem için admin yetkisi gerekli")
		return
	}

	rows, err := h.pool.Query(ctx, `
		SELECT id, requested_by, status, COALESCE(reason, ''), requested_at, 
		       COALESCE(processed_at, '1970-01-01'::timestamptz), COALESCE(notes, '')
		FROM privacy.deletion_requests
		WHERE tenant_id = $1
		ORDER BY requested_at DESC
		LIMIT 50
	`, tenantID)
	if err != nil {
		slog.Error("silme talepleri sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "talepler listelenemedi")
		return
	}
	defer rows.Close()

	type requestRow struct {
		ID          string `json:"id"`
		RequestedBy string `json:"requested_by"`
		Status      string `json:"status"`
		Reason      string `json:"reason"`
		RequestedAt string `json:"requested_at"`
		ProcessedAt string `json:"processed_at,omitempty"`
		Notes       string `json:"notes,omitempty"`
	}

	requests := make([]requestRow, 0)
	for rows.Next() {
		var r requestRow
		var reqAt, procAt interface{}

		if err := rows.Scan(&r.ID, &r.RequestedBy, &r.Status, &r.Reason, &reqAt, &procAt, &r.Notes); err != nil {
			slog.Warn("satır okuma hatası", "error", err)
			continue
		}

		if t, ok := reqAt.(interface{ String() string }); ok {
			r.RequestedAt = t.String()
		}
		if t, ok := procAt.(interface{ String() string }); ok && procAt != nil {
			r.ProcessedAt = t.String()
		}

		requests = append(requests, r)
	}

	if rows.Err() != nil {
		slog.Warn("silme talepleri rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"requests": requests,
	})
}

// ProcessDeletionRequest handles POST /v1/deletion-requests/{id}/process
// Admin kullanıcının silme talebini onaylaması/reddetmesi.
func (h *Handler) ProcessDeletionRequest(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	tenantID := httpmw.GetTenantID(ctx)
	requestID := chi.URLParam(r, "id")
	role := h.userRoleFromDB(ctx)

	if role != "admin" {
		httputil.WriteError(w, http.StatusForbidden, "bu işlem için admin yetkisi gerekli")
		return
	}

	var req processRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	if req.Action != "approve" && req.Action != "reject" {
		httputil.WriteError(w, http.StatusBadRequest, "action 'approve' veya 'reject' olmalıdır")
		return
	}

	if req.Action == "approve" {
		// Talebi onayla ve anonimleştir
		tx, err := h.pool.Begin(ctx)
		if err != nil {
			slog.Error("transaction hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "işlem başarısız")
			return
		}
		defer func() { _ = tx.Rollback(ctx) }()

		err = tx.QueryRow(ctx, `
			UPDATE privacy.deletion_requests
			SET status = 'processing', processed_at = now(), notes = COALESCE($3, notes)
			WHERE id = $1 AND tenant_id = $2 AND status = 'pending'
			RETURNING id
		`, requestID, tenantID, req.Notes).Scan(&requestID)
		if err != nil {
			httputil.WriteError(w, http.StatusNotFound, "talep bulunamadı veya zaten işlenmiş")
			return
		}

		_, err = tx.Exec(ctx, `SELECT privacy.anonymize_tenant($1)`, tenantID)
		if err != nil {
			slog.Error("anonimleştirme hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "anonimleştirme başarısız")
			return
		}

		_, err = tx.Exec(ctx, `
			UPDATE privacy.deletion_requests SET status = 'completed'
			WHERE id = $1
		`, requestID)
		if err != nil {
			slog.Warn("talep güncelleme hatası", "error", err)
		}

		if err := tx.Commit(ctx); err != nil {
			slog.Error("commit hatası", "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "işlem başarısız")
			return
		}

		httputil.WriteJSON(w, http.StatusOK, deletionResponse{
			ID:      requestID,
			Status:  "completed",
			Message: "Talep onaylandı ve veriler anonimleştirildi.",
		})
		return
	}

	// Reject
	_, err := h.pool.Exec(ctx, `
		UPDATE privacy.deletion_requests
		SET status = 'rejected', processed_at = now(), notes = COALESCE($3, notes)
		WHERE id = $1 AND tenant_id = $2
	`, requestID, tenantID, req.Notes)
	if err != nil {
		slog.Error("talep reddetme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "işlem başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, deletionResponse{
		ID:      requestID,
		Status:  "rejected",
		Message: "Talep reddedildi.",
	})
}
