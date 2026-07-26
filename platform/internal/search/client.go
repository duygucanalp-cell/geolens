// Package search provides search indexing functionality.
package search

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

type Client struct {
	endpoint   string
	apiKey     string
	httpClient *http.Client
}

type IndexDoc struct {
	Index string
	ID    string
	Body  map[string]interface{}
}

type SearchResult struct {
	Hits      int               `json:"hits"`
	Documents []json.RawMessage `json:"documents"`
}

func NewClient(endpoint, apiKey string) *Client {
	return &Client{
		endpoint:   endpoint,
		apiKey:     apiKey,
		httpClient: &http.Client{Timeout: 30 * time.Second},
	}
}

func (c *Client) Index(ctx context.Context, doc IndexDoc) error {
	if c.endpoint == "" {
		slog.Debug("elasticsearch: endpoint yapılandırılmamış, atlanıyor", "index", doc.Index)
		return nil
	}

	body, err := json.Marshal(doc.Body)
	if err != nil {
		return fmt.Errorf("es serileştirme: %w", err)
	}

	url := fmt.Sprintf("%s/%s/_doc/%s", c.endpoint, doc.Index, doc.ID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, url, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("es istek: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if c.apiKey != "" {
		req.Header.Set("Authorization", "ApiKey "+c.apiKey)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("es çağrı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode >= 400 {
		raw, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("es hatası (HTTP %d): %s", resp.StatusCode, string(raw))
	}

	return nil
}

func (c *Client) Search(ctx context.Context, index string, query map[string]interface{}) (*SearchResult, error) {
	if c.endpoint == "" {
		return &SearchResult{}, nil
	}

	body, err := json.Marshal(map[string]interface{}{"query": query})
	if err != nil {
		return nil, fmt.Errorf("es sorgu serileştirme: %w", err)
	}

	url := fmt.Sprintf("%s/%s/_search", c.endpoint, index)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("es arama istek: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if c.apiKey != "" {
		req.Header.Set("Authorization", "ApiKey "+c.apiKey)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("es arama çağrı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("es arama hatası (HTTP %d): %s", resp.StatusCode, string(raw))
	}

	var esResp struct {
		Hits struct {
			Total struct {
				Value int `json:"value"`
			} `json:"total"`
			Hits []struct {
				Source json.RawMessage `json:"_source"`
			} `json:"hits"`
		} `json:"hits"`
	}
	if err := json.Unmarshal(raw, &esResp); err != nil {
		return nil, fmt.Errorf("es yanıt ayrıştırma: %w", err)
	}

	result := &SearchResult{Hits: esResp.Hits.Total.Value}
	for _, h := range esResp.Hits.Hits {
		result.Documents = append(result.Documents, h.Source)
	}

	return result, nil
}
