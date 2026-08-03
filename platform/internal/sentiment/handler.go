// Package sentiment provides handlers for sentiment analysis (FR-D7) and hallucination detection (FR-D8).
package sentiment

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

// Handler holds dependencies for sentiment HTTP handlers.
type Handler struct {
	pool dbiface.DB
	svc  *Engine
}

// NewHandler creates a new sentiment Handler.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new sentiment Handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: dbiface.NewAdapter(pool),
		svc:  NewEngine(pool),
	}
}

// AnalyzeSentiment handles POST /v1/workspaces/{ws}/sentiment/analyze
// Belirtilen marka için duygu analizi yapar.
func (h *Handler) AnalyzeSentiment(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
		Prompt  string `json:"prompt,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	results, err := h.svc.AnalyzeSentiment(r.Context(), req.BrandID, workspaceID, tenantID, req.Prompt)
	if err != nil {
		slog.Error("sentiment analiz hatası", "error", err, "brand", req.BrandID)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sentiment analizi başarısız"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// ListSentiment handles GET /v1/workspaces/{ws}/sentiment
// Bir markanın duygu analizi geçmişini listeler.
func (h *Handler) ListSentiment(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT ss.id, ss.brand_id, ss.engine_name, ss.overall_sentiment,
		       ss.positive_score, ss.neutral_score, ss.negative_score,
		       ss.mention_count, ss.analyzed_at
		FROM analysis.sentiment_scores ss
		JOIN config.brands b ON b.id = ss.brand_id
		WHERE ss.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR ss.brand_id = $3)
		ORDER BY ss.analyzed_at DESC
		LIMIT 100
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("sentiment sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type sentimentRow struct {
		ID               string    `json:"id"`
		BrandID          string    `json:"brand_id"`
		EngineName       string    `json:"engine_name"`
		OverallSentiment float64   `json:"overall_sentiment"`
		PositiveScore    float64   `json:"positive_score"`
		NeutralScore     float64   `json:"neutral_score"`
		NegativeScore    float64   `json:"negative_score"`
		MentionCount     int       `json:"mention_count"`
		AnalyzedAt       time.Time `json:"analyzed_at"`
	}

	results := make([]sentimentRow, 0)
	for rows.Next() {
		var s sentimentRow
		if err := rows.Scan(&s.ID, &s.BrandID, &s.EngineName, &s.OverallSentiment,
			&s.PositiveScore, &s.NeutralScore, &s.NegativeScore, &s.MentionCount, &s.AnalyzedAt); err != nil {
			slog.Warn("sentiment satır okuma hatası", "error", err)
			continue
		}
		results = append(results, s)
	}

	if rows.Err() != nil {
		slog.Warn("sentiment rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// GetSentimentSummary handles GET /v1/workspaces/{ws}/sentiment/summary
// Bir markanın özet duygu skorunu döndürür.
func (h *Handler) GetSentimentSummary(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	var overall float64
	var positive, neutral, negative float64
	var mentionCount int
	err := h.pool.QueryRow(r.Context(), `
		SELECT COALESCE(AVG(overall_sentiment), 0),
		       COALESCE(AVG(positive_score), 0),
		       COALESCE(AVG(neutral_score), 0),
		       COALESCE(AVG(negative_score), 0),
		       COALESCE(SUM(mention_count), 0)
		FROM analysis.sentiment_scores ss
		JOIN config.brands b ON b.id = ss.brand_id
		WHERE ss.tenant_id = $1 AND b.workspace_id = $2 AND ss.brand_id = $3
	`, tenantID, workspaceID, brandID).Scan(&overall, &positive, &neutral, &negative, &mentionCount)
	if err != nil {
		slog.Debug("sentiment özet sorgu hatası", "error", err)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"brand_id":       brandID,
		"overall":        overall,
		"positive":       positive,
		"neutral":        neutral,
		"negative":       negative,
		"mention_count":  mentionCount,
		"classification": classifySentiment(overall),
	})
}

// DetectHallucinations handles POST /v1/workspaces/{ws}/hallucination/detect
// Belirtilen marka için hallüsinasyon tespiti yapar.
func (h *Handler) DetectHallucinations(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	results, err := h.svc.DetectHallucinations(r.Context(), req.BrandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("hallüsinasyon tespit hatası", "error", err, "brand", req.BrandID)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "hallüsinasyon tespiti başarısız"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// ListHallucinations handles GET /v1/workspaces/{ws}/hallucination
// Tespit edilen hallüsinasyonları listeler.
func (h *Handler) ListHallucinations(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT hf.id, hf.brand_id, hf.engine_name, hf.hallucination_type,
		       hf.severity, hf.description, hf.confidence, hf.verified, hf.created_at
		FROM analysis.hallucination_flags hf
		JOIN config.brands b ON b.id = hf.brand_id
		WHERE hf.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR hf.brand_id = $3)
		ORDER BY hf.created_at DESC
		LIMIT 100
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("hallüsinasyon sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type hallucinationRow struct {
		ID                string    `json:"id"`
		BrandID           string    `json:"brand_id"`
		EngineName        string    `json:"engine_name"`
		HallucinationType string    `json:"hallucination_type"`
		Severity          string    `json:"severity"`
		Description       string    `json:"description"`
		Confidence        float64   `json:"confidence"`
		Verified          *bool     `json:"verified,omitempty"`
		CreatedAt         time.Time `json:"created_at"`
	}

	results := make([]hallucinationRow, 0)
	for rows.Next() {
		var hf hallucinationRow
		if err := rows.Scan(&hf.ID, &hf.BrandID, &hf.EngineName, &hf.HallucinationType,
			&hf.Severity, &hf.Description, &hf.Confidence, &hf.Verified, &hf.CreatedAt); err != nil {
			slog.Warn("hallüsinasyon satır okuma hatası", "error", err)
			continue
		}
		results = append(results, hf)
	}

	if rows.Err() != nil {
		slog.Warn("hallüsinasyon rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// VerifyHallucination handles POST /v1/workspaces/{ws}/hallucination/{flagId}/verify
// Bir hallüsinasyon kaydını doğrulanmış (true) veya yanlış pozitif (false) olarak işaretler.
func (h *Handler) VerifyHallucination(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	flagID := chi.URLParam(r, "flagId")

	var req struct {
		Verified bool `json:"verified"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	result, err := h.pool.Exec(r.Context(), `
		UPDATE analysis.hallucination_flags SET verified = $1 WHERE id = $2 AND tenant_id = $3
	`, req.Verified, flagID, tenantID)
	if err != nil {
		slog.Error("hallüsinasyon doğrulama hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "doğrulama başarısız")
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteError(w, http.StatusNotFound, "hallüsinasyon kaydı bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "verified"})
}

// classifySentiment classifies a sentiment score into a label.
func classifySentiment(score float64) string {
	if score >= 0.7 {
		return "olumlu"
	} else if score >= 0.4 {
		return "nötr"
	}
	return "olumsuz"
}
