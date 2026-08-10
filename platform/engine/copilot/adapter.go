// Package copilot provides an adapter for the copilot AI engine.
package copilot

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
	tier      = engine.TierDirectional
	apiURL    = "https://copilot.microsoft.com/api/chat/completions"
	modelName = "copilot-gpt-4o"
	timeout   = 120 * time.Second
)

type chatRequest struct {
	Model       string    `json:"model"`
	Messages    []message `json:"messages"`
	Temperature float64   `json:"temperature"`
}

type message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type chatResponse struct {
	ID      string   `json:"id"`
	Object  string   `json:"object"`
	Model   string   `json:"model"`
	Choices []choice `json:"choices"`
	Usage   *usage   `json:"usage,omitempty"`
}

type choice struct {
	Index        int         `json:"index"`
	Message      chatMessage `json:"message"`
	FinishReason string      `json:"finish_reason"`
}

type chatMessage struct {
	Role      string     `json:"role"`
	Content   string     `json:"content"`
	Citations []citation `json:"citations,omitempty"`
}

type citation struct {
	URL   string `json:"url"`
	Title string `json:"title,omitempty"`
}

type usage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

type Adapter struct {
	apiKey      string
	httpClient  *http.Client
	storage     engine.RawSaver
	tenantID    string
	workspaceID string
	endpoint    string
}

func NewAdapter(apiKey string, storage engine.RawSaver) *Adapter {
	return &Adapter{
		apiKey: apiKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		storage:  storage,
		endpoint: apiURL,
	}
}

func (a *Adapter) WithContext(tenantID, workspaceID string) engine.Adapter {
	return &Adapter{
		apiKey:      a.apiKey,
		httpClient:  a.httpClient,
		storage:     a.storage,
		tenantID:    tenantID,
		workspaceID: workspaceID,
		endpoint:    a.endpoint,
	}
}

func (a *Adapter) Name() string { return "copilot" }

func (a *Adapter) Tier() engine.Tier { return tier }

func (a *Adapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
	if a.apiKey == "" || a.apiKey == "mock" {
		return mockResponse(prompt), nil
	}

	url := a.endpoint
	if url == "" {
		url = apiURL
	}

	reqBody := chatRequest{
		Model:       modelName,
		Temperature: 0,
		Messages:    []message{{Role: "user", Content: prompt}},
	}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("copilot istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("copilot http istek: %w", err)
	}
	httpReq.Header.Set("Authorization", "Bearer "+a.apiKey)
	httpReq.Header.Set("Content-Type", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("copilot api çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	durationMs := time.Since(start).Milliseconds()
	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("copilot yanıt okuma: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("copilot api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(ctx, rawBody, durationMs)
}

func mockResponse(prompt string) *engine.RawResponse {
	content := "Acme, dijital pazarlama ve yapay zeka görünürlüğü alanında faaliyet gösteren bir teknoloji şirketidir. "
	content += "Microsoft Copilot entegrasyonu sayesinde, markaların AI motorlarındaki "
	content += "görünürlüğünü analiz eder ve iyileştirme önerileri sunar. "
	content += "Şirketin yenilikçi yaklaşımı, sektör raporlarında dikkat çekmektedir."

	return &engine.RawResponse{
		EngineName:    "copilot",
		RequestID:     "mock-req-copilot-" + fmt.Sprintf("%d", time.Now().UnixMilli()),
		Content:       content,
		HasSearch:     false,
		Tier:          engine.TierDirectional,
		FidelityLabel: "Kademe 3 · copilot · " + modelName + " (mock)",
		Citations: []engine.Citation{
			{URL: "https://copilot.microsoft.com", Title: "Microsoft Copilot"},
			{URL: "https://learn.microsoft.com/en-us/copilot/", Title: "Copilot Documentation"},
		},
	}
}

func (a *Adapter) parseResponse(ctx context.Context, raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var cr chatResponse
	if err := json.Unmarshal(raw, &cr); err != nil {
		return nil, fmt.Errorf("copilot yanıt ayrıştırma: %w", err)
	}
	if len(cr.Choices) == 0 {
		return nil, fmt.Errorf("copilot: boş choices dizisi")
	}

	content := cr.Choices[0].Message.Content

	var citations []engine.Citation
	for _, c := range cr.Choices[0].Message.Citations {
		if c.URL != "" {
			citations = append(citations, engine.Citation{
				URL:   c.URL,
				Title: c.Title,
			})
		}
	}

	resp := &engine.RawResponse{
		EngineName:    "copilot",
		RequestID:     cr.ID,
		Content:       content,
		HasSearch:     false,
		Tier:          tier,
		FidelityLabel: fmt.Sprintf("Kademe 3 · copilot · %s", cr.Model),
		Citations:     citations,
	}

	if a.storage != nil && a.tenantID != "" {
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "copilot", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
