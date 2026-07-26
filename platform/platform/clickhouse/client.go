package clickhouse

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	endpoint   string
	username   string
	password   string
	database   string
	httpClient *http.Client
}

func NewClient(endpoint, username, password, database string) *Client {
	return &Client{
		endpoint:   endpoint,
		username:   username,
		password:   password,
		database:   database,
		httpClient: &http.Client{Timeout: 60 * time.Second},
	}
}

type QueryResult struct {
	Meta       []ColumnMeta      `json:"meta"`
	Data       []json.RawMessage `json:"data"`
	Rows       int               `json:"rows"`
	Statistics Statistics        `json:"statistics"`
}

type ColumnMeta struct {
	Name string `json:"name"`
	Type string `json:"type"`
}

type Statistics struct {
	Elapsed   float64 `json:"elapsed"`
	RowsRead  int     `json:"rows_read"`
	BytesRead int     `json:"bytes_read"`
}

func (c *Client) Query(ctx context.Context, query string, args ...interface{}) (*QueryResult, error) {
	if c.endpoint == "" {
		slog.Debug("clickhouse: endpoint yapılandırılmamış, atlanıyor")
		return &QueryResult{}, nil
	}

	finalQuery := query
	for _, arg := range args {
		argStr := fmt.Sprintf("'%v'", arg)
		finalQuery = strings.Replace(finalQuery, "?", argStr, 1)
	}

	reqBody := fmt.Sprintf("database=%s&query=%s", c.database, url.QueryEscape(finalQuery))
	reqURL := fmt.Sprintf("%s/?database=%s&query=%s", c.endpoint, c.database, url.QueryEscape(finalQuery))

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, reqURL, strings.NewReader(reqBody))
	if err != nil {
		return nil, fmt.Errorf("ch istek: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	if c.username != "" {
		req.SetBasicAuth(c.username, c.password)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("ch çağrı: %w", err)
	}
	defer resp.Body.Close()

	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("ch hatası (HTTP %d): %s", resp.StatusCode, string(raw))
	}

	var result QueryResult
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, fmt.Errorf("ch yanıt ayrıştırma: %w", err)
	}

	return &result, nil
}
