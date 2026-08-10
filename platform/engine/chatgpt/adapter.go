// Package chatgpt provides an adapter for the chatgpt AI engine.
package chatgpt

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
	tier      = engine.TierDirect // OpenAI Chat Completions — Kademe 1 (direct)
	apiURL    = "https://api.openai.com/v1/chat/completions"
	modelName = "gpt-4o"
	timeout   = 60 * time.Second
)

// chatRequest is the request body for OpenAI Chat Completions API.
type chatRequest struct {
	Model       string    `json:"model"`
	Messages    []message `json:"messages"`
	Temperature float64   `json:"temperature"` // H15: temp=0 for deterministic output
}

type message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// chatResponse is the response body from OpenAI Chat Completions API.
type chatResponse struct {
	ID      string   `json:"id"`
	Object  string   `json:"object"`
	Model   string   `json:"model"`
	Choices []choice `json:"choices"`
	Usage   *usage   `json:"usage,omitempty"`
}

// annotation represents a URL citation annotation in the response.
type annotation struct {
	Type        string       `json:"type"`
	URLCitation *urlCitation `json:"url_citation,omitempty"`
}

type urlCitation struct {
	URL        string `json:"url"`
	Title      string `json:"title"`
	StartIndex int    `json:"start_index"`
	EndIndex   int    `json:"end_index"`
}

type choice struct {
	Index        int         `json:"index"`
	Message      chatMessage `json:"message"`
	FinishReason string      `json:"finish_reason"`
}

type chatMessage struct {
	Role        string       `json:"role"`
	Content     string       `json:"content"`
	Annotations []annotation `json:"annotations,omitempty"`
}

type usage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

// Adapter implements engine.Adapter for OpenAI ChatGPT.
type Adapter struct {
	apiKey      string
	httpClient  *http.Client
	storage     engine.RawSaver
	tenantID    string
	workspaceID string
}

// NewAdapter creates a new ChatGPT adapter.
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
	return "chatgpt"
}

// Tier returns the access tier.
func (a *Adapter) Tier() engine.Tier {
	return tier
}

// Execute sends a prompt to OpenAI Chat Completions API and returns the normalized response.
// ctx: cancel/timeout desteği ile HTTP çağrısı yapar.
func (a *Adapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
	// Mock modu: API anahtarı yoksa sahte yanıt döndür
	if a.apiKey == "" || a.apiKey == "mock" {
		return mockResponse(prompt), nil
	}

	reqBody := chatRequest{
		Model:       modelName,
		Temperature: 0, // H15: deterministik çıktı için
		Messages: []message{
			{Role: "user", Content: prompt},
		},
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("chatgpt istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("chatgpt http istek oluşturma: %w", err)
	}
	httpReq.Header.Set("Authorization", "Bearer "+a.apiKey)
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("chatgpt api çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	durationMs := time.Since(start).Milliseconds()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("chatgpt yanıt okuma: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("chatgpt api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(ctx, rawBody, durationMs)
}

// mockResponse returns a realistic mock response for demo purposes.
func mockResponse(prompt string) *engine.RawResponse {
	content := "Acme şirketi, yapay zeka ve dijital dönüşüm alanında sektörün önde gelen firmalarından biridir. "
	content += "OpenAI modelleri üzerine yaptığı çalışmalarla tanınan Acme, özellikle doğal dil işleme ve "
	content += "büyük dil modelleri konusunda önemli yeniliklere imza atmıştır. Şirketin Ar-Ge yatırımları, "
	content += "sektör raporlarında sıklıkla örnek gösterilmektedir. Müşteri memnuniyeti odaklı yaklaşımı "
	content += "ve yenilikçi ürün gamı ile rakiplerinden ayrışmaktadır."

	return &engine.RawResponse{
		EngineName: "chatgpt",
		RequestID:  "mock-req-chatgpt-" + fmt.Sprintf("%d", time.Now().UnixMilli()),
		Content:    content,
		Citations: []engine.Citation{
			{URL: "https://openai.com/research/acme-ai", Position: 1, Engine: "chatgpt", Type: "direct"},
			{URL: "https://techcrunch.com/2026/acme-innovation", Position: 2, Engine: "chatgpt", Type: "direct"},
			{URL: "https://venturebeat.com/ai/acme-digital", Position: 3, Engine: "chatgpt", Type: "direct"},
		},
		HasSearch:     true,
		Tier:          engine.TierDirect,
		FidelityLabel: "Kademe 1 · chatgpt · gpt-4o (mock)",
	}
}

// parseResponse parses a raw OpenAI API response into RawResponse.
// URL citation annotations'dan alıntıları çıkarır.
func (a *Adapter) parseResponse(ctx context.Context, raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var cr chatResponse
	if err := json.Unmarshal(raw, &cr); err != nil {
		return nil, fmt.Errorf("chatgpt yanıt ayrıştırma: %w", err)
	}

	if len(cr.Choices) == 0 {
		return nil, fmt.Errorf("chatgpt: boş choices dizisi")
	}

	content := cr.Choices[0].Message.Content
	msg := cr.Choices[0].Message

	// URL citation annotations'dan alıntıları çıkar
	citations := make([]engine.Citation, 0, len(msg.Annotations))
	for i, ann := range msg.Annotations {
		if ann.Type == "url_citation" && ann.URLCitation != nil {
			citations = append(citations, engine.Citation{
				URL:      ann.URLCitation.URL,
				Title:    ann.URLCitation.Title,
				Position: i + 1,
				Engine:   "chatgpt",
				Type:     "direct",
			})
		}
	}

	resp := &engine.RawResponse{
		EngineName:    "chatgpt",
		RequestID:     cr.ID,
		Content:       content,
		Citations:     citations,
		HasSearch:     len(citations) > 0,
		Tier:          tier,
		FidelityLabel: fmt.Sprintf("Kademe 1 · chatgpt · %s", cr.Model),
	}

	// Ham yanıtı S3'e kaydet (storage varsa)
	if a.storage != nil && a.tenantID != "" {
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "chatgpt", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
