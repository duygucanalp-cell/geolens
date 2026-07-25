package privacy

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
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
	pool *db.Pool
}

// NewHandler creates a new privacy handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
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

// RequestDeletion handles POST /v1/account/deletion
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
		defer tx.Rollback(ctx)

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
	_, _ = h.pool.Exec(ctx, `
		INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata)
		VALUES (gen_random_uuid()::text, $1, $2, 'privacy.deletion_requested', 'tenant', $3, 'request',
		        jsonb_build_object('reason', $4, 'status', 'pending'))
	`, tenantID, userID, tenantID, req.Reason)

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
		defer tx.Rollback(ctx)

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
