package prompt

import (
	"encoding/json"
	"log/slog"
	"math/rand"
	"net/http"
	"strconv"
	"strings"
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

// RunAudit bir prompt'u denetler ve sonucu DB'ye kaydeder.
func (h *Handler) RunAudit(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		PromptID   string `json:"prompt_id"`
		PromptText string `json:"prompt_text"`
		EngineName string `json:"engine_name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.PromptText == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "prompt_text gerekli"})
		return
	}
	if input.EngineName == "" {
		input.EngineName = "generic"
	}

	auditID := id.New()
	now := time.Now().UTC()

	// Prompt denetimini gerçekleştir
	score, issues, status := h.auditPrompt(input.PromptText)

	// Token ve latency simülasyonu (gerçek engine çağrısı yapılmadığı için)
	tokenCount := len(input.PromptText) / 4 // yaklaşık token sayısı
	if tokenCount < 1 {
		tokenCount = 1
	}
	latencyMs := 100 + rand.Intn(900) // 100-1000ms simülasyon

	// Sonucu DB'ye yaz
	issuesJSON, _ := json.Marshal(issues)
	metadata := map[string]interface{}{
		"engine":      input.EngineName,
		"score":       score,
		"issue_count": len(issues),
		"audited_at":  now.Format(time.RFC3339),
	}
	metaJSON, _ := json.Marshal(metadata)

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO prompt.audits (id, tenant_id, prompt_id, prompt_text, engine_name, status, score, token_count, latency_ms, issues, metadata)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
	`, auditID, tenantID, input.PromptID, input.PromptText, input.EngineName,
		status, score, tokenCount, latencyMs, issuesJSON, metaJSON)
	if err != nil {
		slog.Warn("prompt audit DB'ye yazılamadı", "audit_id", auditID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "denetim kaydedilemedi"})
		return
	}

	slog.Info("prompt denetimi tamam", "audit_id", auditID, "status", status, "score", score, "issues", len(issues))

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"audit_id":    auditID,
		"prompt_id":   input.PromptID,
		"prompt_text": input.PromptText,
		"engine_name": input.EngineName,
		"status":      status,
		"score":       score,
		"token_count": tokenCount,
		"latency_ms":  latencyMs,
		"issues":      issues,
	})
}

// ListAudits tenant bazında prompt denetim geçmişini döndürür.
func (h *Handler) ListAudits(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limitStr := r.URL.Query().Get("limit")
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit < 1 || limit > 100 {
		limit = 20
	}

	statusFilter := r.URL.Query().Get("status")
	engineFilter := r.URL.Query().Get("engine")

	query := `SELECT id, COALESCE(prompt_id, ''), prompt_text, engine_name, status, score, token_count, latency_ms, issues, created_at
	          FROM prompt.audits WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	offsetStr := r.URL.Query().Get("offset")
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}

	if statusFilter == "passed" || statusFilter == "flagged" || statusFilter == "failed" {
		query += ` AND status = $` + strconv.Itoa(paramIdx)
		args = append(args, statusFilter)
		paramIdx++
	}
	if engineFilter != "" {
		query += ` AND engine_name = $` + strconv.Itoa(paramIdx)
		args = append(args, engineFilter)
		paramIdx++
	}

	query += ` ORDER BY created_at DESC LIMIT $` + strconv.Itoa(paramIdx) + ` OFFSET $` + strconv.Itoa(paramIdx+1)
	args = append(args, limit, offset)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("audit listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "denetim geçmişi alınamadı"})
		return
	}
	defer rows.Close()

	type auditResult struct {
		ID         string      `json:"id"`
		PromptID   string      `json:"prompt_id"`
		PromptText string      `json:"prompt_text"`
		EngineName string      `json:"engine_name"`
		Status     string      `json:"status"`
		Score      float64     `json:"score"`
		TokenCount int         `json:"token_count"`
		LatencyMs  int         `json:"latency_ms"`
		Issues     interface{} `json:"issues"`
		CreatedAt  string      `json:"created_at"`
	}

	var audits []auditResult
	for rows.Next() {
		var a auditResult
		var createdAt string
		if err := rows.Scan(&a.ID, &a.PromptID, &a.PromptText, &a.EngineName, &a.Status,
			&a.Score, &a.TokenCount, &a.LatencyMs, &a.Issues, &createdAt); err != nil {
			slog.Warn("audit satırı okunamadı", "error", err)
			continue
		}
		a.CreatedAt = createdAt
		audits = append(audits, a)
	}

	if rows.Err() != nil {
		slog.Warn("prompt audit rows iterasyon hatası", "error", rows.Err())
	}

	if audits == nil {
		audits = []auditResult{}
	}

	httputil.WriteJSON(w, http.StatusOK, audits)
}

// GetAudit tek bir prompt denetiminin detayını döndürür.
func (h *Handler) GetAudit(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	auditID := chi.URLParam(r, "auditId")

	var promptID, promptText, engineName, status, createdAt string
	var score float64
	var tokenCount, latencyMs int
	var issuesRaw, metadataRaw []byte

	err := h.pool.QueryRow(r.Context(), `
		SELECT COALESCE(prompt_id, ''), prompt_text, engine_name, status, score, token_count, latency_ms, issues, metadata, created_at
		FROM prompt.audits WHERE id = $1 AND tenant_id = $2
	`, auditID, tenantID).Scan(&promptID, &promptText, &engineName, &status,
		&score, &tokenCount, &latencyMs, &issuesRaw, &metadataRaw, &createdAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "denetim bulunamadı"})
		return
	}

	var issues, metadata interface{}
	if err := json.Unmarshal(issuesRaw, &issues); err != nil {
		slog.Warn("issues JSON çözümleme hatası", "audit_id", auditID, "error", err)
	}
	if err := json.Unmarshal(metadataRaw, &metadata); err != nil {
		slog.Warn("metadata JSON çözümleme hatası", "audit_id", auditID, "error", err)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"audit_id":    auditID,
		"prompt_id":   promptID,
		"prompt_text": promptText,
		"engine_name": engineName,
		"status":      status,
		"score":       score,
		"token_count": tokenCount,
		"latency_ms":  latencyMs,
		"issues":      issues,
		"metadata":    metadata,
		"created_at":  createdAt,
	})
}

// auditPrompt bir prompt'u kalite ve güvenlik açısından denetler.
func (h *Handler) auditPrompt(promptText string) (float64, []map[string]interface{}, string) {
	issues := []map[string]interface{}{}
	score := 1.0

	// Uzunluk kontrolü
	if len(promptText) < 10 {
		issues = append(issues, map[string]interface{}{
			"type": "length", "severity": "low",
			"message": "Prompt çok kısa — daha açıklayıcı olmalı",
		})
		score -= 0.1
	}
	if len(promptText) > 2000 {
		issues = append(issues, map[string]interface{}{
			"type": "length", "severity": "medium",
			"message": "Prompt çok uzun — token limiti aşılabilir",
		})
		score -= 0.15
	}

	// Hedef marka varlığı
	if !containsAny(promptText, []string{"marka", "brand", "şirket", "company", "firma"}) {
		issues = append(issues, map[string]interface{}{
			"type": "clarity", "severity": "medium",
			"message": "Prompt'ta hedef marka/şirket belirtilmemiş",
		})
		score -= 0.15
	}

	// Kaynak/atıf talebi
	if !containsAny(promptText, []string{"kaynak", "source", "referans", "reference", "cite", "atıf"}) {
		issues = append(issues, map[string]interface{}{
			"type": "quality", "severity": "low",
			"message": "Prompt kaynak gösterme talebi içermiyor — yanıt kalitesi düşebilir",
		})
		score -= 0.1
	}

	// Prompt injection riski
	if containsAny(promptText, []string{"ignore", "ignore all", "forget", "unset", "override", "system prompt"}) {
		issues = append(issues, map[string]interface{}{
			"type": "injection", "severity": "high",
			"message": "Prompt injection riski — sistemi atlatma girişimi tespit edildi",
		})
		score -= 0.4
	}

	// PII riski (basit tarama)
	piiPatterns := []string{"@", "tc kimlik", "kimlik no", "pasaport", "telefon", "phone", "email", "adres", "address"}
	if containsAny(promptText, piiPatterns) {
		issues = append(issues, map[string]interface{}{
			"type": "pii", "severity": "high",
			"message": "Prompt kişisel veri (PII) içerebilir — KVKK/GDPR uyumu kontrol edilmeli",
		})
		score -= 0.3
	}

	if score < 0 {
		score = 0
	}

	status := "passed"
	if score < 0.5 {
		status = "failed"
	} else if len(issues) > 0 {
		status = "flagged"
	}

	return score, issues, status
}

// containsAny metnin verilen alt dizelerden herhangi birini içerip içermediğini kontrol eder.
func containsAny(text string, patterns []string) bool {
	lower := strings.ToLower(text)
	for _, p := range patterns {
		if strings.Contains(lower, strings.ToLower(p)) {
			return true
		}
	}
	return false
}
