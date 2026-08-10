// Package seo provides SEO platform integrations (FR-B8) including background sync.
package seo

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math"
	"math/rand"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
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
	start := time.Now()
	rows, err := w.pool.Query(ctx, `
		SELECT id, tenant_id, workspace_id, platform, access_token, refresh_token, token_expires_at
		FROM seo.connections
		WHERE is_active = true AND platform = $1
	`, platform)
	if err != nil {
		slog.Warn("seo sync: bağlantı sorgu hatası", "platform", platform, "error", err)
		metrics.SEOSyncsTotal.WithLabelValues(platform, "", "query_error").Inc()
		return
	}
	defer rows.Close()

	success, failed := 0, 0
	for rows.Next() {
		var c connRow
		if err := rows.Scan(&c.ID, &c.TenantID, &c.WorkspaceID, &c.Platform, &c.AccessToken, &c.RefreshToken, &c.TokenExpiresAt); err != nil {
			slog.Warn("seo sync: satır okuma hatası", "error", err)
			failed++
			continue
		}

		// Token süresi dolmuşsa yenile
		accessToken := c.AccessToken
		if time.Now().After(c.TokenExpiresAt) && c.RefreshToken != "" {
			newToken, err := w.refreshAccessToken(ctx, c.RefreshToken)
			if err != nil {
				slog.Error("seo sync: token yenileme hatası", "conn", c.ID, "error", err)
				failed++
				continue
			}
			accessToken = newToken.AccessToken

			if _, err := w.pool.Exec(ctx, `
				UPDATE seo.connections
				SET access_token = $1, refresh_token = COALESCE(NULLIF($2, ''), refresh_token),
				    token_expires_at = $3, updated_at = now()
				WHERE id = $4
			`, newToken.AccessToken, newToken.RefreshToken, newToken.ExpiresAt, c.ID); err != nil {
				slog.Error("seo sync: token güncelleme hatası", "conn", c.ID, "error", err)
				failed++
				continue
			}
		}

		var syncErr error
		switch platform {
		case "search_console":
			syncErr = w.syncSearchConsole(ctx, c, accessToken)
		case "ga4":
			syncErr = w.syncGA4(ctx, c, accessToken)
		}
		if syncErr != nil {
			slog.Warn("seo sync: bağlantı senkronizasyon hatası", "platform", platform, "conn", c.ID, "error", syncErr)
			failed++
			metrics.SEOSyncsTotal.WithLabelValues(platform, c.TenantID, "error").Inc()
			continue
		}

		// Son sync zamanını güncelle
		if _, err := w.pool.Exec(ctx, `UPDATE seo.connections SET last_synced_at = now() WHERE id = $1`, c.ID); err != nil {
			slog.Warn("seo sync: last_synced_at güncelleme hatası", "conn", c.ID, "error", err)
		}
		success++
		metrics.SEOSyncsTotal.WithLabelValues(platform, c.TenantID, "ok").Inc()
	}

	if rows.Err() != nil {
		slog.Warn("seo sync: connection rows iterasyon hatası", "platform", platform, "error", rows.Err())
		failed++
	}

	metrics.SEOSyncDuration.WithLabelValues(platform).Observe(time.Since(start).Seconds())
	slog.Info("seo sync döngüsü tamam", "platform", platform, "success", success, "failed", failed, "elapsed", time.Since(start).String())
}

// syncSearchConsole processes a single Search Console connection.
func (w *SyncWorker) syncSearchConsole(ctx context.Context, c connRow, accessToken string) error {
	brandRows, err := w.pool.Query(ctx, `
		SELECT id, COALESCE(website_url, '') FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
	`, c.WorkspaceID, c.TenantID)
	if err != nil {
		slog.Warn("seo sync: marka sorgu hatası", "workspace", c.WorkspaceID, "error", err)
		return fmt.Errorf("marka sorgusu: %w", err)
	}
	defer brandRows.Close()

	var syncErr error
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
			syncErr = err
		}
	}

	if brandRows.Err() != nil {
		slog.Warn("seo sync: brand rows iterasyon hatası", "error", brandRows.Err())
		syncErr = brandRows.Err()
	}

	return syncErr
}

// syncGA4 processes a single GA4 connection.
// Önce kullanıcının erişebildiği GA4 property'lerini keşfeder,
// ardından her brand için Analytics Data API'den trafik verilerini çeker.
func (w *SyncWorker) syncGA4(ctx context.Context, c connRow, accessToken string) error {
	// GA4 property'lerini keşfet
	properties, err := w.discoverGA4Properties(ctx, accessToken)
	if err != nil {
		slog.Warn("seo sync: ga4 property keşif hatası", "error", err)
		return fmt.Errorf("ga4 property keşif: %w", err)
	}
	if len(properties) == 0 {
		slog.Debug("seo sync: ga4 property bulunamadı", "conn", c.ID)
		return nil
	}

	// İlk property'yi kullan (veya domain eşleşmesi yap)
	propertyID := properties[0]

	brandRows, err := w.pool.Query(ctx, `
		SELECT id, COALESCE(website_url, '') FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
	`, c.WorkspaceID, c.TenantID)
	if err != nil {
		slog.Warn("seo sync: marka sorgu hatası", "workspace", c.WorkspaceID, "error", err)
		return fmt.Errorf("marka sorgusu: %w", err)
	}
	defer brandRows.Close()

	var syncErr error
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
			syncErr = err
		}
	}

	if brandRows.Err() != nil {
		slog.Warn("seo sync: brand rows iterasyon hatası", "error", brandRows.Err())
		syncErr = brandRows.Err()
	}

	return syncErr
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

	// HT2 sertleştirme: geçici hatalar (429, 5xx) için exponential backoff ile retry
	var rawResp []byte
	var respStatus int
	err = doWithRetry(ctx, 4, func() (bool, error) {
		req, rerr := http.NewRequestWithContext(ctx, "POST", apiURL, bytes.NewReader(body))
		if rerr != nil {
			return false, fmt.Errorf("http istek oluşturma: %w", rerr)
		}
		req.Header.Set("Authorization", "Bearer "+accessToken)
		req.Header.Set("Content-Type", "application/json")

		resp, derr := http.DefaultClient.Do(req)
		if derr != nil {
			return true, fmt.Errorf("ga4 api çağrısı: %w", derr)
		}
		defer resp.Body.Close()
		b, rerr := io.ReadAll(resp.Body)
		if rerr != nil {
			return false, fmt.Errorf("yanıt okuma: %w", rerr)
		}
		rawResp = b
		respStatus = resp.StatusCode
		if retryableStatus(resp.StatusCode) {
			return true, fmt.Errorf("ga4 api hatası (HTTP %d)", resp.StatusCode)
		}
		return false, nil
	})
	if err != nil {
		return err
	}
	if respStatus == http.StatusUnauthorized {
		return fmt.Errorf("yetkisiz erişim")
	}
	if respStatus != http.StatusOK {
		return fmt.Errorf("ga4 api hatası (HTTP %d)", respStatus)
	}

	var apiResp ga4RunReportResponse
	if err := json.Unmarshal(rawResp, &apiResp); err != nil {
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

	// HT2 sertleştirme: geçici hatalar (429, 5xx) için exponential backoff ile retry
	var rawResp []byte
	var respStatus int
	err = doWithRetry(ctx, 4, func() (bool, error) {
		req, rerr := http.NewRequestWithContext(ctx, "POST", apiURL, bytes.NewReader(body))
		if rerr != nil {
			return false, fmt.Errorf("http istek oluşturma: %w", rerr)
		}
		req.Header.Set("Authorization", "Bearer "+accessToken)
		req.Header.Set("Content-Type", "application/json")

		resp, derr := http.DefaultClient.Do(req)
		if derr != nil {
			return true, fmt.Errorf("api çağrısı: %w", derr)
		}
		defer resp.Body.Close()
		b, rerr := io.ReadAll(resp.Body)
		if rerr != nil {
			return false, fmt.Errorf("yanıt okuma: %w", rerr)
		}
		rawResp = b
		respStatus = resp.StatusCode
		if retryableStatus(resp.StatusCode) {
			return true, fmt.Errorf("api hatası (HTTP %d)", resp.StatusCode)
		}
		return false, nil
	})
	if err != nil {
		return err
	}
	if respStatus == http.StatusUnauthorized {
		return fmt.Errorf("yetkisiz erişim (token expired?)")
	}
	if respStatus != http.StatusOK {
		return fmt.Errorf("api hatası (HTTP %d)", respStatus)
	}

	var apiResp searchAnalyticsResponse
	if err := json.Unmarshal(rawResp, &apiResp); err != nil {
		return fmt.Errorf("yanıt ayrıştırma: %w", err)
	}

	// Results'u batch insert ile kaydet
	if len(apiResp.Rows) == 0 {
		return nil
	}

	today := time.Now().Format("2006-01-02")
	written := 0
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
		} else {
			written++
		}
	}
	metrics.SEOSyncRows.WithLabelValues("search_console").Add(float64(written))

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

// doWithRetry executes fn. For retryable HTTP failures (respecting the optional
// status-aware retry function), it waits exponentially (1s, 2s, 4s, 8s) with
// jitter, up to 4 attempts total. HT2 sertleştirme: API rate limit ve geçici
// hatalar için smart retry.
func doWithRetry(ctx context.Context, attempts int, fn func() (bool, error)) error {
	_, lastErr := fn()
	if lastErr == nil {
		return nil
	}
	backoff := 1 * time.Second
	for i := 2; i <= attempts; i++ {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(backoff + time.Duration(rand.Intn(500))*time.Millisecond):
		}
		retry, err := fn()
		if err == nil {
			return nil
		}
		lastErr = err
		if !retry {
			return err
		}
		backoff = time.Duration(math.Min(float64(backoff*2), float64(8*time.Second)))
	}
	return lastErr
}

// retryableStatus returns true if the HTTP status indicates a transient failure.
func retryableStatus(code int) bool {
	return code == http.StatusTooManyRequests || code == http.StatusInternalServerError ||
		code == http.StatusBadGateway || code == http.StatusServiceUnavailable || code == http.StatusGatewayTimeout
}

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
