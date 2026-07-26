// Package audit provides handlers and logic for audit functionality.
package audit

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for audit HTTP handlers.
type Handler struct {
	pool    dbiface.DB
	rawPool *db.Pool
	svc     Service
}

// NewHandler creates a new audit handler with the given DB interface.
// The rawPool parameter is required for service constructors that need *db.Pool.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new audit handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool:    dbiface.NewAdapter(pool),
		rawPool: pool,
		svc:     NewService(pool),
	}
}

// AuditRequest is the request body for triggering an audit.
type AuditRequest struct {
	BrandID    string `json:"brand_id"`
	BrandName  string `json:"brand_name"`
	WebsiteURL string `json:"website_url"`
}

// RunAudit handles POST /v1/workspaces/{ws}/audit
// Triggers a site audit for the given brand.
func (h *Handler) RunAudit(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req AuditRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.BrandID == "" || req.WebsiteURL == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id ve website_url zorunludur"})
		return
	}

	// Marka doğrulama
	var brandName string
	if req.BrandName != "" {
		brandName = req.BrandName
	} else {
		err := h.pool.QueryRow(r.Context(), `
			SELECT name FROM config.brands
			WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3 AND is_active = true
		`, req.BrandID, workspaceID, tenantID).Scan(&brandName)
		if err != nil {
			slog.Error("audit marka sorgu hatası", "error", err)
			httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
			return
		}
	}

	result, err := h.svc.Audit(req.BrandID, brandName, req.WebsiteURL)
	if err != nil {
		slog.Error("audit hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "denetim başarısız"})
		return
	}

	// Sonucu workspace/tenant context ile zenginleştir
	result.WorkspaceID = workspaceID
	result.TenantID = tenantID

	// Audit sonucunu DB'ye kaydet (RLS için önce workspace/tenant set edilmeli)
	if err := h.svc.Save(r.Context(), result); err != nil {
		slog.Error("audit kaydetme hatası", "error", err)
		// Kaydetme başarısız olsa bile sonucu döndür
	}

	slog.Info("site denetimi tamamlandı",
		"brand", brandName,
		"score", result.OverallScore,
		"issues", len(result.Issues),
	)

	httputil.WriteJSON(w, http.StatusOK, result)
}

// GetFindingsCatalog handles GET /v1/workspaces/{ws}/audit/findings?brand_id=xxx
// H6: Kategorize edilmiş bulgu kataloğu döndürür.
func (h *Handler) GetFindingsCatalog(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	var robotsJSON, botJSON, ssrJSON, ssrfJSON, issuesJSON string
	var overallScore float64
	err := h.pool.QueryRow(r.Context(), `
		SELECT COALESCE(robots_txt::text, '{}'), COALESCE(bot_access::text, '{}'),
			COALESCE(ssr::text, '{}'), COALESCE(ssrf::text, '{}'),
			COALESCE(issues::text, '[]'), overall_score
		FROM governance.audit_results
		WHERE brand_id = $1 AND workspace_id = $2 AND tenant_id = $3
		ORDER BY created_at DESC LIMIT 1
	`, brandID, workspaceID, tenantID).Scan(&robotsJSON, &botJSON, &ssrJSON, &ssrfJSON, &issuesJSON, &overallScore)
	if err != nil {
		slog.Debug("audit bulgu bulunamadı", "brand_id", brandID)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
			"brand_id": brandID,
			"catalog":  []interface{}{},
			"summary":  map[string]int{"total": 0, "critical": 0, "high": 0, "medium": 0, "low": 0},
		})
		return
	}

	// Parse issues
	var issues []struct {
		Severity       string `json:"severity"`
		Category       string `json:"category"`
		Title          string `json:"title"`
		Detail         string `json:"detail"`
		Recommendation string `json:"recommendation,omitempty"`
	}
	if err := json.Unmarshal([]byte(issuesJSON), &issues); err != nil {
		slog.Warn("bulgu JSON çözümleme hatası", "error", err)
	}

	if issues == nil {
		issues = []struct {
			Severity       string `json:"severity"`
			Category       string `json:"category"`
			Title          string `json:"title"`
			Detail         string `json:"detail"`
			Recommendation string `json:"recommendation,omitempty"`
		}{}
	}

	// Sonuçları kategorize et
	categorized := map[string][]interface{}{
		"robots_txt": {},
		"bot_access": {},
		"ssr":        {},
		"ssrf":       {},
	}

	// issues'dan kategorize et
	for _, iss := range issues {
		item := map[string]interface{}{
			"title":          iss.Title,
			"detail":         iss.Detail,
			"severity":       iss.Severity,
			"recommendation": iss.Recommendation,
		}
		if _, ok := categorized[iss.Category]; ok {
			categorized[iss.Category] = append(categorized[iss.Category], item)
		} else {
			categorized["ssr"] = append(categorized["ssr"], item)
		}
	}

	summary := map[string]int{"total": 0, "critical": 0, "high": 0, "medium": 0, "low": 0}
	for _, iss := range issues {
		summary["total"]++
		if _, ok := summary[iss.Severity]; ok {
			summary[iss.Severity]++
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"brand_id":      brandID,
		"overall_score": overallScore,
		"summary":       summary,
		"catalog":       categorized,
	})
}

// ListAuditTrail handles GET /v1/admin/audit-trail
// T3: Yöneticiye görüntülenebilir denetim kaydı.
func (h *Handler) ListAuditTrail(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	eventType := r.URL.Query().Get("event_type")
	resourceType := r.URL.Query().Get("resource_type")
	limit := 100

	// LIMIT+1 pattern for has_more
	rows, err := h.pool.Query(r.Context(), `
		SELECT id, COALESCE(user_id, ''), event_type, resource_type,
			COALESCE(resource_id, ''), action, COALESCE(metadata::text, '{}'),
			COALESCE(ip_address, ''), created_at
		FROM governance.audit_log
		WHERE tenant_id = $1
			AND ($2 = '' OR event_type = $2)
			AND ($3 = '' OR resource_type = $3)
		ORDER BY created_at DESC
		LIMIT $4
	`, tenantID, eventType, resourceType, limit+1)
	if err != nil {
		slog.Error("denetim kaydı sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"entries": []interface{}{}, "has_more": false})
		return
	}
	defer rows.Close()

	type auditEntry struct {
		ID           string `json:"id"`
		UserID       string `json:"user_id"`
		EventType    string `json:"event_type"`
		ResourceType string `json:"resource_type"`
		ResourceID   string `json:"resource_id"`
		Action       string `json:"action"`
		Metadata     string `json:"metadata,omitempty"`
		IPAddress    string `json:"ip_address,omitempty"`
		CreatedAt    string `json:"created_at"`
	}

	entries := make([]auditEntry, 0)
	for rows.Next() {
		var e auditEntry
		var createdAt time.Time
		if err := rows.Scan(&e.ID, &e.UserID, &e.EventType, &e.ResourceType, &e.ResourceID, &e.Action, &e.Metadata, &e.IPAddress, &createdAt); err != nil {
			slog.Warn("denetim satır okuma hatası", "error", err)
			continue
		}
		e.CreatedAt = createdAt.Format(time.RFC3339)
		entries = append(entries, e)
	}

	hasMore := len(entries) > limit
	if hasMore {
		entries = entries[:limit]
	}

	if rows.Err() != nil {
		slog.Warn("denetim kaydı rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entries":  entries,
		"has_more": hasMore,
		"count":    len(entries),
	})
}

// ExportAuditTrail handles GET /v1/admin/audit-trail/export
// T3: Denetim kaydını CSV olarak dışa aktarır.
func (h *Handler) ExportAuditTrail(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT COALESCE(user_id, 'system'), event_type, resource_type,
			COALESCE(resource_id, ''), action, COALESCE(ip_address, ''), created_at
		FROM governance.audit_log
		WHERE tenant_id = $1
		ORDER BY created_at DESC
		LIMIT 1000
	`, tenantID)
	if err != nil {
		slog.Error("denetim dışa aktarma sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "dışa aktarılamadı")
		return
	}
	defer rows.Close()

	w.Header().Set("Content-Type", "text/csv")
	w.Header().Set("Content-Disposition", "attachment; filename=\"audit-trail.csv\"")

	_, _ = w.Write([]byte("user_id,event_type,resource_type,resource_id,action,ip_address,created_at\n"))
	for rows.Next() {
		var userID, eventType, resourceType, resourceID, action, ipAddr string
		var createdAt time.Time
		if err := rows.Scan(&userID, &eventType, &resourceType, &resourceID, &action, &ipAddr, &createdAt); err != nil {
			slog.Warn("audit export satır okuma hatası", "error", err)
			continue
		}
		fmt.Fprintf(w, "%s,%s,%s,%s,%s,%s,%s\n",
			userID, eventType, resourceType, resourceID, action, ipAddr, createdAt.Format(time.RFC3339))
	}

	if rows.Err() != nil {
		slog.Warn("audit export rows iterasyon hatası", "error", rows.Err())
	}
}
