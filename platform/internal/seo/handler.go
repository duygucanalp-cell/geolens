// Package seo provides SEO platform integrations (FR-B8).
// Google Search Console ve GA4 bağlantılarını OAuth2 ile yönetir.
package seo

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

const (
	googleAuthURL   = "https://accounts.google.com/o/oauth2/v2/auth"
	googleTokenURL  = "https://oauth2.googleapis.com/token"
	scSearchConsole = "https://www.googleapis.com/auth/webmasters.readonly"
	scGA4           = "https://www.googleapis.com/auth/analytics.readonly"
)

// Handler holds dependencies for SEO integration handlers.
type Handler struct {
	pool      dbiface.DB
	clientID  string
	clientSec string
	baseURL   string
}

// NewHandler creates a new SEO handler.
func NewHandler(pool dbiface.DB, clientID, clientSecret, baseURL string) *Handler {
	return &Handler{
		pool:      pool,
		clientID:  clientID,
		clientSec: clientSecret,
		baseURL:   baseURL,
	}
}

// NewProductionHandler creates a new SEO handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool, clientID, clientSecret, baseURL string) *Handler {
	return NewHandler(dbiface.NewAdapter(pool), clientID, clientSecret, baseURL)
}

// ListConnections handles GET /v1/workspaces/{ws}/seo/connections
func (h *Handler) ListConnections(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, platform, email, is_active, last_synced_at, created_at
		FROM seo.connections
		WHERE tenant_id = $1 AND workspace_id = $2
		ORDER BY platform
	`, tenantID, workspaceID)
	if err != nil {
		slog.Debug("seo connection listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type connectionRow struct {
		ID         string     `json:"id"`
		Platform   string     `json:"platform"`
		Email      string     `json:"email"`
		IsActive   bool       `json:"is_active"`
		LastSynced *time.Time `json:"last_synced_at,omitempty"`
		CreatedAt  time.Time  `json:"created_at"`
	}

	conns := make([]connectionRow, 0)
	for rows.Next() {
		var c connectionRow
		if err := rows.Scan(&c.ID, &c.Platform, &c.Email, &c.IsActive, &c.LastSynced, &c.CreatedAt); err != nil {
			slog.Warn("seo connection satır okuma hatası", "error", err)
			continue
		}
		conns = append(conns, c)
	}

	httputil.WriteJSON(w, http.StatusOK, conns)
}

// GetAuthURL handles GET /v1/workspaces/{ws}/seo/auth-url?platform=search_console|ga4
// Google OAuth2 consent ekranına yönlendirme URL'si döndürür.
func (h *Handler) GetAuthURL(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	platform := r.URL.Query().Get("platform")

	if platform != "search_console" && platform != "ga4" {
		httputil.WriteError(w, http.StatusBadRequest, "platform search_console veya ga4 olmalıdır")
		return
	}

	if h.clientID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "Google OAuth yapılandırılmamış")
		return
	}

	// State token: CSRF koruması için random + tenant/workspace bilgisi
	stateBytes := make([]byte, 16)
	if _, err := rand.Read(stateBytes); err != nil {
		slog.Error("state token oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "durum kodu oluşturulamadı")
		return
	}
	stateToken := hex.EncodeToString(stateBytes)

	// State'i geçici olarak sakla (5 dk TTL)
	stateKey := fmt.Sprintf("seo:state:%s", stateToken)
	stateValue := fmt.Sprintf("%s|%s|%s", tenantID, workspaceID, platform)
	// Redis yoksa memory'de tutamayız, geçici olarak DB kullan
	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO governance.cache_store (cache_key, cache_value, expires_at)
		VALUES ($1, $2, now() + interval '5 minutes')
		ON CONFLICT (cache_key) DO UPDATE SET cache_value = $2, expires_at = now() + interval '5 minutes'
	`, stateKey, stateValue)
	if err != nil {
		slog.Warn("state token kaydetme hatası (non-fatal)", "error", err)
	}

	scopes := scSearchConsole
	if platform == "ga4" {
		scopes = scGA4
	}

	redirectURI := h.baseURL + "/v1/workspaces/" + workspaceID + "/seo/callback"
	authURL := fmt.Sprintf("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&state=%s",
		googleAuthURL, h.clientID, url.QueryEscape(redirectURI), url.QueryEscape(scopes), stateToken)

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"auth_url":    authURL,
		"state_token": stateToken,
	})
}

// HandleCallback handles GET /v1/workspaces/{ws}/seo/callback?code=...&state=...
// Google OAuth2 callback'ini işler.
// Bu endpoint JWT auth middleware DIŞINDA çalışır (Google redirect'i token taşımaz).
// Tenant ve workspace bilgisi state token'dan çözülür.
func (h *Handler) HandleCallback(w http.ResponseWriter, r *http.Request) {
	workspaceID := chi.URLParam(r, "ws")
	code := r.URL.Query().Get("code")
	state := r.URL.Query().Get("state")

	if code == "" || state == "" {
		httputil.WriteError(w, http.StatusBadRequest, "code ve state parametreleri gerekli")
		return
	}

	if workspaceID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "workspace ID gerekli")
		return
	}

	// State token'ı doğrula — içinden tenantID, workspaceID ve platform'u çöz
	var stateValue string
	err := h.pool.QueryRow(r.Context(), `
		SELECT cache_value FROM governance.cache_store
		WHERE cache_key = $1 AND expires_at > now()
	`, "seo:state:"+state).Scan(&stateValue)
	if err != nil {
		slog.Warn("geçersiz/expired state token", "state", state[:8]+"...")
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz state token")
		return
	}

	parts := strings.SplitN(stateValue, "|", 3)
	if len(parts) != 3 {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz state")
		return
	}
	tenantID, wsID, platform := parts[0], parts[1], parts[2]

	if wsID != workspaceID {
		httputil.WriteError(w, http.StatusBadRequest, "workspace eşleşmez")
		return
	}

	// state token'ını cache'den temizle
	if _, err := h.pool.Exec(r.Context(), `DELETE FROM governance.cache_store WHERE cache_key = $1`, "seo:state:"+state); err != nil {
		slog.Warn("state token temizlenemedi", "error", err)
	}

	// Authorization code'u token ile değiştir
	redirectURI := h.baseURL + "/v1/workspaces/" + workspaceID + "/seo/callback"
	token, err := h.exchangeCode(r.Context(), code, redirectURI)
	if err != nil {
		slog.Error("token exchange hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "token alınamadı")
		return
	}

	// Token'ı veritabanına kaydet
	_, err = h.pool.Exec(r.Context(), `
		INSERT INTO seo.connections (id, tenant_id, workspace_id, platform, email, access_token, refresh_token, token_expires_at)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, $5, $6, $7)
		ON CONFLICT (tenant_id, platform) DO UPDATE SET
			email = EXCLUDED.email,
			access_token = EXCLUDED.access_token,
			refresh_token = EXCLUDED.refresh_token,
			token_expires_at = EXCLUDED.token_expires_at,
			is_active = true,
			updated_at = now()
	`, tenantID, workspaceID, platform, token.Email, token.AccessToken, token.RefreshToken, token.ExpiresAt)
	if err != nil {
		slog.Error("token kaydetme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "bağlantı kaydedilemedi")
		return
	}

	slog.Info("seo platform bağlandı", "tenant", tenantID[:8], "platform", platform, "email", token.Email)

	// Başarılı bağlantı — frontend'i yönlendir
	http.Redirect(w, r, h.baseURL+"/?seo=connected&platform="+platform, http.StatusTemporaryRedirect)
}

// exchangeCode exchanges an authorization code for OAuth2 tokens.
func (h *Handler) exchangeCode(ctx context.Context, code, redirectURI string) (*tokenResponse, error) {
	data := url.Values{
		"code":          {code},
		"client_id":     {h.clientID},
		"client_secret": {h.clientSec},
		"redirect_uri":  {redirectURI},
		"grant_type":    {"authorization_code"},
	}

	req, err := http.NewRequestWithContext(ctx, "POST", googleTokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return nil, fmt.Errorf("token istek oluşturma: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("token isteği: %w", err)
	}
	defer resp.Body.Close()

	var tr tokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tr); err != nil {
		return nil, fmt.Errorf("token yanıt ayrıştırma: %w", err)
	}

	if tr.AccessToken == "" {
		return nil, fmt.Errorf("token yanıtında access_token yok: %+v", tr)
	}

	if tr.ExpiresIn == 0 {
		tr.ExpiresIn = 3600
	}
	tr.ExpiresAt = time.Now().Add(time.Duration(tr.ExpiresIn) * time.Second)

	// Token info endpoint'inden email al
	tr.Email = h.getTokenEmail(ctx, tr.AccessToken)

	return &tr, nil
}

// getTokenEmail, access token ile Google tokeninfo endpoint'inden email bilgisini alır.
func (h *Handler) getTokenEmail(ctx context.Context, accessToken string) string {
	req, err := http.NewRequestWithContext(ctx, "GET", "https://www.googleapis.com/oauth2/v2/tokeninfo?access_token="+accessToken, nil)
	if err != nil {
		return ""
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()

	var info struct {
		Email string `json:"email"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&info); err != nil {
		return ""
	}
	return info.Email
}

// Disconnect handles DELETE /v1/workspaces/{ws}/seo/connections/{platform}
func (h *Handler) Disconnect(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	platform := chi.URLParam(r, "platform")

	_, err := h.pool.Exec(r.Context(), `
		DELETE FROM seo.connections
		WHERE tenant_id = $1 AND workspace_id = $2 AND platform = $3
	`, tenantID, workspaceID, platform)
	if err != nil {
		slog.Error("seo bağlantı silme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "bağlantı kaldırılamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "disconnected", "platform": platform})
}

// GetSearchConsoleData handles GET /v1/workspaces/{ws}/seo/search-console?brand_id=xxx
func (h *Handler) GetSearchConsoleData(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT scd.query, scd.clicks, scd.impressions, scd.ctr, scd.avg_position, scd.measured_at
		FROM seo.search_console_data scd
		WHERE scd.tenant_id = $1 AND ($2 = '' OR scd.brand_id = $2)
		ORDER BY scd.measured_at DESC, scd.clicks DESC
		LIMIT 100
	`, tenantID, brandID)
	if err != nil {
		slog.Debug("search console veri hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type scRow struct {
		Query       string  `json:"query"`
		Clicks      int64   `json:"clicks"`
		Impressions int64   `json:"impressions"`
		CTR         float64 `json:"ctr"`
		AvgPosition float64 `json:"avg_position"`
		MeasuredAt  string  `json:"measured_at"`
	}

	data := make([]scRow, 0)
	for rows.Next() {
		var r scRow
		var measuredAt time.Time
		if err := rows.Scan(&r.Query, &r.Clicks, &r.Impressions, &r.CTR, &r.AvgPosition, &measuredAt); err != nil {
			continue
		}
		r.MeasuredAt = measuredAt.Format("2006-01-02")
		data = append(data, r)
	}

	httputil.WriteJSON(w, http.StatusOK, data)
}

// GetGA4Data handles GET /v1/workspaces/{ws}/seo/ga4?brand_id=xxx
func (h *Handler) GetGA4Data(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT gd.page_views, gd.sessions, gd.bounce_rate, gd.avg_session_duration, gd.measured_at
		FROM seo.ga4_data gd
		WHERE gd.tenant_id = $1 AND ($2 = '' OR gd.brand_id = $2)
		ORDER BY gd.measured_at DESC
		LIMIT 100
	`, tenantID, brandID)
	if err != nil {
		slog.Debug("ga4 veri hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type ga4Row struct {
		PageViews          int64   `json:"page_views"`
		Sessions           int64   `json:"sessions"`
		BounceRate         float64 `json:"bounce_rate"`
		AvgSessionDuration float64 `json:"avg_session_duration"`
		MeasuredAt         string  `json:"measured_at"`
	}

	data := make([]ga4Row, 0)
	for rows.Next() {
		var r ga4Row
		var measuredAt time.Time
		if err := rows.Scan(&r.PageViews, &r.Sessions, &r.BounceRate, &r.AvgSessionDuration, &measuredAt); err != nil {
			continue
		}
		r.MeasuredAt = measuredAt.Format("2006-01-02")
		data = append(data, r)
	}

	httputil.WriteJSON(w, http.StatusOK, data)
}

// tokenResponse represents Google OAuth2 token response.
type tokenResponse struct {
	AccessToken  string    `json:"access_token"`
	RefreshToken string    `json:"refresh_token,omitempty"`
	ExpiresIn    int       `json:"expires_in"`
	Scope        string    `json:"scope,omitempty"`
	TokenType    string    `json:"token_type,omitempty"`
	Email        string    `json:"email,omitempty"`
	ExpiresAt    time.Time `json:"-"`
}
