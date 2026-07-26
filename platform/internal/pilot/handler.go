package pilot

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool *db.Pool
}

func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

type PilotTenant struct {
	ID            string `json:"id"`
	TenantID      string `json:"tenant_id"`
	ProgramName   string `json:"program_name"`
	TrialEndsAt   string `json:"trial_ends_at"`
	MaxWorkspaces int    `json:"max_workspaces"`
	MaxEngines    int    `json:"max_engines"`
	SupportLevel  string `json:"support_level"`
	ContactEmail  string `json:"contact_email"`
	Notes         string `json:"notes"`
	AutoConvert   bool   `json:"auto_convert"`
	Status        string `json:"status"`
	CreatedAt     string `json:"created_at"`
}

// GetStatus returns pilot program status for the current tenant.
func (h *Handler) GetStatus(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var p PilotTenant
	err := h.pool.QueryRow(r.Context(), `
		SELECT pt.id, pt.tenant_id, pt.program_name, pt.trial_ends_at,
			pt.max_workspaces, pt.max_engines, pt.support_level,
			pt.contact_email, pt.notes, pt.auto_convert, pt.status,
			pt.created_at
		FROM identity.tenants t
		LEFT JOIN identity.pilot_tenants pt ON pt.tenant_id = t.id
		WHERE t.id = $1
	`, tenantID).Scan(
		&p.ID, &p.TenantID, &p.ProgramName, &p.TrialEndsAt,
		&p.MaxWorkspaces, &p.MaxEngines, &p.SupportLevel,
		&p.ContactEmail, &p.Notes, &p.AutoConvert, &p.Status,
		&p.CreatedAt,
	)
	if err != nil {
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
			"enrolled": false,
			"message":  "Bu tenant pilot programına kayıtlı değil",
		})
		return
	}

	daysLeft := 0
	if p.TrialEndsAt != "" {
		if endTime, err := time.Parse(time.RFC3339, p.TrialEndsAt); err == nil {
			daysLeft = int(time.Until(endTime).Hours() / 24)
			if daysLeft < 0 {
				daysLeft = 0
			}
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"enrolled":       true,
		"program":        p,
		"days_remaining": daysLeft,
		"features": map[string]interface{}{
			"max_workspaces":             p.MaxWorkspaces,
			"max_engines":                p.MaxEngines,
			"support_level":              p.SupportLevel,
			"premium_support":            p.SupportLevel == "premium",
			"extended_trial":             true,
			"priority_onboarding":        true,
			"dedicated_slack_channel":    p.SupportLevel == "premium",
			"monthly_business_review":    true,
			"early_access_features":      true,
			"custom_integration_support": p.SupportLevel == "premium",
		},
	})
}

// Enroll registers the current tenant for the pilot program.
func (h *Handler) Enroll(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		ProgramName  string `json:"program_name"`
		ContactEmail string `json:"contact_email"`
		Notes        string `json:"notes"`
		SupportLevel string `json:"support_level"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.ProgramName == "" {
		input.ProgramName = "Kurumsal Pilot Programı"
	}
	if input.SupportLevel == "" {
		input.SupportLevel = "standard"
	}
	if input.ContactEmail == "" {
		input.ContactEmail = httpmw.GetUserID(r.Context())
	}

	trialEnd := time.Now().Add(90 * 24 * time.Hour)

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO identity.pilot_tenants (tenant_id, program_name, trial_ends_at,
			max_workspaces, max_engines, support_level, contact_email, notes, auto_convert, status)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		ON CONFLICT (tenant_id) DO UPDATE SET
			program_name = EXCLUDED.program_name,
			support_level = EXCLUDED.support_level,
			contact_email = EXCLUDED.contact_email,
			notes = EXCLUDED.notes,
			status = 'active'
	`, tenantID, input.ProgramName, trialEnd,
		10, // max_workspaces — pilot için standartın üzerinde
		5,  // max_engines — pilot için standartın üzerinde
		input.SupportLevel,
		input.ContactEmail,
		input.Notes,
		true, // auto_convert — pilot bitince otomatik ücretliye geç
		"active",
	)
	if err != nil {
		slog.Error("pilot kayıt hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "pilot programa kayıt başarısız"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"status":        "kayıtlı",
		"program":       input.ProgramName,
		"trial_ends_at": trialEnd.Format(time.RFC3339),
		"support_level": input.SupportLevel,
		"auto_convert":  true,
	})
}

// ExtendTrial extends the pilot trial period.
func (h *Handler) ExtendTrial(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		ExtraDays int `json:"extra_days"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.ExtraDays < 1 || input.ExtraDays > 365 {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "ek süre 1-365 gün arasında olmalıdır"})
		return
	}

	_, err := h.pool.Exec(r.Context(), `
		UPDATE identity.pilot_tenants
		SET trial_ends_at = GREATEST(trial_ends_at, now()) + ($1 || ' days')::INTERVAL,
			updated_at = now()
		WHERE tenant_id = $2
	`, input.ExtraDays, tenantID)
	if err != nil {
		slog.Error("pilot süre uzatma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "süre uzatılamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "pilot süresi uzatıldı"})
}

// Cancel cancels the pilot program enrollment.
func (h *Handler) Cancel(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	_, err := h.pool.Exec(r.Context(), `
		UPDATE identity.pilot_tenants SET status = 'cancelled', auto_convert = false, updated_at = now()
		WHERE tenant_id = $1
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "pilot iptal edilemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "pilot iptal edildi"})
}

// ListAll returns all pilot tenants (admin only).
func (h *Handler) ListAll(w http.ResponseWriter, r *http.Request) {
	rows, err := h.pool.Query(r.Context(), `
		SELECT pt.id, pt.tenant_id, t.name, pt.program_name, pt.trial_ends_at,
			pt.support_level, pt.status, pt.created_at
		FROM identity.pilot_tenants pt
		JOIN identity.tenants t ON t.id = pt.tenant_id
		ORDER BY pt.created_at DESC
	`)
	if err != nil {
		slog.Error("pilot listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"pilots": []interface{}{}})
		return
	}
	defer rows.Close()

	type PilotRow struct {
		ID           string `json:"id"`
		TenantID     string `json:"tenant_id"`
		TenantName   string `json:"tenant_name"`
		ProgramName  string `json:"program_name"`
		TrialEndsAt  string `json:"trial_ends_at"`
		SupportLevel string `json:"support_level"`
		Status       string `json:"status"`
		CreatedAt    string `json:"created_at"`
	}

	var pilots []PilotRow
	for rows.Next() {
		var pr PilotRow
		if err := rows.Scan(&pr.ID, &pr.TenantID, &pr.TenantName, &pr.ProgramName,
			&pr.TrialEndsAt, &pr.SupportLevel, &pr.Status, &pr.CreatedAt); err != nil {
			slog.Error("pilot okuma hatası", "error", err)
			continue
		}
		pilots = append(pilots, pr)
	}

	if rows.Err() != nil {
		slog.Error("pilot rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"pilots": pilots})
}
