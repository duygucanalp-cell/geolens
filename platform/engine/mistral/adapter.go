// Package mistral provides an adapter for the Mistral AI engine.
// Mistral, Kademe 2 (official proxy) olarak sınıflandırılmıştır.
// AB pazarı ve KVKK/GDPR uyumu için stratejik öneme sahiptir.
package mistral

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
	tier      = engine.TierOfficialProxy // Mistral API — Kademe 2 (official proxy)
	apiURL    = "https://api.mistral.ai/v1/chat/completions"
	modelName = "mistral-large-latest"
	timeout   = 60 * time.Second
)

// ---- Request Types ----

// chatRequest is the request body for Mistral Chat Completions API.
type chatRequest struct {
	Model       string        `json:"model"`
	Messages    []chatMessage `json:"messages"`
	Temperature float64       `json:"temperature"` // H15: temp=0 for deterministic output
	MaxTokens   int           `json:"max_tokens,omitempty"`
	SafePrompt  bool          `json:"safe_prompt,omitempty"`
}

type chatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// ---- Response Types ----

// chatResponse is the response body from Mistral Chat Completions API.
type chatResponse struct {
	ID      string       `json:"id"`
	Model   string       `json:"model"`
	Choices []chatChoice `json:"choices"`
	Usage   *usageInfo   `json:"usage,omitempty"`
}

type chatChoice struct {
	Index        int         `json:"index"`
	Message      chatMessage `json:"message"`
	FinishReason string      `json:"finish_reason"`
}

type usageInfo struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

// ---- Adapter ----

// Adapter implements engine.Adapter for Mistral AI API.
type Adapter struct {
	apiKey      string
	httpClient  *http.Client
	storage     engine.RawSaver
	tenantID    string
	workspaceID string
}

// NewAdapter creates a new Mistral adapter.
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
	return "mistral"
}

// Tier returns the access tier (Kademe 2 — official proxy).
func (a *Adapter) Tier() engine.Tier {
	return tier
}

// Execute sends a prompt to Mistral AI API and returns the normalized response.
// Mistral, OpenAI uyumlu API kullandığı için chat/completions endpoint'ine
// istek gönderir. Kademe 2 (official proxy) fidelity etiketi kullanılır.
func (a *Adapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
	// Mock modu: API anahtarı yoksa sahte yanıt döndür
	if a.apiKey == "" || a.apiKey == "mock" {
		return mockResponse(prompt), nil
	}

	reqBody := chatRequest{
		Model: modelName,
		Messages: []chatMessage{
			{Role: "user", Content: prompt},
		},
		Temperature: 0,    // H15: deterministik çıktı için
		SafePrompt:  true, // Mistral güvenli prompt filtresi
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("mistral istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("mistral http istek oluşturma: %w", err)
	}
	httpReq.Header.Set("Authorization", "Bearer "+a.apiKey)
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("mistral api çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	durationMs := time.Since(start).Milliseconds()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("mistral yanıt okuma: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("mistral api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(ctx, rawBody, durationMs)
}

// mockResponse returns a realistic mock response for demo purposes.
func mockResponse(prompt string) *engine.RawResponse {
	content := "Acme şirketi, yapay zeka ve makine öğrenimi alanında öncü çözümler sunmaktadır. "
	content += "Mistral AI'nin açık kaynak modelleri ve Le Chat platformu, "
	content += "şirketin Avrupa pazarındaki dijital dönüşüm hedeflerine katkı sağlamaktadır. "
	content += "Sektör analizlerine göre Acme, KVKK/GDPR uyumlu AI altyapısıyla "
	content += "rakiplerinden ayrışmaktadır. Şirketin çok dilli destek yetenekleri, "
	content += "özellikle Fransızca ve Almanca konuşulan pazarlarda rekabet avantajı yaratmaktadır."

	return &engine.RawResponse{
		EngineName: "mistral",
		RequestID:  "mock-req-" + fmt.Sprintf("%d", time.Now().UnixMilli()),
		Content:    content,
		Citations: []engine.Citation{
			{URL: "https://mistral.ai/news/acme-partnership", Position: 1, Engine: "mistral", Type: "direct"},
			{URL: "https://consilium.europa.eu/ai-act-2026", Position: 2, Engine: "mistral", Type: "direct"},
			{URL: "https://techcrunch.com/mistral-acme-2026", Position: 3, Engine: "mistral", Type: "direct"},
		},
		HasSearch:     true,
		Tier:          engine.TierOfficialProxy,
		FidelityLabel: "Kademe 2 · mistral · mistral-large-latest (mock)",
	}
}

// parseResponse parses a raw Mistral API response into RawResponse.
func (a *Adapter) parseResponse(ctx context.Context, raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var cr chatResponse
	if err := json.Unmarshal(raw, &cr); err != nil {
		return nil, fmt.Errorf("mistral yanıt ayrıştırma: %w", err)
	}

	if len(cr.Choices) == 0 {
		return nil, fmt.Errorf("mistral: boş choices dizisi")
	}

	content := cr.Choices[0].Message.Content

	resp := &engine.RawResponse{
		EngineName:    "mistral",
		RequestID:     cr.ID,
		Content:       content,
		Citations:     []engine.Citation{},
		HasSearch:     false, // Mistral API standard chat'te citation döndürmez
		Tier:          tier,
		FidelityLabel: fmt.Sprintf("Kademe 2 · mistral · %s", cr.Model),
		S3Ref:         "",
	}

	// Usage bilgisini engine meta'ya ekle (ileride cost analytics için kullanılır)
	_ = cr.Usage

	// Ham yanıtı S3'e kaydet (storage varsa)
	if a.storage != nil && a.tenantID != "" {
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "mistral", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
