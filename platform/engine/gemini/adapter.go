package gemini

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
	tier      = engine.TierDirect // Gemini API — Kademe 1 (direct)
	apiURL    = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent"
	modelName = "gemini-3.5-pro"
	timeout   = 60 * time.Second
)

// ---- Request Types ----

// geminiRequest is the request body for Gemini generateContent API.
type geminiRequest struct {
	Contents         []content         `json:"contents"`
	Tools            []tool            `json:"tools,omitempty"`
	GroundingConfig  *groundingConfig  `json:"groundingConfig,omitempty"`
	GenerationConfig *generationConfig `json:"generationConfig,omitempty"` // H15: temp=0
}

type content struct {
	Parts []part `json:"parts"`
}

type part struct {
	Text string `json:"text"`
}

type tool struct {
	GoogleSearch *googleSearch `json:"google_search,omitempty"`
}

type googleSearch struct{}

type groundingConfig struct {
	Threshold string `json:"threshold,omitempty"` // e.g. "BLOCK_MEDIUM_AND_ABOVE"
}

type generationConfig struct {
	Temperature float64 `json:"temperature"`
}

// ---- Response Types ----

// geminiResponse is the response body from Gemini generateContent API.
type geminiResponse struct {
	Candidates []candidate `json:"candidates"`
	Usage      *usage      `json:"usageMetadata,omitempty"`
}

type candidate struct {
	Content               content                `json:"content"`
	FinishReason          string                 `json:"finishReason"`
	GroundingAttributions []groundingAttribution `json:"groundingAttributions,omitempty"`
}

type groundingAttribution struct {
	SourceID sourceID `json:"sourceId"`
	Content  content  `json:"content"`
}

type sourceID struct {
	WebSource *webSource `json:"webSource,omitempty"`
}

type webSource struct {
	URI string `json:"uri"`
}

type usage struct {
	PromptTokenCount     int `json:"promptTokenCount"`
	CandidatesTokenCount int `json:"candidatesTokenCount"`
	TotalTokenCount      int `json:"totalTokenCount"`
}

// ---- Adapter ----

// Adapter implements engine.Adapter for Google Gemini.
type Adapter struct {
	apiKey      string
	httpClient  *http.Client
	storage     engine.RawSaver
	tenantID    string
	workspaceID string
}

// NewAdapter creates a new Gemini adapter.
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
	return "gemini"
}

// Tier returns the access tier.
func (a *Adapter) Tier() engine.Tier {
	return tier
}

// Execute sends a prompt to Gemini API with Google Search grounding and returns the normalized response.
func (a *Adapter) Execute(prompt string) (*engine.RawResponse, error) {
	// Mock modu: API anahtarı yoksa sahte yanıt döndür
	if a.apiKey == "" || a.apiKey == "mock" {
		return mockResponse(prompt), nil
	}

	reqBody := geminiRequest{
		Contents: []content{
			{
				Parts: []part{
					{Text: prompt},
				},
			},
		},
		Tools: []tool{
			{GoogleSearch: &googleSearch{}},
		},
		GenerationConfig: &generationConfig{
			Temperature: 0, // H15: deterministik çıktı için
		},
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("gemini istek serileştirme: %w", err)
	}

	httpReq, err := http.NewRequest("POST", apiURL+"?key="+a.apiKey, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("gemini http istek oluşturma: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	start := time.Now()
	resp, err := a.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("gemini api çağrısı: %w", err)
	}
	defer resp.Body.Close()

	durationMs := time.Since(start).Milliseconds()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("gemini yanıt okuma: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("gemini api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(rawBody, durationMs)
}

// mockResponse returns a realistic mock response for demo purposes.
func mockResponse(prompt string) *engine.RawResponse {
	content := "Acme şirketi, Google Gemini modelleri ve yapay zeka altyapısı konusunda sektörde öncü bir konuma sahiptir. "
	content += "Gemini'nin çok modlu yetenekleri ve Google Search grounding entegrasyonu sayesinde Acme, "
	content += "doğru ve güncel bilgi sunma konusunda rakiplerinin önünde yer almaktadır. "
	content += "Şirketin inovasyon odaklı yaklaşımı, birden çok sektör raporunda "
	content += "örnek vaka olarak gösterilmektedir."

	return &engine.RawResponse{
		EngineName: "gemini",
		RequestID:  "mock-req-gemini-" + fmt.Sprintf("%d", time.Now().UnixMilli()),
		Content:    content,
		Citations: []engine.Citation{
			{URL: "https://deepmind.google/gemini/acme", Position: 1, Engine: "gemini", Type: "direct"},
			{URL: "https://ai.googleblog.com/2026/acme-case", Position: 2, Engine: "gemini", Type: "direct"},
			{URL: "https://cloud.google.com/gemini/acme", Position: 3, Engine: "gemini", Type: "direct"},
		},
		HasSearch:     true,
		Tier:          engine.TierDirect,
		FidelityLabel: "Kademe 1 · gemini · gemini-3.5-pro (mock)",
	}
}

// parseResponse parses a raw Gemini API response into RawResponse.
// GroundingAttributions'dan alıntıları çıkarır.
func (a *Adapter) parseResponse(raw []byte, durationMs int64) (*engine.RawResponse, error) {
	var gr geminiResponse
	if err := json.Unmarshal(raw, &gr); err != nil {
		return nil, fmt.Errorf("gemini yanıt ayrıştırma: %w", err)
	}

	if len(gr.Candidates) == 0 {
		return nil, fmt.Errorf("gemini: boş candidates dizisi")
	}

	cand := gr.Candidates[0]

	// Metin içeriğini birleştir
	var contentText string
	for _, p := range cand.Content.Parts {
		contentText += p.Text
	}

	// GroundingAttributions'dan alıntıları çıkar
	citations := make([]engine.Citation, 0, len(cand.GroundingAttributions))
	for i, attr := range cand.GroundingAttributions {
		if attr.SourceID.WebSource != nil && attr.SourceID.WebSource.URI != "" {
			// Attributions içindeki metni kullan
			var snippet string
			for _, p := range attr.Content.Parts {
				snippet += p.Text
			}
			citations = append(citations, engine.Citation{
				URL:      attr.SourceID.WebSource.URI,
				Position: i + 1,
				Engine:   "gemini",
				Type:     "direct",
				Title:    snippet,
			})
		}
	}

	resp := &engine.RawResponse{
		EngineName:    "gemini",
		RequestID:     fmt.Sprintf("gemini-%d", time.Now().UnixMilli()),
		Content:       contentText,
		Citations:     citations,
		HasSearch:     len(citations) > 0,
		Tier:          tier,
		FidelityLabel: fmt.Sprintf("Kademe 1 · gemini · %s", modelName),
	}

	// Ham yanıtı S3'e kaydet (storage varsa)
	if a.storage != nil && a.tenantID != "" {
		ctx := context.Background()
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "gemini", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}
