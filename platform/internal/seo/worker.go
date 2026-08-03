// Package seo provides SEO platform integrations (FR-B8) including background sync.
package seo

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/geolens/platform/platform/db"
)

// SyncWorker periodically syncs Search Console and GA4 data for connected accounts.
type SyncWorker struct {
	pool      *db.Pool
	clientID  string
	clientSec string
	interval  time.Duration
}

// NewSyncWorker creates a new SEO sync worker.
func NewSyncWorker(pool *db.Pool, clientID, clientSecret string, interval time.Duration) *SyncWorker {
	return &SyncWorker{
		pool:      pool,
		clientID:  clientID,
		clientSec: clientSecret,
		interval:  interval,
	}
}

// Start begins the SEO data sync loop.
func (w *SyncWorker) Start(ctx context.Context) {
	slog.Info("seo veri senkronizasyon işçisi başlatıldı", "interval", w.interval)
	ticker := time.NewTicker(w.interval)
	defer ticker.Stop()

	// İlk çalıştırmada hemen sync yap
	w.syncAll(ctx)

	for {
		select {
		case <-ctx.Done():
			slog.Info("seo veri senkronizasyon işçisi durduruldu")
			return
		case <-ticker.C:
			w.syncAll(ctx)
		}
	}
}

// syncAll iterates all active SEO connections and syncs data (Search Console + GA4).
func (w *SyncWorker) syncAll(ctx context.Context) {
	ctx, cancel := context.WithTimeout(ctx, 5*time.Minute)
	defer cancel()

	// Sync Search Console connections
	w.syncConnections(ctx, "search_console")

	// Sync GA4 connections
	w.syncConnections(ctx, "ga4")
}

// syncConnections processes all active connections for a given platform.
func (w *SyncWorker) syncConnections(ctx context.Context, platform string) {
	rows, err := w.pool.Query(ctx, `
		SELECT id, tenant_id, workspace_id, platform, access_token, refresh_token, token_expires_at
		FROM seo.connections
		WHERE is_active = true AND platform = $1
	`, platform)
	if err != nil {
		slog.Warn("seo sync: bağlantı sorgu hatası", "platform", platform, "error", err)
		return
	}
	defer rows.Close()

	for rows.Next() {
		var c connRow
		if err := rows.Scan(&c.ID, &c.TenantID, &c.WorkspaceID, &c.Platform, &c.AccessToken, &c.RefreshToken, &c.TokenExpiresAt); err != nil {
			slog.Warn("seo sync: satır okuma hatası", "error", err)
			continue
		}

		// Token süresi dolmuşsa yenile
		accessToken := c.AccessToken
		if time.Now().After(c.TokenExpiresAt) && c.RefreshToken != "" {
			newToken, err := w.refreshAccessToken(ctx, c.RefreshToken)
			if err != nil {
				slog.Error("seo sync: token yenileme hatası", "conn", c.ID, "error", err)
				continue
			}
			accessToken = newToken.AccessToken

			_, _ = w.pool.Exec(ctx, `
				UPDATE seo.connections
				SET access_token = $1, refresh_token = COALESCE(NULLIF($2, ''), refresh_token),
				    token_expires_at = $3, updated_at = now()
				WHERE id = $4
			`, newToken.AccessToken, newToken.RefreshToken, newToken.ExpiresAt, c.ID)
		}

		switch platform {
		case "search_console":
			w.syncSearchConsole(ctx, c, accessToken)
		case "ga4":
			w.syncGA4(ctx, c, accessToken)
		}

		// Son sync zamanını güncelle
		_, _ = w.pool.Exec(ctx, `UPDATE seo.connections SET last_synced_at = now() WHERE id = $1`, c.ID)
	}

	if rows.Err() != nil {
		slog.Warn("seo sync: connection rows iterasyon hatası", "platform", platform, "error", rows.Err())
	}
}

// syncSearchConsole processes a single Search Console connection.
func (w *SyncWorker) syncSearchConsole(ctx context.Context, c connRow, accessToken string) {
	brandRows, err := w.pool.Query(ctx, `
		SELECT id, COALESCE(website_url, '') FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
	`, c.WorkspaceID, c.TenantID)
	if err != nil {
		slog.Warn("seo sync: marka sorgu hatası", "workspace", c.WorkspaceID, "error", err)
		return
	}
	defer brandRows.Close()

	for brandRows.Next() {
		var brandID, siteURL string
		if err := brandRows.Scan(&brandID, &siteURL); err != nil {
			continue
		}
		if siteURL == "" {
			continue
		}
		if err := w.syncBrandData(ctx, c.TenantID, c.WorkspaceID, c.ID, brandID, siteURL, accessToken); err != nil {
			slog.Warn("seo sync: sc data hatası", "brand", brandID, "error", err)
		}
	}

	if brandRows.Err() != nil {
		slog.Warn("seo sync: brand rows iterasyon hatası", "error", brandRows.Err())
	}
}

// syncGA4 processes a single GA4 connection.
// Önce kullanıcının erişebildiği GA4 property'lerini keşfeder,
// ardından her brand için Analytics Data API'den trafik verilerini çeker.
func (w *SyncWorker) syncGA4(ctx context.Context, c connRow, accessToken string) {
	// GA4 property'lerini keşfet
	properties, err := w.discoverGA4Properties(ctx, accessToken)
	if err != nil {
		slog.Warn("seo sync: ga4 property keşif hatası", "error", err)
		return
	}
	if len(properties) == 0 {
		slog.Debug("seo sync: ga4 property bulunamadı", "conn", c.ID)
		return
	}

	// İlk property'yi kullan (veya domain eşleşmesi yap)
	propertyID := properties[0]

	brandRows, err := w.pool.Query(ctx, `
		SELECT id, COALESCE(website_url, '') FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
	`, c.WorkspaceID, c.TenantID)
	if err != nil {
		slog.Warn("seo sync: marka sorgu hatası", "workspace", c.WorkspaceID, "error", err)
		return
	}
	defer brandRows.Close()

	for brandRows.Next() {
		var brandID, siteURL string
		if err := brandRows.Scan(&brandID, &siteURL); err != nil {
			continue
		}
		if siteURL == "" {
			continue
		}

		if err := w.syncGA4Data(ctx, c.TenantID, c.WorkspaceID, c.ID, brandID, siteURL, propertyID, accessToken); err != nil {
			slog.Warn("seo sync: ga4 data hatası", "brand", brandID, "error", err)
		}
	}

	if brandRows.Err() != nil {
		slog.Warn("seo sync: brand rows iterasyon hatası", "error", brandRows.Err())
	}
}

// discoverGA4Properties gets available GA4 properties for the connected Google account.
func (w *SyncWorker) discoverGA4Properties(ctx context.Context, accessToken string) ([]string, error) {
	req, err := http.NewRequestWithContext(ctx, "GET",
		"https://analyticsadmin.googleapis.com/v1beta/accountSummaries?pageSize=50", nil)
	if err != nil {
		return nil, fmt.Errorf("http istek oluşturma: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("property listeleme: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("property listeleme hatası (HTTP %d)", resp.StatusCode)
	}

	var result struct {
		AccountSummaries []struct {
			PropertySummaries []struct {
				Property string `json:"property"` // format: "properties/123456789"
			} `json:"propertySummaries"`
		} `json:"accountSummaries"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("yanıt ayrıştırma: %w", err)
	}

	props := make([]string, 0)
	for _, acct := range result.AccountSummaries {
		for _, ps := range acct.PropertySummaries {
			if ps.Property != "" {
				props = append(props, ps.Property)
			}
		}
	}

	return props, nil
}

// syncGA4Data calls Google Analytics Data API for a single brand.
func (w *SyncWorker) syncGA4Data(ctx context.Context, tenantID, workspaceID, connID, brandID, siteURL, propertyID, accessToken string) error {
	// Query GA4 Data API for past 7 days
	reqBody := ga4RunReportRequest{
		DateRanges: []ga4DateRange{
			{StartDate: time.Now().AddDate(0, 0, -7).Format("2006-01-02"),
				EndDate: time.Now().Format("2006-01-02")},
		},
		Metrics: []ga4Metric{
			{Name: "screenPageViews"},
			{Name: "sessions"},
			{Name: "bounceRate"},
			{Name: "averageSessionDuration"},
		},
		Dimensions: []ga4Dimension{},
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("istek serileştirme: %w", err)
	}

	apiURL := fmt.Sprintf("https://analyticsdata.googleapis.com/v1beta/%s:runReport", propertyID)
	req, err := http.NewRequestWithContext(ctx, "POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("http istek oluşturma: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("ga4 api çağrısı: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusUnauthorized {
		return fmt.Errorf("yetkisiz erişim")
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("ga4 api hatası (HTTP %d)", resp.StatusCode)
	}

	var apiResp ga4RunReportResponse
	if err := json.NewDecoder(resp.Body).Decode(&apiResp); err != nil {
		return fmt.Errorf("yanıt ayrıştırma: %w", err)
	}

	if len(apiResp.Rows) == 0 {
		return nil
	}

	// İlk row'dan metrikleri al (dimensions yok, tek row gelir)
	row := apiResp.Rows[0]
	var pageViews, sessions int64
	var bounceRate, avgDuration float64

	for i, header := range apiResp.MetricHeaders {
		if i >= len(row.MetricValues) {
			break
		}
		switch header.Name {
		case "screenPageViews":
			pageViews = parseInt64(row.MetricValues[i].Value)
		case "sessions":
			sessions = parseInt64(row.MetricValues[i].Value)
		case "bounceRate":
			bounceRate = parseFloat(row.MetricValues[i].Value)
		case "averageSessionDuration":
			avgDuration = parseFloat(row.MetricValues[i].Value)
		}
	}

	today := time.Now().Format("2006-01-02")
	_, err = w.pool.Exec(ctx, `
		INSERT INTO seo.ga4_data
			(connection_id, tenant_id, brand_id, page_views, sessions, bounce_rate, avg_session_duration, measured_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8::date)
		ON CONFLICT (connection_id, brand_id, measured_at)
		DO UPDATE SET page_views = EXCLUDED.page_views, sessions = EXCLUDED.sessions,
		              bounce_rate = EXCLUDED.bounce_rate, avg_session_duration = EXCLUDED.avg_session_duration
	`, connID, tenantID, brandID, pageViews, sessions, bounceRate, avgDuration, today)
	if err != nil {
		return fmt.Errorf("veri kaydetme: %w", err)
	}

	slog.Debug("seo sync: ga4 brand verisi güncellendi",
		"brand", brandID, "page_views", pageViews, "sessions", sessions)
	return nil
}

// syncBrandData calls Search Console API for a single brand/site.
func (w *SyncWorker) syncBrandData(ctx context.Context, tenantID, workspaceID, connID, brandID, siteURL, accessToken string) error {
	// Google Search Console API requires URL-encoded site URL
	encodedURL := url.QueryEscape(siteURL)

	// Query the Search Analytics API for the past 7 days of data
	reqBody := searchAnalyticsRequest{
		StartDate:  time.Now().AddDate(0, 0, -7).Format("2006-01-02"),
		EndDate:    time.Now().Format("2006-01-02"),
		Dimensions: []string{"query"},
		RowLimit:   100,
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("istek serileştirme: %w", err)
	}

	apiURL := fmt.Sprintf("https://www.googleapis.com/webmasters/v3/sites/%s/searchAnalytics/query", encodedURL)
	req, err := http.NewRequestWithContext(ctx, "POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("http istek oluşturma: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("api çağrısı: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusUnauthorized {
		return fmt.Errorf("yetkisiz erişim (token expired?)")
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("api hatası (HTTP %d)", resp.StatusCode)
	}

	var apiResp searchAnalyticsResponse
	if err := json.NewDecoder(resp.Body).Decode(&apiResp); err != nil {
		return fmt.Errorf("yanıt ayrıştırma: %w", err)
	}

	// Results'u batch insert ile kaydet
	if len(apiResp.Rows) == 0 {
		return nil
	}

	today := time.Now().Format("2006-01-02")
	for _, row := range apiResp.Rows {
		query := ""
		if len(row.Keys) > 0 {
			query = row.Keys[0]
		}

		_, err := w.pool.Exec(ctx, `
			INSERT INTO seo.search_console_data
				(connection_id, tenant_id, brand_id, query, clicks, impressions, ctr, avg_position, measured_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::date)
			ON CONFLICT (connection_id, brand_id, query, measured_at)
			DO UPDATE SET clicks = EXCLUDED.clicks, impressions = EXCLUDED.impressions,
			              ctr = EXCLUDED.ctr, avg_position = EXCLUDED.avg_position
		`, connID, tenantID, brandID, query, row.Clicks, row.Impressions, row.CTR, row.AvgPosition, today)
		if err != nil {
			slog.Warn("seo sync: veri kaydetme hatası", "query", query, "error", err)
		}
	}

	slog.Debug("seo sync: brand verisi güncellendi",
		"brand", brandID, "rows", len(apiResp.Rows))
	return nil
}

// refreshAccessToken uses the refresh token to get a new access token.
func (w *SyncWorker) refreshAccessToken(ctx context.Context, refreshToken string) (*tokenResponse, error) {
	data := url.Values{
		"client_id":     {w.clientID},
		"client_secret": {w.clientSec},
		"refresh_token": {refreshToken},
		"grant_type":    {"refresh_token"},
	}

	req, err := http.NewRequestWithContext(ctx, "POST", googleTokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return nil, fmt.Errorf("refresh istek oluşturma: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("refresh isteği: %w", err)
	}
	defer resp.Body.Close()

	var tr tokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tr); err != nil {
		return nil, fmt.Errorf("refresh yanıt ayrıştırma: %w", err)
	}

	if tr.AccessToken == "" {
		return nil, fmt.Errorf("refresh yanıtı boş")
	}

	if tr.ExpiresIn == 0 {
		tr.ExpiresIn = 3600
	}
	tr.ExpiresAt = time.Now().Add(time.Duration(tr.ExpiresIn) * time.Second)

	return &tr, nil
}

// ---- Request/Response Types for Search Console API ----

type connRow struct {
	ID             string
	TenantID       string
	WorkspaceID    string
	Platform       string
	AccessToken    string
	RefreshToken   string
	TokenExpiresAt time.Time
}

type searchAnalyticsRequest struct {
	StartDate  string   `json:"startDate"`
	EndDate    string   `json:"endDate"`
	Dimensions []string `json:"dimensions"`
	RowLimit   int      `json:"rowLimit"`
}

type searchAnalyticsResponse struct {
	Rows []searchAnalyticsRow `json:"rows,omitempty"`
}

type searchAnalyticsRow struct {
	Keys        []string `json:"keys"`
	Clicks      int64    `json:"clicks"`
	Impressions int64    `json:"impressions"`
	CTR         float64  `json:"ctr"`
	AvgPosition float64  `json:"avgPosition"`
}

// ---- Request/Response Types for GA4 Data API ----

type ga4RunReportRequest struct {
	DateRanges []ga4DateRange `json:"dateRanges"`
	Metrics    []ga4Metric    `json:"metrics"`
	Dimensions []ga4Dimension `json:"dimensions,omitempty"`
}

type ga4DateRange struct {
	StartDate string `json:"startDate"`
	EndDate   string `json:"endDate"`
}

type ga4Metric struct {
	Name string `json:"name"`
}

type ga4Dimension struct {
	Name string `json:"name"`
}

type ga4RunReportResponse struct {
	Rows          []ga4Row    `json:"rows,omitempty"`
	MetricHeaders []ga4Header `json:"metricHeaders,omitempty"`
	RowCount      int         `json:"rowCount"`
}

type ga4Row struct {
	MetricValues []ga4MetricValue `json:"metricValues"`
}

type ga4Header struct {
	Name string `json:"name"`
	Type string `json:"type"`
}

type ga4MetricValue struct {
	Value string `json:"value"`
}

// ---- Helper Functions ----

func parseInt64(s string) int64 {
	var v int64
	fmt.Sscanf(s, "%d", &v)
	return v
}

func parseFloat(s string) float64 {
	var v float64
	fmt.Sscanf(s, "%f", &v)
	return v
}
