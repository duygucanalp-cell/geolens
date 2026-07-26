package registry

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

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

type Entity struct {
	ID               string  `json:"id"`
	TenantID         string  `json:"tenant_id"`
	EntityType       string  `json:"entity_type"`
	Name             string  `json:"name"`
	Description      string  `json:"description"`
	Version          string  `json:"version"`
	Provider         string  `json:"provider"`
	LifecycleState   string  `json:"lifecycle_state"`
	RiskClass        string  `json:"risk_class"`
	Owner            string  `json:"owner"`
	DocumentationURL string  `json:"documentation_url"`
	DeployedAt       *string `json:"deployed_at,omitempty"`
	CreatedAt        string  `json:"created_at"`
	UpdatedAt        string  `json:"updated_at"`
}

func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityType := r.URL.Query().Get("entity_type")
	lifecycle := r.URL.Query().Get("lifecycle_state")
	risk := r.URL.Query().Get("risk_class")

	query := `SELECT id, tenant_id, entity_type, name, description, version, provider,
		lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
		FROM registry.entities WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	argIdx := 2

	if entityType != "" {
		query += ` AND entity_type = $` + string(rune('0'+argIdx))
		args = append(args, entityType)
		argIdx++
	}
	if lifecycle != "" {
		query += ` AND lifecycle_state = $` + string(rune('0'+argIdx))
		args = append(args, lifecycle)
		argIdx++
	}
	if risk != "" {
		query += ` AND risk_class = $` + string(rune('0'+argIdx))
		args = append(args, risk)
	}
	query += ` ORDER BY created_at DESC`

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Error("registry sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"entities": []interface{}{}})
		return
	}
	defer rows.Close()

	var entities []Entity
	for rows.Next() {
		var e Entity
		if err := rows.Scan(&e.ID, &e.TenantID, &e.EntityType, &e.Name, &e.Description,
			&e.Version, &e.Provider, &e.LifecycleState, &e.RiskClass, &e.Owner,
			&e.DocumentationURL, &e.DeployedAt, &e.CreatedAt, &e.UpdatedAt); err != nil {
			slog.Error("registry okuma hatası", "error", err)
			continue
		}
		entities = append(entities, e)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"entities": entities})
}

func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	var e Entity
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, tenant_id, entity_type, name, description, version, provider,
			lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
		FROM registry.entities WHERE id = $1 AND tenant_id = $2
	`, entityID, tenantID).Scan(
		&e.ID, &e.TenantID, &e.EntityType, &e.Name, &e.Description,
		&e.Version, &e.Provider, &e.LifecycleState, &e.RiskClass, &e.Owner,
		&e.DocumentationURL, &e.DeployedAt, &e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "varlık bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, e)
}

func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		EntityType       string `json:"entity_type"`
		Name             string `json:"name"`
		Description      string `json:"description"`
		Version          string `json:"version"`
		Provider         string `json:"provider"`
		LifecycleState   string `json:"lifecycle_state"`
		RiskClass        string `json:"risk_class"`
		Owner            string `json:"owner"`
		DocumentationURL string `json:"documentation_url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	validTypes := map[string]bool{"model": true, "agent": true, "application": true, "dataset": true}
	if !validTypes[input.EntityType] {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz entity_type: model, agent, application, dataset"})
		return
	}

	if input.LifecycleState == "" {
		input.LifecycleState = "development"
	}
	if input.RiskClass == "" {
		input.RiskClass = "medium"
	}
	if input.Version == "" {
		input.Version = "1.0.0"
	}

	var e Entity
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO registry.entities (tenant_id, entity_type, name, description, version, provider,
			lifecycle_state, risk_class, owner, documentation_url)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		RETURNING id, tenant_id, entity_type, name, description, version, provider,
			lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
	`, tenantID, input.EntityType, input.Name, input.Description, input.Version,
		input.Provider, input.LifecycleState, input.RiskClass, input.Owner, input.DocumentationURL,
	).Scan(&e.ID, &e.TenantID, &e.EntityType, &e.Name, &e.Description,
		&e.Version, &e.Provider, &e.LifecycleState, &e.RiskClass, &e.Owner,
		&e.DocumentationURL, &e.DeployedAt, &e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		slog.Error("registry kayıt hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "varlık kaydedilemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, e)
}

func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	var input struct {
		Name             string `json:"name"`
		Description      string `json:"description"`
		Version          string `json:"version"`
		Provider         string `json:"provider"`
		LifecycleState   string `json:"lifecycle_state"`
		RiskClass        string `json:"risk_class"`
		Owner            string `json:"owner"`
		DocumentationURL string `json:"documentation_url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	var e Entity
	err := h.pool.QueryRow(r.Context(), `
		UPDATE registry.entities SET
			name = COALESCE(NULLIF($3, ''), name),
			description = COALESCE(NULLIF($4, ''), description),
			version = COALESCE(NULLIF($5, ''), version),
			provider = COALESCE(NULLIF($6, ''), provider),
			lifecycle_state = COALESCE(NULLIF($7, ''), lifecycle_state),
			risk_class = COALESCE(NULLIF($8, ''), risk_class),
			owner = COALESCE(NULLIF($9, ''), owner),
			documentation_url = COALESCE(NULLIF($10, ''), documentation_url),
			updated_at = now()
		WHERE id = $1 AND tenant_id = $2
		RETURNING id, tenant_id, entity_type, name, description, version, provider,
			lifecycle_state, risk_class, owner, documentation_url, deployed_at, created_at, updated_at
	`, entityID, tenantID, input.Name, input.Description, input.Version,
		input.Provider, input.LifecycleState, input.RiskClass, input.Owner, input.DocumentationURL,
	).Scan(&e.ID, &e.TenantID, &e.EntityType, &e.Name, &e.Description,
		&e.Version, &e.Provider, &e.LifecycleState, &e.RiskClass, &e.Owner,
		&e.DocumentationURL, &e.DeployedAt, &e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "varlık bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, e)
}

func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	result, err := h.pool.Exec(r.Context(), `
		DELETE FROM registry.entities WHERE id = $1 AND tenant_id = $2
	`, entityID, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "silme hatası"})
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "varlık bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "silindi"})
}

func (h *Handler) AssessRisk(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	var input struct {
		RiskClass string  `json:"risk_class"`
		Score     float64 `json:"score"`
		Summary   string  `json:"summary"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	var result struct {
		ID string `json:"id"`
	}
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO registry.risk_assessments (entity_id, tenant_id, risk_class, score, summary, assessed_by)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING id
	`, entityID, tenantID, input.RiskClass, input.Score, input.Summary, httpmw.GetUserID(r.Context()),
	).Scan(&result.ID)
	if err != nil {
		slog.Error("risk değerlendirme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "değerlendirme kaydedilemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"id":     result.ID,
		"status": "değerlendirildi",
	})
}
