package perplexity

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/geolens/platform/engine"
)

const (
	tier      = engine.TierDirect // Perplexity Sonar API — Kademe 1 (direct)
	apiURL    = "https://api.perplexity.ai/chat/completions"
	modelName = "sonar-pro"
	timeout   = 60 * time.Second
)

// sonarRequest is the request body for Perplexity Sonar API.
type sonarRequest struct {
	Model    string    `json:"model"`
	Messages []message `json:"messages"`
}

type message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// sonarResponse is the response body from Perplexity Sonar API.
type sonarResponse struct {
	ID        string   `json:"id"`
	Model     string   `json:"model"`
	Choices   []choice `json:"choices"`
	Citations []string `json:"citations"`
}

type choice struct {
	Index   int     `json:"index"`
	Message message `json:"message"`
}

// RawSaver defines the interface for saving raw API responses to persistent storage.
type RawSaver interface {
	SaveRawResponse(ctx context.Context, tenantID, workspaceID, engineName string, data []byte) (string, error)
}

// Adapter implements engine.Adapter for Perplexity Sonar API.
type Adapter struct {
	apiKey      string
	httpClient  *http.Client
	storage     RawSaver
	tenantID    string
	workspaceID string
}

// NewAdapter creates a new Perplexity adapter.
func NewAdapter(apiKey string, storage RawSaver) *Adapter {
	return &Adapter{
		apiKey: apiKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		storage: storage,
	}
}

// WithContext creates a copy of the adapter with the given tenant and workspace context.
// Orijinal adapter mutasyona uğramaz — böylece eşzamanlı isteklerde race condition önlenir.
// engine.Adapter dönüş tipi, measure handler'ın import bağımlılığı olmadan çağırabilmesini sağlar.
func (a *Adapter) WithContext(tenantID, workspaceID string) engine.Adapter {
	return &Adapter{
		apiKey:      a.apiKey,
		httpClient:  a.httpClient,
		storage:     a.storage,
		tenantID:    tenantID,
		workspaceID: workspaceID,
	}
}

// Name returns the engine name.
func (a *Adapter) Name() string {
	return "perplexity"
}

// Tier returns the access tier.
func (a *Adapter) Tier() engine.Tier {
	return tier
}

// Execute sends a prompt to Perplexity Sonar API and returns the normalized response.
func (a *Adapter) Execute(prompt string) (*engine.RawResponse, error) {
	reqBody := sonarRequest{
		Model: modelName,
		Messages: []message{
			{Role: "user", Content: prompt},
		},
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("perplexity istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequest("POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("perplexity http istek oluşturma: %w", err)
	}
	httpReq.Header.Set("Authorization", "Bearer "+a.apiKey)
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("perplexity api çağrısı: %w", err)
	}
	defer resp.Body.Close()

	durationMs := time.Since(start).Milliseconds()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("perplexity yanıt okuma: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("perplexity api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(rawBody, durationMs)
}

// parseResponse parses a raw Perplexity API response into RawResponse.
func (a *Adapter) parseResponse(raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var sr sonarResponse
	if err := json.Unmarshal(raw, &sr); err != nil {
		return nil, fmt.Errorf("perplexity yanıt ayrıştırma: %w", err)
	}

	if len(sr.Choices) == 0 {
		return nil, fmt.Errorf("perplexity: boş choices dizisi")
	}

	content := sr.Choices[0].Message.Content

	// Alıntıları dönüştür
	citations := make([]engine.Citation, 0, len(sr.Citations))
	for i, c := range sr.Citations {
		citations = append(citations, engine.Citation{
			URL:      c,
			Position: i + 1,
			Engine:   "perplexity",
			Type:     "direct",
		})
	}

	resp := &engine.RawResponse{
		EngineName:    "perplexity",
		RequestID:     sr.ID,
		Content:       content,
		Citations:     citations,
		HasSearch:     len(sr.Citations) > 0,
		Tier:          tier,
		FidelityLabel: "Kademe 1 · perplexity · sonar-pro",
		S3Ref:         "",
	}

	// Ham yanıtı S3'e kaydet (storage varsa)
	if a.storage != nil && a.tenantID != "" {
		ctx := context.Background()
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "perplexity", raw)
		if err != nil {
			// Non-fatal: S3 hatası skor hesaplamasını engellemez
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
