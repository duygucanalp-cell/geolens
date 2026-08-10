package config

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type brandRequest struct {
	Name        string   `json:"name"`
	WebsiteURL  string   `json:"website_url"`
	Competitors []string `json:"competitors,omitempty"`
}

type brandResponse struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	WebsiteURL string `json:"website_url"`
}

// Handler holds dependencies for config HTTP handlers.
type Handler struct {
	pool dbiface.DB
}

// NewHandler creates a new config handler with the given DB interface.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new config handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

// SearchBrands handles GET /v1/workspaces/{ws}/brands/search?q=...&exclude=...&offset=0&limit=20
// Workspace içindeki markaları isim veya ID ile arar (FR-B1).
func (h *Handler) SearchBrands(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	query := r.URL.Query().Get("q")
	exclude := r.URL.Query().Get("exclude")

	if query == "" {
		httputil.WriteError(w, http.StatusBadRequest, "q parametresi gerekli")
		return
	}

	offset := 0
	if v := r.URL.Query().Get("offset"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n >= 0 {
			offset = n
		}
	}
	limit := 20
	if v := r.URL.Query().Get("limit"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			limit = int(math.Min(float64(n), 100))
		}
	}

	// Total count first
	var total int
	if err := h.pool.QueryRow(r.Context(), `
		SELECT count(*)
		FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
			AND (name ILIKE '%' || $3 || '%' OR id ILIKE '%' || $3 || '%')
			AND ($4 = '' OR id != $4)
	`, workspaceID, tenantID, query, exclude).Scan(&total); err != nil {
		slog.Error("marka arama count hatası", "query", query, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, website_url
		FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
			AND (name ILIKE '%' || $3 || '%' OR id ILIKE '%' || $3 || '%')
			AND ($4 = '' OR id != $4)
		ORDER BY name
		LIMIT $5 OFFSET $6
	`, workspaceID, tenantID, query, exclude, limit, offset)
	if err != nil {
		slog.Error("marka arama hatası", "query", query, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	brands := make([]brandResponse, 0)
	for rows.Next() {
		var b brandResponse
		if err := rows.Scan(&b.ID, &b.Name, &b.WebsiteURL); err != nil {
			slog.Warn("marka arama satır okuma hatası", "error", err)
			continue
		}
		brands = append(brands, b)
	}

	if rows.Err() != nil {
		slog.Warn("marka arama rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"data":   brands,
		"total":  total,
		"offset": offset,
		"limit":  limit,
	})
}

// ListBrands handles GET /v1/workspaces/{ws}/brands
func (h *Handler) ListBrands(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, website_url
		FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
		ORDER BY name
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("marka listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	var parseErrors int
	brands := make([]brandResponse, 0)
	for rows.Next() {
		var b brandResponse
		if err := rows.Scan(&b.ID, &b.Name, &b.WebsiteURL); err != nil {
			slog.Error("marka satır okuma hatası", "error", err)
			parseErrors++
			continue
		}
		brands = append(brands, b)
	}

	if rows.Err() != nil {
		slog.Error("marka listesi rows iterasyon hatası", "error", rows.Err())
	}

	// K5: Kısmi sonuç uyarısı (parseErrors > 0 ise response header'a eklenir)
	if parseErrors > 0 {
		w.Header().Set("X-Has-More", "true")
	}

	httputil.WriteJSON(w, http.StatusOK, brands)
}

// CreateBrand handles POST /v1/workspaces/{ws}/brands
func (h *Handler) CreateBrand(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req brandRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.Name == "" || req.WebsiteURL == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "marka adı ve web sitesi zorunludur"})
		return
	}

	// Transaction başlat
	tx, err := h.pool.Begin(r.Context())
	if err != nil {
		slog.Error("transaction başlatma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "işlem başlatılamadı"})
		return
	}
	rollback := func() { _ = tx.Rollback(r.Context()) }

	// Kullanıcı tanımlı rakipleri doğrula (FR-B1)
	if len(req.Competitors) > 0 {
		for _, compID := range req.Competitors {
			if compID == "" {
				continue
			}
			var exists bool
			err = tx.QueryRow(r.Context(), `
				SELECT EXISTS(SELECT 1 FROM config.brands
					WHERE id = $1 AND tenant_id = $2 AND is_active = true)
			`, compID, tenantID).Scan(&exists)
			if err != nil || !exists {
				rollback()
				httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{
					"error": fmt.Sprintf("rakip bulunamadı: %s", compID),
				})
				return
			}
		}
	}

	var brandID string
	err = tx.QueryRow(r.Context(), `
		INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4)
		RETURNING id
	`, workspaceID, tenantID, req.Name, req.WebsiteURL).Scan(&brandID)

	if err != nil {
		rollback()
		slog.Error("marka oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "marka oluşturulamadı"})
		return
	}

	// Kullanıcı tanımlı rakipleri kaydet
	if len(req.Competitors) > 0 {
		for _, compID := range req.Competitors {
			if compID == brandID {
				continue // kendi kendine rakip olamaz
			}
			_, err = tx.Exec(r.Context(), `
				INSERT INTO config.brand_competitors (id, brand_id, competitor_id, tenant_id)
				VALUES (gen_random_uuid()::text, $1, $2, $3)
				ON CONFLICT (brand_id, competitor_id) DO NOTHING
			`, brandID, compID, tenantID)
			if err != nil {
				rollback()
				slog.Error("rakip kaydetme hatası", "brand", brandID, "competitor", compID, "error", err)
				httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "rakip kaydedilemedi"})
				return
			}
		}
	}

	// Transaction'ı commit et
	if err = tx.Commit(r.Context()); err != nil {
		rollback()
		slog.Error("transaction commit hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "işlem tamamlanamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, brandResponse{
		ID:         brandID,
		Name:       req.Name,
		WebsiteURL: req.WebsiteURL,
	})
}

// UpdateBrand handles PUT /v1/workspaces/{ws}/brands/{brandId}
// Marka adı ve/veya web sitesi URL'sini günceller.
func (h *Handler) UpdateBrand(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandId")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	var req struct {
		Name       string `json:"name"`
		WebsiteURL string `json:"website_url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.Name == "" && req.WebsiteURL == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "en az bir alan gerekli (name veya website_url)"})
		return
	}

	tag, err := h.pool.Exec(r.Context(), `
		UPDATE config.brands SET
			name = COALESCE(NULLIF($1, ''), name),
			website_url = COALESCE(NULLIF($2, ''), website_url)
		WHERE id = $3 AND workspace_id = $4 AND tenant_id = $5 AND is_active = true
	`, req.Name, req.WebsiteURL, brandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("marka güncelleme hatası", "brand", brandID, "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "marka güncellenemedi")
		return
	}

	if tag.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
		return
	}

	var resp brandResponse
	if err := h.pool.QueryRow(r.Context(), `
		SELECT id, name, website_url FROM config.brands
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3
	`, brandID, workspaceID, tenantID).Scan(&resp.ID, &resp.Name, &resp.WebsiteURL); err != nil {
		slog.Error("güncellenmiş marka okuma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "marka bilgisi okunamadı")
		return
	}

	slog.Info("marka güncellendi", "brand", brandID)
	httputil.WriteJSON(w, http.StatusOK, resp)
}

// DeleteBrand handles DELETE /v1/workspaces/{ws}/brands/{brandId}
// Markayı soft-delete eder (is_active = false).
func (h *Handler) DeleteBrand(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandId")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	tag, err := h.pool.Exec(r.Context(), `
		UPDATE config.brands SET is_active = false
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3 AND is_active = true
	`, brandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("marka silme hatası", "brand", brandID, "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "marka silinemedi")
		return
	}

	if tag.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
		return
	}

	slog.Info("marka silindi (soft-delete)", "brand", brandID)
	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "deleted",
		"brand_id": brandID,
	})
}

// DeleteBrandCompetitor handles DELETE /v1/workspaces/{ws}/brands/{brandId}/competitors/{competitorId}
// Tek bir rakip ilişkisini siler (FR-B1).
func (h *Handler) DeleteBrandCompetitor(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandId")
	competitorID := chi.URLParam(r, "competitorId")

	if brandID == "" || competitorID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id ve competitor_id gerekli")
		return
	}

	if brandID == competitorID {
		httputil.WriteError(w, http.StatusBadRequest, "kendi kendine rakip ilişkisi silinemez")
		return
	}

	tag, err := h.pool.Exec(r.Context(), `
		DELETE FROM config.brand_competitors
		WHERE brand_id = $1 AND competitor_id = $2 AND tenant_id = $3
	`, brandID, competitorID, tenantID)
	if err != nil {
		slog.Error("rakip silme hatası", "brand", brandID, "competitor", competitorID, "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "rakip silinemedi")
		return
	}

	if tag.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "rakip ilişkisi bulunamadı"})
		return
	}

	slog.Info("rakip silindi", "brand", brandID, "competitor", competitorID)
	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"status":        "deleted",
		"brand_id":      brandID,
		"competitor_id": competitorID,
	})
}

// GetSetupStatus handles GET /v1/workspaces/{ws}/setup-status
// Kurulum sihirbazının hangi adımların tamamlandığını döner.

// ListBrandCompetitors handles GET /v1/workspaces/{ws}/brands/{brandId}/competitors
// Kullanıcı tanımlı rakipleri listeler (FR-B1).
func (h *Handler) ListBrandCompetitors(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandId")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	// Markanın tenant'a ait olduğunu doğrula
	var brandExists bool
	if err := h.pool.QueryRow(r.Context(), `
		SELECT EXISTS(SELECT 1 FROM config.brands
			WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3)
	`, brandID, workspaceID, tenantID).Scan(&brandExists); err != nil || !brandExists {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
		return
	}

	// Kullanıcı tanımlı rakipleri getir
	rows, err := h.pool.Query(r.Context(), `
		SELECT bc.competitor_id, b.name AS competitor_name, bc.created_at
		FROM config.brand_competitors bc
		JOIN config.brands b ON b.id = bc.competitor_id
		WHERE bc.brand_id = $1 AND bc.tenant_id = $2
		ORDER BY b.name
	`, brandID, tenantID)
	if err != nil {
		slog.Error("rakip listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	type competitorItem struct {
		CompetitorID   string `json:"competitor_id"`
		CompetitorName string `json:"competitor_name"`
		CreatedAt      string `json:"created_at"`
	}

	competitors := make([]competitorItem, 0)
	for rows.Next() {
		var c competitorItem
		var createdAt time.Time
		if err := rows.Scan(&c.CompetitorID, &c.CompetitorName, &createdAt); err != nil {
			slog.Warn("rakip satır okuma hatası", "error", err)
			continue
		}
		c.CreatedAt = createdAt.Format(time.RFC3339)
		competitors = append(competitors, c)
	}

	if rows.Err() != nil {
		slog.Warn("rakip listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, competitors)
}

// UpdateBrandCompetitors handles PUT /v1/workspaces/{ws}/brands/{brandId}/competitors
// Kullanıcı tanımlı rakipleri günceller (FR-B1).
func (h *Handler) UpdateBrandCompetitors(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandId")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	var req struct {
		Competitors []string `json:"competitors"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	// Markanın tenant'a ait olduğunu doğrula
	var brandExists bool
	if err := h.pool.QueryRow(r.Context(), `
		SELECT EXISTS(SELECT 1 FROM config.brands
			WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3)
	`, brandID, workspaceID, tenantID).Scan(&brandExists); err != nil || !brandExists {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
		return
	}

	// Transaction başlat
	tx, err := h.pool.Begin(r.Context())
	if err != nil {
		slog.Error("transaction başlatma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "işlem başlatılamadı")
		return
	}
	rollback := func() { _ = tx.Rollback(r.Context()) }

	// Mevcut rakipleri sil (DELETE + INSERT = replace)
	if _, err = tx.Exec(r.Context(), `
		DELETE FROM config.brand_competitors
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID); err != nil {
		rollback()
		slog.Error("rakip silme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "rakip güncellenemedi")
		return
	}

	// Yeni rakipleri doğrula ve ekle
	for _, compID := range req.Competitors {
		if compID == "" || compID == brandID {
			continue
		}
		var exists bool
		if err = tx.QueryRow(r.Context(), `
			SELECT EXISTS(SELECT 1 FROM config.brands
				WHERE id = $1 AND tenant_id = $2 AND is_active = true)
		`, compID, tenantID).Scan(&exists); err != nil || !exists {
			rollback()
			httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{
				"error": fmt.Sprintf("rakip bulunamadı: %s", compID),
			})
			return
		}
		if _, err = tx.Exec(r.Context(), `
			INSERT INTO config.brand_competitors (id, brand_id, competitor_id, tenant_id)
			VALUES (gen_random_uuid()::text, $1, $2, $3)
			ON CONFLICT (brand_id, competitor_id) DO NOTHING
		`, brandID, compID, tenantID); err != nil {
			rollback()
			slog.Error("rakip ekleme hatası", "competitor", compID, "error", err)
			httputil.WriteError(w, http.StatusInternalServerError, "rakip eklenemedi")
			return
		}
	}

	if err = tx.Commit(r.Context()); err != nil {
		rollback()
		slog.Error("transaction commit hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "işlem tamamlanamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"status":      "updated",
		"brand_id":    brandID,
		"competitors": req.Competitors,
	})
}

func (h *Handler) GetSetupStatus(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())

	var checks struct {
		BrandCount       int `json:"brand_count"`
		PanelCount       int `json:"panel_count"`
		PromptSetCount   int `json:"prompt_set_count"`
		MeasurementCount int `json:"measurement_count"`
	}

	_ = h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM config.brands WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.BrandCount)
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM config.panels WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.PanelCount)
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM config.prompt_sets WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.PromptSetCount)
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM measure.scores WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.MeasurementCount)

	steps := []map[string]interface{}{
		{"key": "brand", "label": "Marka Ekle", "done": checks.BrandCount > 0},
		{"key": "panel", "label": "Panel Oluştur", "done": checks.PanelCount > 0},
		{"key": "prompt_set", "label": "Prompt Seti Oluştur", "done": checks.PromptSetCount > 0},
		{"key": "measurement", "label": "İlk Ölçümü Çalıştır", "done": checks.MeasurementCount > 0},
	}

	allDone := checks.BrandCount > 0 && checks.PanelCount > 0 && checks.PromptSetCount > 0 && checks.MeasurementCount > 0

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"setup_complete": allDone,
		"steps":          steps,
	})
}

// ListWorkspacePanorama handles GET /v1/tenant/panorama
// H5: Ajans görünümü — tüm workspace'lerin son skor özetini döndürür.
func (h *Handler) ListWorkspacePanorama(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT w.id, w.name,
			COALESCE(s.score_value, 0) AS avg_score,
			COALESCE(s.brand_count, 0) AS brand_count,
			COALESCE(s.measurement_count, 0) AS measurement_count,
			w.archived_at IS NOT NULL AS archived,
			w.created_at
		FROM config.workspaces w
		LEFT JOIN LATERAL (
			SELECT
				AVG(s2.value) AS score_value,
				COUNT(DISTINCT s2.brand_id) AS brand_count,
				COUNT(*) AS measurement_count
			FROM measure.scores s2
			WHERE s2.workspace_id = w.id AND s2.tenant_id = w.tenant_id
		) s ON true
		WHERE w.tenant_id = $1
		ORDER BY w.created_at DESC
	`, tenantID)
	if err != nil {
		slog.Error("panorama sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"workspaces": []interface{}{}})
		return
	}
	defer rows.Close()

	type workspaceSummary struct {
		ID               string  `json:"id"`
		Name             string  `json:"name"`
		AvgScore         float64 `json:"avg_score"`
		BrandCount       int     `json:"brand_count"`
		MeasurementCount int     `json:"measurement_count"`
		Archived         bool    `json:"archived"`
		CreatedAt        string  `json:"created_at"`
	}

	workspaces := make([]workspaceSummary, 0)
	for rows.Next() {
		var ws workspaceSummary
		var createdAt time.Time
		if err := rows.Scan(&ws.ID, &ws.Name, &ws.AvgScore, &ws.BrandCount, &ws.MeasurementCount, &ws.Archived, &createdAt); err != nil {
			slog.Warn("panorama satır okuma hatası", "error", err)
			continue
		}
		ws.CreatedAt = createdAt.Format(time.RFC3339)
		workspaces = append(workspaces, ws)
	}

	if rows.Err() != nil {
		slog.Warn("panorama rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"workspaces": workspaces})
}
