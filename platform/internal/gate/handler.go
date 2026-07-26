package gate

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/id"
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

func (h *Handler) Check(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		EntityID   string `json:"entity_id"`
		EntityType string `json:"entity_type"`
		TargetEnv  string `json:"target_environment"`
		Version    string `json:"version"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	checkID := id.New()

	checks := []struct {
		Name    string
		Passed  bool
		Details string
	}{
		{"Risk Assessment", false, "Risk değerlendirmesi yapılmamış"},
		{"Policy Compliance", false, "Uygunluk politikası kontrol edilmedi"},
		{"Bias Test", true, "Bias testi gerekli değil (varsayılan)"},
		{"Documentation", false, "Teknik dokümantasyon eksik"},
		{"Registry Entry", false, "AI Registry'de kayıtlı değil"},
	}

	// Check if entity exists in registry
	var registryCount int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*) FROM registry.entities WHERE id = $1 AND tenant_id = $2
	`, input.EntityID, tenantID).Scan(&registryCount)
	if registryCount > 0 {
		checks[4].Passed = true
		checks[4].Details = "AI Registry'de kayıtlı"

		var riskAssessCount int
		_ = h.pool.QueryRow(r.Context(), `
			SELECT COUNT(*) FROM registry.risk_assessments WHERE entity_id = $1 AND tenant_id = $2
		`, input.EntityID, tenantID).Scan(&riskAssessCount)
		if riskAssessCount > 0 {
			checks[0].Passed = true
			checks[0].Details = "Risk değerlendirmesi mevcut"
		}
	}

	// Check policy compliance
	var passedControls int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*) FROM policy.controls WHERE tenant_id = $1 AND status = 'passed'
	`, tenantID).Scan(&passedControls)
	if passedControls > 0 {
		checks[1].Passed = true
		checks[1].Details = "Politika kontrolleri mevcut"
	}

	passed := 0
	for _, c := range checks {
		if c.Passed {
			passed++
		}
	}
	allPassed := passed == len(checks)

	decision := "blocked"
	if allPassed {
		decision = "approved"
	} else if float64(passed)/float64(len(checks)) >= 0.5 {
		decision = "flagged"
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"check_id":    checkID,
		"entity_id":   input.EntityID,
		"entity_type": input.EntityType,
		"target_env":  input.TargetEnv,
		"decision":    decision,
		"passed":      passed,
		"total":       len(checks),
		"checks":      checks,
		"checked_at":  time.Now().Format(time.RFC3339),
	})
}

func (h *Handler) History(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entity_id": entityID,
		"tenant_id": tenantID,
		"history":   []interface{}{},
		"total":     0,
	})
}
