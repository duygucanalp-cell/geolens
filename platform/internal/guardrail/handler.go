// Package guardrail provides handlers and logic for guardrail functionality.
package guardrail

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"regexp"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
	"github.com/geolens/platform/platform/queue"
)

type Handler struct {
	pool dbiface.DB
}

type guardRule struct {
	ID       string
	Name     string
	Category string
	Pattern  string
	Action   string
	Severity string
}

func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
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
			slog.Warn("guardrail rule satır okuma hatası", "error", err)
			continue
		}
		rules = append(rules, r)
	}

	if rows.Err() != nil {
		slog.Warn("guardrail rule rows iterasyon hatası", "error", rows.Err())
	}

	// R3: tenant hiç kural oluşturmamışsa varsayılanları yükle
	if len(rules) == 0 {
		h.seedDefaults(r.Context(), tenantID)
		// Yeniden sorgula
		rows2, err2 := h.pool.Query(r.Context(), `
			SELECT id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
			FROM guardrail.rules WHERE tenant_id = $1 ORDER BY category, name
		`, tenantID)
		if err2 == nil {
			defer rows2.Close()
			rules = nil
			for rows2.Next() {
				var r Rule
				if err := rows2.Scan(&r.ID, &r.TenantID, &r.Name, &r.Category, &r.Pattern,
					&r.Action, &r.Severity, &r.Enabled, &r.CreatedAt, &r.UpdatedAt); err != nil {
					slog.Warn("guardrail rule (seed tekrar) satır okuma hatası", "error", err)
					continue
				}
				rules = append(rules, r)
			}
			if rows2.Err() != nil {
				slog.Warn("guardrail rule (seed tekrar) rows iterasyon hatası", "error", rows2.Err())
			}
		}
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

	start := time.Now()

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, category, pattern, action, severity
		FROM guardrail.rules WHERE tenant_id = $1 AND enabled = true
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "kural sorgu hatası"})
		return
	}
	defer rows.Close()

	var rules []guardRule
	for rows.Next() {
		var r guardRule
		if err := rows.Scan(&r.ID, &r.Name, &r.Category, &r.Pattern, &r.Action, &r.Severity); err != nil {
			slog.Warn("guardrail evaluation rule satır okuma hatası", "error", err)
			continue
		}
		rules = append(rules, r)
	}

	if rows.Err() != nil {
		slog.Warn("guardrail evaluation rows iterasyon hatası", "error", rows.Err())
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

		h.logEvaluation(r.Context(), tenantID, rule.ID, input.Prompt, input.Response, matched, actionTaken, time.Since(start).Milliseconds())

		// O-6: guardrail ihlali olayını outbox üzerinden taşı (doğrudan DB yazımı yerine)
		// İçerik türevli deterministik anahtar: aynı tenant+kural+prompt/response ikilisi
		// için tekrarlanan evaluate çağrıları yinelenen olay üretmez (unique index).
		if matched && actionTaken != "none" {
			idemKey := guardrailIdempotencyKey(tenantID, rule.ID, input.Prompt, input.Response)
			if err := queue.EnqueueEvent(r.Context(), h.pool, "guardrail.violation", queue.StreamGovernance, map[string]interface{}{
				"rule_id":      rule.ID,
				"rule_name":    rule.Name,
				"category":     rule.Category,
				"severity":     rule.Severity,
				"action_taken": actionTaken,
			}, tenantID, idemKey); err != nil {
				slog.Warn("guardrail olayı outbox'a yazılamadı", "rule_id", rule.ID, "error", err)
			}
		}
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

// guardrailIdempotencyKey deterministik bir outbox idempotency anahtarı üretir.
// Aynı (tenant, rule, prompt, response) ikilisi her zaman aynı anahtarı verir —
// tekrarlanan evaluate çağrıları event_outbox unique index'i sayesinde tek olaya indirgenir.
func guardrailIdempotencyKey(tenantID, ruleID, prompt, response string) string {
	h := sha256.Sum256([]byte(tenantID + "|" + ruleID + "|" + prompt + "|" + response))
	return fmt.Sprintf("guardrail:%s:%x", ruleID, h[:12])
}

// compilePattern compiles a guardrail pattern into a regexp.
// Patterns in /*keyword*/ format are treated as simple substring matches.
// Patterns in /.../i format are treated as case-insensitive regex.
// Other patterns are treated as exact substring matches.
func compilePattern(pattern string) (*regexp.Regexp, bool, error) {
	if len(pattern) <= 2 {
		return nil, false, nil
	}

	// Case-insensitive regex: /pattern/i
	if pattern[len(pattern)-2:] == "/i" && pattern[0] == '/' {
		re, err := regexp.Compile("(?i)" + pattern[1:len(pattern)-2])
		if err != nil {
			return nil, false, err
		}
		return re, true, nil
	}

	// Regex pattern: /pattern/
	if pattern[0] == '/' && pattern[len(pattern)-1] == '/' {
		re, err := regexp.Compile(pattern[1 : len(pattern)-1])
		if err != nil {
			return nil, false, err
		}
		return re, true, nil
	}

	return nil, false, nil
}

func evaluateRule(r guardRule, prompt, response string) bool {
	if r.Pattern == "" {
		return false
	}

	re, isRegex, err := compilePattern(r.Pattern)
	if err != nil {
		slog.Warn("guardrail: pattern derleme hatası", "rule_id", r.ID, "pattern", r.Pattern, "error", err)
		return false
	}

	text := ""
	if prompt != "" {
		text = prompt
	}
	if response != "" {
		if text != "" {
			text += "\n"
		}
		text += response
	}

	if isRegex && re != nil {
		return re.MatchString(text)
	}

	// Plain substring match
	for i := 0; i <= len(text)-len(r.Pattern); i++ {
		if text[i:i+len(r.Pattern)] == r.Pattern {
			return true
		}
	}
	return false
}

func (h *Handler) logEvaluation(ctx context.Context, tenantID, ruleID, prompt, response string, matched bool, actionTaken string, durationMs int64) {
	_, err := h.pool.Exec(ctx, `
		INSERT INTO guardrail.evaluations (tenant_id, rule_id, prompt, response, matched, action_taken, duration_ms)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
	`, tenantID, ruleID, prompt, response, matched, actionTaken, durationMs)
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

func (h *Handler) ToggleRule(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	ruleID := chi.URLParam(r, "ruleId")

	var input struct {
		Enabled bool `json:"enabled"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	_, err := h.pool.Exec(r.Context(), `
		UPDATE guardrail.rules SET enabled = $1, updated_at = now() WHERE id = $2 AND tenant_id = $3
	`, input.Enabled, ruleID, tenantID)
	if err != nil {
		slog.Error("kural toggle hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "kural güncellenemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "güncellendi", "enabled": func() string {
		if input.Enabled {
			return "true"
		}
		return "false"
	}()})
}

func (h *Handler) SeedDefaults(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	h.seedDefaults(r.Context(), tenantID)

	httputil.WriteJSON(w, http.StatusCreated, map[string]string{"status": "varsayılan kurallar oluşturuldu"})
}

func (h *Handler) seedDefaults(ctx context.Context, tenantID string) {
	now := time.Now().UTC()

	defaults := []Rule{
		{Name: "SQL Injection", Category: "prompt_injection", Pattern: "/ignore previous instructions/", Action: "block", Severity: "critical", Enabled: true},
		{Name: "Prompt Leak", Category: "prompt_injection", Pattern: "/reveal your prompt/", Action: "block", Severity: "critical", Enabled: true},
		{Name: "Email Leak", Category: "pii_leakage", Pattern: "/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/", Action: "block", Severity: "high", Enabled: true},
		{Name: "Phone Leak", Category: "pii_leakage", Pattern: "/\\+?[0-9]{10,15}/", Action: "block", Severity: "high", Enabled: true},
		{Name: "Toxic Content", Category: "toxic_output", Pattern: "/\\b(hate|discriminate|violent|threat)\\b/i", Action: "flag", Severity: "medium", Enabled: true},
		{Name: "Credit Card", Category: "pii_leakage", Pattern: "/\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\\b/", Action: "block", Severity: "critical", Enabled: true},
		{Name: "API Key Leak", Category: "pii_leakage", Pattern: "/\\b(sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{36,})\\b/", Action: "block", Severity: "critical", Enabled: true},
		{Name: "Hallucination Pattern", Category: "hallucination", Pattern: "/\\bI am not aware\\b|\\bI cannot confirm\\b/i", Action: "flag", Severity: "medium", Enabled: true},
	}

	count := 0
	for _, d := range defaults {
		_, err := h.pool.Exec(ctx, `
			INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			ON CONFLICT DO NOTHING
		`, tenantID, d.Name, d.Category, d.Pattern, d.Action, d.Severity, d.Enabled, now, now)
		if err != nil {
			slog.Error("default rule seed hatası", "name", d.Name, "error", err)
			continue
		}
		count++
	}

	slog.Info("guardrail varsayılan kurallar yüklendi", "tenant", tenantID, "created", count)
}
