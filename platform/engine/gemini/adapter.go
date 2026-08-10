// Package gemini provides an adapter for the gemini AI engine.
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
	tier           = engine.TierDirect      // Gemini API — Kademe 1 (direct)
	aiOverviewTier = engine.TierDirectional // Google AI Overview — Kademe 3 (directional)
	aiModeTier     = engine.TierDirectional // Google AI Mode — Kademe 3 (directional)
	apiURL         = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent"
	aiOverviewURL  = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent?alt=sse" // AI Overview endpoint (Kademe 3 proxy)
	aiModeURL      = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent?alt=sse" // Google AI Mode endpoint (Kademe 3 proxy)
	modelName      = "gemini-3.5-pro"
	timeout        = 60 * time.Second
)

// ---- Request Types ----

// geminiRequest is the request body for Gemini generateContent API.
type geminiRequest struct {
	Contents         []content         `json:"contents"`
	Tools            []tool            `json:"tools,omitempty"`
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
// Standard Gemini sorguları için Kademe 1 (direct) fidelity etiketi kullanılır.
// ctx: cancel/timeout desteği ile HTTP çağrısı yapar.
func (a *Adapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
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

	httpReq, err := http.NewRequestWithContext(ctx, "POST", apiURL+"?key="+a.apiKey, bytes.NewReader(body))
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
	defer func() { _ = resp.Body.Close() }()

	durationMs := time.Since(start).Milliseconds()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("gemini yanıt okuma: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("gemini api hatası (HTTP %d): %s", resp.StatusCode, string(rawBody))
	}

	return a.parseResponse(ctx, rawBody, durationMs)
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
func (a *Adapter) parseResponse(ctx context.Context, raw []byte, durationMs int64) (*engine.RawResponse, error) {
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

	// Kademe belirleme: Standart Gemini sorguları Kademe 1 (direct) olarak işaretlenir.
	// Google AI Overview sonuçları Kademe 3 (directional) olarak ayrı bir adapter üzerinden işlenir.
	responseTier := tier
	fidelityLabel := fmt.Sprintf("Kademe 1 · gemini · %s", modelName)

	resp := &engine.RawResponse{
		EngineName:    "gemini",
		RequestID:     fmt.Sprintf("gemini-%d", time.Now().UnixMilli()),
		Content:       contentText,
		Citations:     citations,
		HasSearch:     len(citations) > 0,
		Tier:          responseTier,
		FidelityLabel: fidelityLabel,
	}

	// Ham yanıtı S3'e kaydet (storage varsa)
	if a.storage != nil && a.tenantID != "" {
		key, err := a.storage.SaveRawResponse(ctx, a.tenantID, a.workspaceID, "gemini", raw)
		if err != nil {
			resp.S3Ref = ""
		} else {
			resp.S3Ref = key
		}
	}

	return resp, nil
}

// WithAIOverview returns a copy of the adapter configured for Google AI Overview mode.
// AI Overview sonuçları Kademe 3 (directional) fidelity etiketiyle işaretlenir.
// MVP'de Google AI Overview, ayrı bir yüzey olarak Gemini grounding vekili üzerinden proxy'lenir.
func (a *Adapter) WithAIOverview(tenantID, workspaceID string) *aiOverviewAdapter {
	return &aiOverviewAdapter{
		Adapter: Adapter{
			apiKey:      a.apiKey,
			httpClient:  a.httpClient,
			storage:     a.storage,
			tenantID:    tenantID,
			workspaceID: workspaceID,
		},
	}
}

// aiOverviewAdapter wraps the standard Gemini adapter for Google AI Overview queries.
type aiOverviewAdapter struct {
	Adapter
}

func (a *aiOverviewAdapter) Name() string {
	return "google_ai_overview"
}

func (a *aiOverviewAdapter) Tier() engine.Tier {
	return aiOverviewTier
}

// WithContext returns a copy of the AI Overview adapter with tenant and workspace context set.
// Adapter.WithContext override edilir; aksi halde embed edilen Adapter yöntemi AI Overview
// wrapper'ını düşürerek Kademe 1 gemini adapter'ına geri döner.
func (a *aiOverviewAdapter) WithContext(tenantID, workspaceID string) engine.Adapter {
	return &aiOverviewAdapter{
		Adapter: Adapter{
			apiKey:      a.apiKey,
			httpClient:  a.httpClient,
			storage:     a.storage,
			tenantID:    tenantID,
			workspaceID: workspaceID,
		},
	}
}

func (a *aiOverviewAdapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
	// Google AI Overview, Gemini grounding API'sini kullanır ancak Kademe 3 (directional)
	// fidelity etiketiyle işaretlenir.
	resp, err := a.Adapter.Execute(ctx, prompt)
	if err != nil {
		return nil, err
	}
	// Yanıtı AI Overview etiketiyle override et
	resp.Tier = aiOverviewTier
	resp.FidelityLabel = fmt.Sprintf("Kademe 3 · google_ai_overview · %s (official_proxy/directional)", modelName)
	resp.EngineName = "google_ai_overview"
	return resp, nil
}

// WithAIMode returns a copy of the adapter configured for Google AI Mode.
// AI Mode sonuçları Kademe 3 (directional) fidelity etiketiyle işaretlenir.
// HT2 — FR-B6 genişletmesi: Gemini proxy adapter'ı üzerinden AI Mode yüzeyi.
// Maliyet/kararlılık değerlendirmesi 0207-ht2 §5.2.3 kriterleriyle yapılır.
func (a *Adapter) WithAIMode(tenantID, workspaceID string) *aiModeAdapter {
	return &aiModeAdapter{
		Adapter: Adapter{
			apiKey:      a.apiKey,
			httpClient:  a.httpClient,
			storage:     a.storage,
			tenantID:    tenantID,
			workspaceID: workspaceID,
		},
	}
}

// aiModeAdapter wraps the standard Gemini adapter for Google AI Mode queries.
type aiModeAdapter struct {
	Adapter
}

func (a *aiModeAdapter) Name() string {
	return "google_ai_mode"
}

func (a *aiModeAdapter) Tier() engine.Tier {
	return aiModeTier
}

// WithContext returns a copy of the AI Mode adapter with tenant and workspace context set.
// Adapter.WithContext override edilir; aksi halde embed edilen Adapter yöntemi AI Mode
// wrapper'ını düşürerek Kademe 1 gemini adapter'ına geri döner.
func (a *aiModeAdapter) WithContext(tenantID, workspaceID string) engine.Adapter {
	return &aiModeAdapter{
		Adapter: Adapter{
			apiKey:      a.apiKey,
			httpClient:  a.httpClient,
			storage:     a.storage,
			tenantID:    tenantID,
			workspaceID: workspaceID,
		},
	}
}

func (a *aiModeAdapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
	// Google AI Mode, Gemini API'yi kullanır ancak Kademe 3 (directional)
	// fidelity etiketiyle işaretlenir.
	resp, err := a.Adapter.Execute(ctx, prompt)
	if err != nil {
		return nil, err
	}
	// Yanıtı AI Mode etiketiyle override et
	resp.Tier = aiModeTier
	resp.FidelityLabel = fmt.Sprintf("Kademe 3 · google_ai_mode · %s (official_proxy/directional)", modelName)
	resp.EngineName = "google_ai_mode"
	return resp, nil
}
