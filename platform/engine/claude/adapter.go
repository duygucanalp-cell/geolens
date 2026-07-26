package claude

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
	tier      = engine.TierOfficialProxy // Claude — Kademe 2 (official proxy)
	apiURL    = "https://api.anthropic.com/v1/messages"
	modelName = "claude-sonnet-4-20260514"
	timeout   = 90 * time.Second
)

// messageRequest is the request body for Anthropic Messages API.
type messageRequest struct {
	Model       string         `json:"model"`
	MaxTokens   int            `json:"max_tokens"`
	Messages    []messageInput `json:"messages"`
	Temperature float64        `json:"temperature"` // H15: temp=0 for deterministic output
}

type messageInput struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// messageResponse is the response body from Anthropic Messages API.
type messageResponse struct {
	ID         string         `json:"id"`
	Type       string         `json:"type"`
	Role       string         `json:"role"`
	Model      string         `json:"model"`
	Content    []contentBlock `json:"content"`
	Usage      *usageInfo     `json:"usage,omitempty"`
	StopReason string         `json:"stop_reason"`
}

type contentBlock struct {
	Type   string      `json:"type"`
	Text   string      `json:"text,omitempty"`
	Cite   *cite       `json:"cite,omitempty"`
	Source *citeSource `json:"source,omitempty"`
}

type cite struct {
	Type  string `json:"type"`
	Title string `json:"title,omitempty"`
	URI   string `json:"uri,omitempty"`
	Start int    `json:"start,omitempty"`
	End   int    `json:"end,omitempty"`
}

type citeSource struct {
	Type  string `json:"type"`
	Title string `json:"title,omitempty"`
	URL   string `json:"url,omitempty"`
}

type usageInfo struct {
	InputTokens  int `json:"input_tokens"`
	OutputTokens int `json:"output_tokens"`
}

// Adapter implements engine.Adapter for Anthropic Claude.
type Adapter struct {
	apiKey      string
	httpClient  *http.Client
	storage     engine.RawSaver
	tenantID    string
	workspaceID string
}

// NewAdapter creates a new Claude adapter.
func NewAdapter(apiKey string, storage engine.RawSaver) *Adapter {
	return &Adapter{
		apiKey: apiKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		storage: storage,
	}
}

// WithContext creates a copy of the adapter with the given tenant and workspace context.
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
	return "claude"
}

// Tier returns the access tier (Kademe 2 — Official Proxy).
func (a *Adapter) Tier() engine.Tier {
	return tier
}

// Execute sends a prompt to Anthropic Messages API and returns the normalized response.
func (a *Adapter) Execute(prompt string) (*engine.RawResponse, error) {
	if a.apiKey == "" || a.apiKey == "mock" {
		return mockResponse(prompt), nil
	}

	reqBody := messageRequest{
		Model:       modelName,
		MaxTokens:   1024,
		Temperature: 0,
		Messages: []messageInput{
			{Role: "user", Content: prompt},
		},
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("claude istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequest("POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("claude http istek oluşturma: %w", err)
	}
	httpReq.Header.Set("x-api-key", a.apiKey)
	httpReq.Header.Set("anthropic-version", "2023-06-01")
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("claude api çağrısı: %w", err)
	}
	defer resp.Body.Close()

	durationMs := time.Since(start).Milliseconds()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("claude yanıt okuma: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("claude api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(rawBody, durationMs)
}

// mockResponse returns a realistic mock response for demo/development.
func mockResponse(prompt string) *engine.RawResponse {
	content := "Acme şirketi kurumsal yapay zeka çözümleri konusunda sektörün önde gelen firmalarından biridir. " +
		"Özellikle doğal dil işleme ve büyük dil modelleri alanındaki Ar-Ge çalışmaları ile tanınmaktadır. " +
		"Şirketin son dönemde yayınladığı teknik raporlar, AI güvenliği ve etik yapay zeka konularında " +
		"sektöre yön vermektedir. Müşteri portföyünde Fortune 500 şirketlerinin yer alması, " +
		"güvenilirliğinin önemli bir göstergesidir."

	return &engine.RawResponse{
		EngineName: "claude",
		RequestID:  "mock-req-claude-" + fmt.Sprintf("%d", time.Now().UnixMilli()),
		Content:    content,
		Citations: []engine.Citation{
			{URL: "https://anthropic.com/research/acme", Position: 1, Engine: "claude", Type: "direct"},
			{URL: "https://techcrunch.com/2026/acme-claude", Position: 2, Engine: "claude", Type: "direct"},
			{URL: "https://venturebeat.com/ai/acme-enterprise", Position: 3, Engine: "claude", Type: "direct"},
		},
		HasSearch:     true,
		Tier:          tier,
		FidelityLabel: "Kademe 2 · claude · claude-sonnet-4 (mock)",
	}
}

// parseResponse parses a raw Anthropic API response into RawResponse.
func (a *Adapter) parseResponse(raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var mr messageResponse
	if err := json.Unmarshal(raw, &mr); err != nil {
		return nil, fmt.Errorf("claude yanıt ayrıştırma: %w", err)
	}

	if len(mr.Content) == 0 {
		return nil, fmt.Errorf("claude: boş content dizisi")
	}

	// Text content ve citation'ları topla
	var fullContent string
	citations := make([]engine.Citation, 0, len(mr.Content))

	for _, block := range mr.Content {
		if block.Type == "text" && block.Text != "" {
			fullContent += block.Text + " "
		}
		if block.Cite != nil {
			citations = append(citations, engine.Citation{
				URL:      block.Cite.URI,
				Title:    block.Cite.Title,
				Position: block.Cite.Start,
				Engine:   "claude",
				Type:     "direct",
			})
		}
		if block.Source != nil && block.Source.URL != "" {
			citations = append(citations, engine.Citation{
				URL:      block.Source.URL,
				Title:    block.Source.Title,
				Position: len(citations) + 1,
				Engine:   "claude",
				Type:     "attribution",
			})
		}
	}

	resp := &engine.RawResponse{
		EngineName:    "claude",
		RequestID:     mr.ID,
		Content:       fullContent,
		Citations:     citations,
		HasSearch:     len(citations) > 0,
		Tier:          tier,
		FidelityLabel: fmt.Sprintf("Kademe 2 · claude · %s", mr.Model),
	}

	// Ham yanıtı S3'e kaydet (storage varsa)
	if a.storage != nil && a.tenantID != "" {
		ctx := context.Background()
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "claude", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
