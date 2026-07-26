package gate

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool dbiface.DB
}

func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

// CheckResult represents a single governance check within a gate evaluation.
type CheckResult struct {
	Name    string `json:"name"`
	Passed  bool   `json:"passed"`
	Details string `json:"details"`
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
	entityID := input.EntityID

	checks := []CheckResult{
		{Name: "Registry Entry", Passed: false, Details: "AI Registry'de kayıtlı değil"},
		{Name: "Risk Assessment", Passed: false, Details: "Risk değerlendirmesi yapılmamış"},
		{Name: "Policy Compliance", Passed: false, Details: "Uygunluk politikası kontrol edilmedi"},
		{Name: "Documentation", Passed: false, Details: "Teknik dokümantasyon kontrol edilmedi"},
		{Name: "Guardrails", Passed: false, Details: "Guardrail kuralı kontrol edilmedi"},
		{Name: "Bias Test", Passed: true, Details: "Bias testi gerekli değil (varsayılan)"},
	}

	// 1. Registry check — entity kayıtlı mı?
	var registryID, entityType, lifecycleState string
	_ = h.pool.QueryRow(r.Context(), `
		SELECT id, entity_type, lifecycle_state FROM registry.entities
		WHERE (id = $1 OR name = $1) AND tenant_id = $2
	`, entityID, tenantID).Scan(&registryID, &entityType, &lifecycleState)
	if registryID != "" {
		checks[0].Passed = true
		checks[0].Details = "AI Registry'de kayıtlı (" + entityType + ", " + lifecycleState + ")"

		// 2. Risk assessment check
		var riskAssessCount int
		_ = h.pool.QueryRow(r.Context(), `
			SELECT COUNT(*) FROM registry.risk_assessments WHERE entity_id = $1 AND tenant_id = $2
		`, registryID, tenantID).Scan(&riskAssessCount)
		if riskAssessCount > 0 {
			checks[1].Passed = true
			checks[1].Details = "Risk değerlendirmesi mevcut (" + strconv.Itoa(riskAssessCount) + " adet)"
		}

		// 3. Documentation check — entity documentation_url dolu mu?
		var docURL string
		_ = h.pool.QueryRow(r.Context(), `
			SELECT COALESCE(documentation_url, '') FROM registry.entities WHERE id = $1
		`, registryID).Scan(&docURL)
		if docURL != "" {
			checks[3].Passed = true
			checks[3].Details = "Teknik dokümantasyon mevcut"
		}
	}

	// 4. Policy compliance check — tenant'ın passed control'ları var mı?
	var passedControls, totalControls, activePacks int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*) FROM policy.packs WHERE tenant_id = $1 AND enabled = true
	`, tenantID).Scan(&activePacks)
	_ = h.pool.QueryRow(r.Context(), `
		SELECT
			COUNT(*)::int,
			COALESCE(SUM(CASE WHEN status = 'passed' THEN 1 ELSE 0 END), 0)::int
		FROM policy.controls WHERE tenant_id = $1
	`, tenantID).Scan(&totalControls, &passedControls)
	if activePacks > 0 && totalControls > 0 {
		checks[2].Passed = passedControls >= totalControls/2
		if checks[2].Passed {
			checks[2].Details = aktifPackStr(activePacks) + " aktif, " + controlPct(passedControls, totalControls)
		} else {
			checks[2].Details = controlPct(passedControls, totalControls) + " — yarıdan az kontrol geçti"
		}
	} else if activePacks > 0 {
		checks[2].Details = aktifPackStr(activePacks) + ", henüz kontrol geçilmemiş"
	}

	// 5. Guardrails check — tenant'ın guardrail kuralı var mı?
	var guardrailCount int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*) FROM guardrail.rules WHERE tenant_id = $1 AND enabled = true
	`, tenantID).Scan(&guardrailCount)
	if guardrailCount > 0 {
		checks[4].Passed = true
		checks[4].Details = guardrailSayisi(guardrailCount) + " aktif"
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

	// Persist check result to gate.checks
	checkDetailsJSON, _ := json.Marshal(checks)
	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO gate.checks (id, tenant_id, entity_id, entity_type, target_env, version,
			decision, passed_checks, total_checks, check_details, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10::jsonb, now())
	`, checkID, tenantID, entityID, input.EntityType, input.TargetEnv, input.Version,
		decision, passed, len(checks), string(checkDetailsJSON))
	if err != nil {
		slog.Warn("gate check persistence hatası", "check_id", checkID, "error", err)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"check_id":    checkID,
		"entity_id":   entityID,
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

	limit := 50

	// LIMIT+1 pattern for has_more
	rows, err := h.pool.Query(r.Context(), `
		SELECT id, entity_id, entity_type, target_env, version, decision, passed_checks, total_checks, created_at
		FROM gate.checks
		WHERE tenant_id = $1 AND ($2 = '' OR entity_id = $2)
		ORDER BY created_at DESC
		LIMIT $3
	`, tenantID, entityID, limit+1)
	if err != nil {
		slog.Warn("gate history sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"history": []interface{}{}, "has_more": false, "total": 0})
		return
	}
	defer rows.Close()

	type historyEntry struct {
		ID         string `json:"id"`
		EntityID   string `json:"entity_id"`
		EntityType string `json:"entity_type"`
		TargetEnv  string `json:"target_env"`
		Version    string `json:"version"`
		Decision   string `json:"decision"`
		Passed     int    `json:"passed_checks"`
		Total      int    `json:"total_checks"`
		CheckedAt  string `json:"checked_at"`
	}

	history := make([]historyEntry, 0)
	for rows.Next() {
		var e historyEntry
		var checkedAt time.Time
		if err := rows.Scan(&e.ID, &e.EntityID, &e.EntityType, &e.TargetEnv, &e.Version, &e.Decision, &e.Passed, &e.Total, &checkedAt); err != nil {
			slog.Warn("gate history satır hatası", "error", err)
			continue
		}
		e.CheckedAt = checkedAt.Format(time.RFC3339)
		history = append(history, e)
	}

	hasMore := len(history) > limit
	if hasMore {
		history = history[:limit]
	}

	if rows.Err() != nil {
		slog.Warn("gate history rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entity_id": entityID,
		"tenant_id": tenantID,
		"history":   history,
		"has_more":  hasMore,
		"total":     len(history),
	})
}

// ---- Yardımcı Fonksiyonlar ----

func aktifPackStr(n int) string {
	if n == 1 {
		return "1 pack"
	}
	return strconv.Itoa(n) + " pack"
}

func guardrailSayisi(n int) string {
	if n == 1 {
		return "1 guardrail"
	}
	return strconv.Itoa(n) + " guardrail"
}

func controlPct(passed, total int) string {
	if total == 0 {
		return "%0 geçti"
	}
	return "%" + strconv.Itoa(passed*100/total) + " geçti"
}
