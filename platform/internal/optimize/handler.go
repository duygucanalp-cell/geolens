package optimize

import (
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

func (h *Handler) ListRecommendations(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 100 {
		limit = 20
	}
	statusFilter := r.URL.Query().Get("status")
	categoryFilter := r.URL.Query().Get("category")

	// LIMIT+1 pattern for has_more
	query := `SELECT id, category, title, description, impact, effort, status, score_potential, created_at
	          FROM optimize.recommendations WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	if statusFilter == "pending" || statusFilter == "implemented" || statusFilter == "dismissed" {
		query += ` AND status = $` + strconv.Itoa(paramIdx)
		args = append(args, statusFilter)
		paramIdx++
	}
	if categoryFilter != "" {
		query += ` AND category = $` + strconv.Itoa(paramIdx)
		args = append(args, categoryFilter)
		paramIdx++
	}
	query += ` ORDER BY score_potential DESC LIMIT $` + strconv.Itoa(paramIdx)
	args = append(args, limit+1)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("optimizasyon listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"data": []interface{}{}, "has_more": false})
		return
	}
	defer rows.Close()

	type rec struct {
		ID             string  `json:"id"`
		Category       string  `json:"category"`
		Title          string  `json:"title"`
		Description    string  `json:"description"`
		Impact         string  `json:"impact"`
		Effort         string  `json:"effort"`
		Status         string  `json:"status"`
		ScorePotential float64 `json:"score_potential"`
		CreatedAt      string  `json:"created_at"`
	}
	var recs []rec
	for rows.Next() {
		var r rec
		var ts string
		if err := rows.Scan(&r.ID, &r.Category, &r.Title, &r.Description, &r.Impact, &r.Effort, &r.Status, &r.ScorePotential, &ts); err != nil {
			slog.Warn("optimizasyon satır okuma hatası", "error", err)
			continue
		}
		r.CreatedAt = ts
		recs = append(recs, r)
	}

	hasMore := len(recs) > limit
	if hasMore {
		recs = recs[:limit]
	}

	if recs == nil {
		recs = []rec{}
	}

	if rows.Err() != nil {
		slog.Warn("optimizasyon listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"data":     recs,
		"has_more": hasMore,
	})
}

func (h *Handler) GenerateRecommendations(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	input := struct {
		BrandID  string `json:"brand_id"`
		AutoSave bool   `json:"auto_save"`
	}{}
	json.NewDecoder(r.Body).Decode(&input)

	// Analiz için mevcut skorları kontrol et
	var scoreCount int
	h.pool.QueryRow(r.Context(), `SELECT COUNT(*) FROM measure.scores WHERE tenant_id = $1`, tenantID).Scan(&scoreCount)

	recommendations := h.analyze(scoreCount)

	created := []map[string]interface{}{}
	for _, rec := range recommendations {
		recID := id.New()
		now := time.Now().UTC()

		if input.AutoSave {
			_, err := h.pool.Exec(r.Context(), `
				INSERT INTO optimize.recommendations (id, tenant_id, category, title, description, impact, effort, score_potential, created_at)
				VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			`, recID, tenantID, rec["category"], rec["title"], rec["description"],
				rec["impact"], rec["effort"], rec["score_potential"], now)
			if err != nil {
				slog.Warn("öneri kaydedilemedi", "error", err)
				continue
			}
			rec["id"] = recID
			rec["status"] = "pending"
		}
		created = append(created, rec)
	}

	slog.Info("optimizasyon önerileri oluşturuldu", "count", len(created), "auto_save", input.AutoSave)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"recommendations": created,
		"count":           len(created),
	})
}

func (h *Handler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	recID := chi.URLParam(r, "recId")

	var input struct {
		Status string `json:"status"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.Status != "implemented" && input.Status != "dismissed" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz durum: implemented veya dismissed olmalı"})
		return
	}

	result, err := h.pool.Exec(r.Context(), `
		UPDATE optimize.recommendations SET status = $1, updated_at = NOW()
		WHERE id = $2 AND tenant_id = $3
	`, input.Status, recID, tenantID)
	if err != nil || result.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "öneri bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"id": recID, "status": input.Status})
}

func (h *Handler) analyze(scoreCount int) []map[string]interface{} {
	recs := []map[string]interface{}{}

	// Öneri 1: Daha sık ölçüm
	if scoreCount < 5 {
		recs = append(recs, map[string]interface{}{
			"category": "measurement", "title": "Ölçüm sıklığını artırın",
			"description": "Daha sık ölçüm, trend verisi ve erken uyarı sağlar. Haftada en az 1 ölçüm önerilir.",
			"impact":      "high", "effort": "low", "score_potential": 15.0,
		})
	}

	// Öneri 2: Çoklu engine kullanımı
	recs = append(recs, map[string]interface{}{
		"category": "engine", "title": "Çoklu AI motoru kullanın",
		"description": "Farklı motorlardan (Perplexity, ChatGPT, Gemini) veri almak görünürlük skorunun güvenilirliğini artırır.",
		"impact":      "high", "effort": "medium", "score_potential": 20.0,
	})

	// Öneri 3: Prompt optimizasyonu
	recs = append(recs, map[string]interface{}{
		"category": "prompt", "title": "Prompt'ları optimize edin",
		"description": "Marka adı, sektör ve kaynak talebi içeren prompt'lar daha doğru sonuçlar üretir.",
		"impact":      "medium", "effort": "medium", "score_potential": 12.0,
	})

	// Öneri 4: Kaynak çeşitliliği
	recs = append(recs, map[string]interface{}{
		"category": "citation", "title": "Kaynak çeşitliliğini artırın",
		"description": "Web sitesi, sosyal medya ve haber kaynaklarında marka varlığınızı güçlendirin.",
		"impact":      "medium", "effort": "high", "score_potential": 18.0,
	})

	return recs
}
