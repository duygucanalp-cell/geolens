// Package public provides handlers for the public REST API (FR-F6).
// Tüm endpoint'ler /public/v1 altında, API anahtarı ile kimlik doğrulamalıdır.
package public

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler serves public API endpoints under /public/v1
type Handler struct {
	pool dbiface.DB
}

// NewHandler creates a new public API Handler with the given DB interface.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new public API Handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

// GetScore handles GET /public/v1/scores/{brandID}
// Returns the latest score for a brand (public, API key auth).
func (h *Handler) GetScore(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandID")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	var brandName string
	var score float64
	var fidelityLabel string
	var freshnessAt time.Time

	err := h.pool.QueryRow(r.Context(), `
		SELECT b.name, COALESCE(s.value, 0), COALESCE(s.fidelity_label, 'yok'), s.freshness_at
		FROM config.brands b
		LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.tenant_id = b.tenant_id
		WHERE b.id = $1 AND b.tenant_id = $2 AND b.is_active = true
		ORDER BY s.freshness_at DESC LIMIT 1
	`, brandID, tenantID).Scan(&brandName, &score, &fidelityLabel, &freshnessAt)
	if err != nil {
		slog.Debug("public: marka bulunamadı", "brand_id", brandID, "error", err)
		httputil.WriteError(w, http.StatusNotFound, "marka bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"brand_id":    brandID,
		"brand_name":  brandName,
		"score":       score,
		"fidelity":    fidelityLabel,
		"measured_at": freshnessAt.Format(time.RFC3339),
	})
}

// ListScores handles GET /public/v1/scores
// Bir çalışma alanındaki tüm markaların en güncel skorlarını döndürür.
func (h *Handler) ListScores(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT b.id, b.name, COALESCE(s.value, 0), COALESCE(s.fidelity_label, 'yok'), s.freshness_at
		FROM config.brands b
		LEFT JOIN LATERAL (
			SELECT value, fidelity_label, freshness_at
			FROM measure.scores
			WHERE brand_id = b.id AND tenant_id = b.tenant_id
			ORDER BY freshness_at DESC LIMIT 1
		) s ON true
		WHERE b.tenant_id = $1 AND b.is_active = true
		ORDER BY b.name ASC
	`, tenantID)
	if err != nil {
		slog.Debug("public: scores listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type scoreRow struct {
		BrandID    string  `json:"brand_id"`
		BrandName  string  `json:"brand_name"`
		Score      float64 `json:"score"`
		Fidelity   string  `json:"fidelity"`
		MeasuredAt string  `json:"measured_at,omitempty"`
	}

	scores := make([]scoreRow, 0)
	for rows.Next() {
		var s scoreRow
		var at time.Time
		if err := rows.Scan(&s.BrandID, &s.BrandName, &s.Score, &s.Fidelity, &at); err != nil {
			slog.Warn("public: score satır okuma hatası", "error", err)
			continue
		}
		if !at.IsZero() {
			s.MeasuredAt = at.Format(time.RFC3339)
		}
		scores = append(scores, s)
	}

	httputil.WriteJSON(w, http.StatusOK, scores)
}

// ListBrands handles GET /public/v1/brands
// Çalışma alanındaki tüm aktif markaları döndürür.
func (h *Handler) ListBrands(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, COALESCE(website_url, '')
		FROM config.brands
		WHERE tenant_id = $1 AND is_active = true
		ORDER BY name ASC
	`, tenantID)
	if err != nil {
		slog.Debug("public: brand listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type brandRow struct {
		ID         string `json:"id"`
		Name       string `json:"name"`
		WebsiteURL string `json:"website_url"`
	}

	brands := make([]brandRow, 0)
	for rows.Next() {
		var b brandRow
		if err := rows.Scan(&b.ID, &b.Name, &b.WebsiteURL); err != nil {
			continue
		}
		brands = append(brands, b)
	}

	httputil.WriteJSON(w, http.StatusOK, brands)
}

// GetBrand handles GET /public/v1/brands/{brandID}
// Tek bir markanın detayını döndürür.
func (h *Handler) GetBrand(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandID")

	var name, url string
	err := h.pool.QueryRow(r.Context(), `
		SELECT name, COALESCE(website_url, '')
		FROM config.brands
		WHERE id = $1 AND tenant_id = $2 AND is_active = true
	`, brandID, tenantID).Scan(&name, &url)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "marka bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"id":          brandID,
		"name":        name,
		"website_url": url,
	})
}

// ListCitations handles GET /public/v1/citations?brand_id=xxx
// Bir markaya ait alıntı listesini döndürür.
func (h *Handler) ListCitations(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	query := `
		SELECT c.id, c.brand_id, c.url, c.title, c.snippet, c.position, c.engine, c.measured_at
		FROM measure.citations c
		JOIN config.brands b ON b.id = c.brand_id
		WHERE c.tenant_id = $1 AND b.tenant_id = $1
			AND ($2 = '' OR c.brand_id = $2)
		ORDER BY c.measured_at DESC
		LIMIT 200
	`
	rows, err := h.pool.Query(r.Context(), query, tenantID, brandID)
	if err != nil {
		slog.Debug("public: citation listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type citationRow struct {
		ID         string `json:"id"`
		BrandID    string `json:"brand_id"`
		URL        string `json:"url"`
		Title      string `json:"title"`
		Snippet    string `json:"snippet,omitempty"`
		Position   int    `json:"position"`
		Engine     string `json:"engine"`
		MeasuredAt string `json:"measured_at"`
	}

	citations := make([]citationRow, 0)
	for rows.Next() {
		var c citationRow
		var at time.Time
		if err := rows.Scan(&c.ID, &c.BrandID, &c.URL, &c.Title, &c.Snippet, &c.Position, &c.Engine, &at); err != nil {
			continue
		}
		c.MeasuredAt = at.Format(time.RFC3339)
		citations = append(citations, c)
	}

	httputil.WriteJSON(w, http.StatusOK, citations)
}

// ListReports handles GET /public/v1/reports?brand_id=xxx
// Bir markaya ait rapor meta verilerini döndürür.
func (h *Handler) ListReports(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	query := `
		SELECT r.id, r.type, r.file_name, r.page_count, r.generated_at
		FROM measure.measurement_reports r
		WHERE r.tenant_id = $1
			AND ($2 = '' OR r.brand_id = $2)
		ORDER BY r.generated_at DESC
		LIMIT 50
	`
	rows, err := h.pool.Query(r.Context(), query, tenantID, brandID)
	if err != nil {
		slog.Debug("public: report listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type reportRow struct {
		ID          string `json:"id"`
		Type        string `json:"type"`
		FileName    string `json:"file_name"`
		PageCount   int    `json:"page_count"`
		GeneratedAt string `json:"generated_at"`
	}

	reports := make([]reportRow, 0)
	for rows.Next() {
		var r reportRow
		var at time.Time
		if err := rows.Scan(&r.ID, &r.Type, &r.FileName, &r.PageCount, &at); err != nil {
			continue
		}
		r.GeneratedAt = at.Format(time.RFC3339)
		reports = append(reports, r)
	}

	httputil.WriteJSON(w, http.StatusOK, reports)
}

// DownloadReport handles GET /public/v1/reports/{reportID}/download
// Bir raporu PDF/CSV/Excel olarak indirir.
func (h *Handler) DownloadReport(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	reportID := chi.URLParam(r, "reportID")

	var fileData []byte
	var fileName, fileType string
	err := h.pool.QueryRow(r.Context(), `
		SELECT report_data, file_name, mime_type
		FROM measure.measurement_reports
		WHERE id = $1 AND tenant_id = $2
	`, reportID, tenantID).Scan(&fileData, &fileName, &fileType)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "rapor bulunamadı")
		return
	}

	w.Header().Set("Content-Type", fileType)
	w.Header().Set("Content-Disposition", "attachment; filename=\""+fileName+"\"")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(fileData)
}

// ListTrends handles GET /public/v1/trends?brand_id=xxx
// Returns score trends for a brand.
func (h *Handler) ListTrends(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id parametresi gerekli")
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT s.value, s.fidelity_label, s.freshness_at
		FROM measure.scores s
		WHERE s.brand_id = $1 AND s.tenant_id = $2
		ORDER BY s.freshness_at ASC
		LIMIT 50
	`, brandID, tenantID)
	if err != nil {
		slog.Error("public: trend sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"trends": []interface{}{}})
		return
	}
	defer rows.Close()

	type trendPoint struct {
		Value         float64 `json:"value"`
		FidelityLabel string  `json:"fidelity_label"`
		MeasuredAt    string  `json:"measured_at"`
	}

	trends := make([]trendPoint, 0)
	for rows.Next() {
		var t trendPoint
		var measuredAt time.Time
		if err := rows.Scan(&t.Value, &t.FidelityLabel, &measuredAt); err != nil {
			slog.Warn("public: trend satır okuma hatası", "error", err)
			continue
		}
		t.MeasuredAt = measuredAt.Format(time.RFC3339)
		trends = append(trends, t)
	}

	if rows.Err() != nil {
		slog.Warn("public trends rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"trends": trends})
}
