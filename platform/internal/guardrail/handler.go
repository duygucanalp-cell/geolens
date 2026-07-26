package guardrail

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

type guardRule struct {
	ID       string
	Name     string
	Category string
	Pattern  string
	Action   string
	Severity string
}

func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

type Rule struct {
	ID        string `json:"id"`
	TenantID  string `json:"tenant_id"`
	Name      string `json:"name"`
	Category  string `json:"category"`
	Pattern   string `json:"pattern"`
	Action    string `json:"action"`
	Severity  string `json:"severity"`
	Enabled   bool   `json:"enabled"`
	CreatedAt string `json:"created_at"`
	UpdatedAt string `json:"updated_at"`
}

func (h *Handler) ListRules(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
		FROM guardrail.rules WHERE tenant_id = $1 ORDER BY category, name
	`, tenantID)
	if err != nil {
		slog.Error("guardrail sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"rules": []interface{}{}})
		return
	}
	defer rows.Close()

	var rules []Rule
	for rows.Next() {
		var r Rule
		if err := rows.Scan(&r.ID, &r.TenantID, &r.Name, &r.Category, &r.Pattern,
			&r.Action, &r.Severity, &r.Enabled, &r.CreatedAt, &r.UpdatedAt); err != nil {
			continue
		}
		rules = append(rules, r)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"rules": rules})
}

func (h *Handler) CreateRule(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		Name     string `json:"name"`
		Category string `json:"category"`
		Pattern  string `json:"pattern"`
		Action   string `json:"action"`
		Severity string `json:"severity"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.Action == "" {
		input.Action = "block"
	}
	if input.Severity == "" {
		input.Severity = "high"
	}

	var rule Rule
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
	`, tenantID, input.Name, input.Category, input.Pattern, input.Action, input.Severity,
	).Scan(&rule.ID, &rule.TenantID, &rule.Name, &rule.Category, &rule.Pattern,
		&rule.Action, &rule.Severity, &rule.Enabled, &rule.CreatedAt, &rule.UpdatedAt)
	if err != nil {
		slog.Error("guardrail kural hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "kural oluşturulamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, rule)
}

func (h *Handler) Evaluate(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		Prompt   string `json:"prompt"`
		Response string `json:"response"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, category, pattern, action, severity
		FROM guardrail.rules WHERE tenant_id = $1 AND enabled = true
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "kural sorgu hatası"})
		return
	}
	defer rows.Close()

	type rule struct {
		ID       string
		Name     string
		Category string
		Pattern  string
		Action   string
		Severity string
	}

	var rules []guardRule
	for rows.Next() {
		var r guardRule
		if err := rows.Scan(&r.ID, &r.Name, &r.Category, &r.Pattern, &r.Action, &r.Severity); err != nil {
			continue
		}
		rules = append(rules, r)
	}

	type evalResult struct {
		RuleID      string `json:"rule_id"`
		RuleName    string `json:"rule_name"`
		Category    string `json:"category"`
		Matched     bool   `json:"matched"`
		ActionTaken string `json:"action_taken"`
	}

	var results []evalResult
	blocked := false

	for _, rule := range rules {
		matched := evaluateRule(rule, input.Prompt, input.Response)

		actionTaken := "none"
		if matched {
			actionTaken = rule.Action
			if rule.Action == "block" {
				blocked = true
			}
		}

		results = append(results, evalResult{
			RuleID:      rule.ID,
			RuleName:    rule.Name,
			Category:    rule.Category,
			Matched:     matched,
			ActionTaken: actionTaken,
		})

		h.logEvaluation(tenantID, rule.ID, input.Prompt, input.Response, matched, actionTaken)
	}

	resp := map[string]interface{}{
		"results": results,
		"blocked": blocked,
		"allowed": !blocked,
	}

	status := http.StatusOK
	if blocked {
		status = http.StatusForbidden
	}
	httputil.WriteJSON(w, status, resp)
}

func evaluateRule(r guardRule, prompt, response string) bool {
	if r.Pattern == "" {
		return false
	}
	if prompt != "" && containsPattern(r.Pattern, prompt) {
		return true
	}
	if response != "" && containsPattern(r.Pattern, response) {
		return true
	}
	return false
}

func containsPattern(pattern, text string) bool {
	if len(pattern) > 2 && pattern[0] == '/' && pattern[len(pattern)-1] == '/' {
		// Simple regex-like: /*keyword*/ pattern matching
		keyword := pattern[1 : len(pattern)-1]
		for i := 0; i <= len(text)-len(keyword); i++ {
			if text[i:i+len(keyword)] == keyword {
				return true
			}
		}
		return false
	}
	for i := 0; i <= len(text)-len(pattern); i++ {
		if text[i:i+len(pattern)] == pattern {
			return true
		}
	}
	return false
}

func (h *Handler) logEvaluation(tenantID, ruleID, prompt, response string, matched bool, actionTaken string) {
	_, err := h.pool.Exec(nil, `
		INSERT INTO guardrail.evaluations (tenant_id, rule_id, prompt, response, matched, action_taken)
		VALUES ($1, $2, $3, $4, $5, $6)
	`, tenantID, ruleID, prompt, response, matched, actionTaken)
	if err != nil {
		slog.Debug("guardrail log hatası", "error", err)
	}
}

func (h *Handler) DeleteRule(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	ruleID := chi.URLParam(r, "ruleId")

	_, err := h.pool.Exec(r.Context(), `
		DELETE FROM guardrail.rules WHERE id = $1 AND tenant_id = $2
	`, ruleID, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "silme hatası"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "silindi"})
}

func (h *Handler) SeedDefaults(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	defaults := []struct {
		Name     string
		Category string
		Pattern  string
		Action   string
		Severity string
	}{
		{"SQL Injection", "prompt_injection", "/ignore previous instructions/", "block", "critical"},
		{"Prompt Leak", "prompt_injection", "/reveal your prompt/", "block", "critical"},
		{"Email Leak", "pii_leakage", "/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/", "block", "high"},
		{"Phone Leak", "pii_leakage", "/\\+?[0-9]{10,15}/", "block", "high"},
		{"Toxic Content", "toxic_output", "/\\b(hate|discriminate|violent|threat)\\b/i", "flag", "medium"},
		{"Credit Card", "pii_leakage", "/\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\\b/", "block", "critical"},
		{"API Key Leak", "pii_leakage", "/\\b(sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{36,})\\b/", "block", "critical"},
		{"Hallucination Pattern", "hallucination", "/\\bI am not aware\\b|\\bI cannot confirm\\b/i", "flag", "medium"},
	}

	count := 0
	for _, d := range defaults {
		var existing int
		h.pool.QueryRow(nil, `SELECT COUNT(*) FROM guardrail.rules WHERE tenant_id = $1 AND category = $2 AND name = $3`,
			tenantID, d.Category, d.Name).Scan(&existing)
		if existing > 0 {
			continue
		}

		_, err := h.pool.Exec(nil, `
			INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity)
			VALUES ($1, $2, $3, $4, $5, $6)
		`, tenantID, d.Name, d.Category, d.Pattern, d.Action, d.Severity)
		if err != nil {
			slog.Error("default rule seed hatası", "error", err)
			continue
		}
		count++
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"status":  "varsayılan kurallar oluşturuldu",
		"created": count,
	})
}
