package alert

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for alert rule HTTP handlers.
type Handler struct {
	pool dbiface.DB
}

// NewHandler creates a new alert Handler with the given DB interface.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new alert Handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

// List handles GET /v1/workspaces/{ws}/alert-rules
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT ar.id, ar.brand_id, ar.name, ar.metric, ar.condition, ar.threshold,
			ar.channel, ar.channel_config, ar.enabled, ar.cooldown_min, ar.last_fired_at, ar.created_at, ar.updated_at
		FROM governance.alert_rules ar
		JOIN config.brands b ON b.id = ar.brand_id
		WHERE ar.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR ar.brand_id = $3)
		ORDER BY ar.created_at DESC
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Error("alert rule sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"rules": []interface{}{}})
		return
	}
	defer rows.Close()

	type ruleRow struct {
		ID          string     `json:"id"`
		BrandID     string     `json:"brand_id"`
		Name        string     `json:"name"`
		Metric      string     `json:"metric"`
		Condition   string     `json:"condition"`
		Threshold   float64    `json:"threshold"`
		Channel     string     `json:"channel"`
		ChannelCfg  *string    `json:"channel_config,omitempty"`
		Enabled     bool       `json:"enabled"`
		CooldownMin int        `json:"cooldown_min"`
		LastFiredAt *time.Time `json:"last_fired_at,omitempty"`
		CreatedAt   time.Time  `json:"created_at"`
		UpdatedAt   time.Time  `json:"updated_at"`
	}

	rules := make([]ruleRow, 0)
	for rows.Next() {
		var r ruleRow
		var channelCfg *string
		if err := rows.Scan(&r.ID, &r.BrandID, &r.Name, &r.Metric, &r.Condition,
			&r.Threshold, &r.Channel, &channelCfg, &r.Enabled, &r.CooldownMin,
			&r.LastFiredAt, &r.CreatedAt, &r.UpdatedAt); err != nil {
			slog.Warn("alert rule satır okuma hatası", "error", err)
			continue
		}
		if channelCfg != nil {
			r.ChannelCfg = channelCfg
		}
		rules = append(rules, r)
	}

	if rows.Err() != nil {
		slog.Warn("alert rules rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"rules": rules})
}

// Create handles POST /v1/workspaces/{ws}/alert-rules
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())

	var req struct {
		BrandID     string  `json:"brand_id"`
		Name        string  `json:"name"`
		Metric      string  `json:"metric"`
		Condition   string  `json:"condition"`
		Threshold   float64 `json:"threshold"`
		Channel     string  `json:"channel"`
		ChannelCfg  *string `json:"channel_config,omitempty"`
		CooldownMin int     `json:"cooldown_min"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.BrandID == "" || req.Name == "" || req.Metric == "" || req.Condition == "" {
		httputil.WriteError(w, http.StatusBadRequest, "marka, ad, metrik ve koşul zorunludur")
		return
	}
	if req.Channel == "" {
		req.Channel = "email"
	}
	if req.CooldownMin == 0 {
		req.CooldownMin = 60
	}

	var brandExists bool
	err := h.pool.QueryRow(r.Context(), `
		SELECT EXISTS(SELECT 1 FROM config.brands WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3)
	`, req.BrandID, workspaceID, tenantID).Scan(&brandExists)
	if err != nil || !brandExists {
		httputil.WriteError(w, http.StatusNotFound, "marka bulunamadı")
		return
	}

	var ruleID string
	err = h.pool.QueryRow(r.Context(), `
		INSERT INTO governance.alert_rules
			(id, tenant_id, brand_id, name, metric, condition, threshold, channel, channel_config, cooldown_min)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, $5, $6, $7, $8, $9)
		RETURNING id
	`, tenantID, req.BrandID, req.Name, req.Metric, req.Condition, req.Threshold,
		req.Channel, req.ChannelCfg, req.CooldownMin).Scan(&ruleID)
	if err != nil {
		slog.Error("alert rule oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kural oluşturulamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]string{"id": ruleID, "status": "created"})
}

// Update handles PUT /v1/workspaces/{ws}/alert-rules/{ruleId}
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	ruleID := chi.URLParam(r, "ruleId")

	var req struct {
		Name        *string  `json:"name,omitempty"`
		Metric      *string  `json:"metric,omitempty"`
		Condition   *string  `json:"condition,omitempty"`
		Threshold   *float64 `json:"threshold,omitempty"`
		Channel     *string  `json:"channel,omitempty"`
		ChannelCfg  *string  `json:"channel_config,omitempty"`
		Enabled     *bool    `json:"enabled,omitempty"`
		CooldownMin *int     `json:"cooldown_min,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}

	result, err := h.pool.Exec(r.Context(), `
		UPDATE governance.alert_rules
		SET name           = COALESCE($2, name),
		    metric         = COALESCE($3, metric),
		    condition      = COALESCE($4, condition),
		    threshold      = COALESCE($5, threshold),
		    channel        = COALESCE($6, channel),
		    channel_config = COALESCE($7, channel_config),
		    enabled        = COALESCE($8, enabled),
		    cooldown_min   = COALESCE($9, cooldown_min),
		    updated_at     = now()
		WHERE id = $1 AND tenant_id = $10
	`, ruleID, req.Name, req.Metric, req.Condition, req.Threshold,
		req.Channel, req.ChannelCfg, req.Enabled, req.CooldownMin, tenantID)
	if err != nil {
		slog.Error("alert rule güncelleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kural güncellenemedi")
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteError(w, http.StatusNotFound, "kural bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "updated"})
}

// Delete handles DELETE /v1/workspaces/{ws}/alert-rules/{ruleId}
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	ruleID := chi.URLParam(r, "ruleId")

	result, err := h.pool.Exec(r.Context(), `
		DELETE FROM governance.alert_rules WHERE id = $1 AND tenant_id = $2
	`, ruleID, tenantID)
	if err != nil {
		slog.Error("alert rule silme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "kural silinemedi")
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteError(w, http.StatusNotFound, "kural bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
}
