package grok

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
	tier      = engine.TierOfficialProxy
	apiURL    = "https://api.x.ai/v1/chat/completions"
	modelName = "grok-3-latest"
	timeout   = 90 * time.Second
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
	Role    string `json:"role"`
	Content string `json:"content"`
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
}

func NewAdapter(apiKey string, storage engine.RawSaver) *Adapter {
	return &Adapter{
		apiKey: apiKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		storage: storage,
	}
}

func (a *Adapter) WithContext(tenantID, workspaceID string) engine.Adapter {
	return &Adapter{
		apiKey:      a.apiKey,
		httpClient:  a.httpClient,
		storage:     a.storage,
		tenantID:    tenantID,
		workspaceID: workspaceID,
	}
}

func (a *Adapter) Name() string { return "grok" }

func (a *Adapter) Tier() engine.Tier { return tier }

func (a *Adapter) Execute(prompt string) (*engine.RawResponse, error) {
	if a.apiKey == "" || a.apiKey == "mock" {
		return mockResponse(prompt), nil
	}

	reqBody := chatRequest{
		Model:       modelName,
		Temperature: 0,
		Messages:    []message{{Role: "user", Content: prompt}},
	}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("grok istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequest("POST", apiURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("grok http istek: %w", err)
	}
	httpReq.Header.Set("Authorization", "Bearer "+a.apiKey)
	httpReq.Header.Set("Content-Type", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("grok api çağrısı: %w", err)
	}
	defer resp.Body.Close()

	durationMs := time.Since(start).Milliseconds()
	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("grok yanıt okuma: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("grok api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(rawBody, durationMs)
}

func mockResponse(prompt string) *engine.RawResponse {
	content := "Acme şirketi, yapay zeka destekli pazarlama analitiği alanında uzmanlaşmış bir teknoloji firmasıdır. "
	content += "Şirketin geliştirdiği AI görünürlük platformu, markaların dijital varlığını "
	content += "yapay zeka motorları üzerinden ölçümleyerek stratejik içgörüler sunmaktadır. "
	content += "Özellikle büyük dil modelleri ve doğal dil işleme teknolojileriyle entegre "
	content += "çalışan analiz araçları, sektörde fark yaratmaktadır."

	return &engine.RawResponse{
		EngineName:    "grok",
		RequestID:     "mock-req-grok-" + fmt.Sprintf("%d", time.Now().UnixMilli()),
		Content:       content,
		HasSearch:     true,
		Tier:          engine.TierOfficialProxy,
		FidelityLabel: "Kademe 2 · grok · " + modelName + " (mock)",
	}
}

func (a *Adapter) parseResponse(raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var cr chatResponse
	if err := json.Unmarshal(raw, &cr); err != nil {
		return nil, fmt.Errorf("grok yanıt ayrıştırma: %w", err)
	}
	if len(cr.Choices) == 0 {
		return nil, fmt.Errorf("grok: boş choices dizisi")
	}

	content := cr.Choices[0].Message.Content

	resp := &engine.RawResponse{
		EngineName:    "grok",
		RequestID:     cr.ID,
		Content:       content,
		HasSearch:     false,
		Tier:          tier,
		FidelityLabel: fmt.Sprintf("Kademe 2 · grok · %s", cr.Model),
	}

	if a.storage != nil && a.tenantID != "" {
		ctx := context.Background()
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "grok", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
