// Package ml provides the Go client for the ML serving API (0421 A0-2).
//
// Serving ulaşılamazsa veya hata dönerse çağıran taraf kural tabanlı
// bileşene fallback yapar (0421 M-4). Inference gecikmesi sınırı çevre
// değişkeni ML_TIMEOUT (varsayılan 2s) ile yönetilir.
package ml

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"time"
)

// defaultTimeout serving API çağrıları için üst sınırdır (0421 §6: ölçüm
// timeout'u ≤ recall). Inference hedefi cevap başına < 200ms.
const defaultTimeout = 2 * time.Second

// ErrNotConfigured, ML_SERVING_URL boşken inference çağrısı yapılmaya
// çalışıldığında döner — çağıran taraf kural tabanlı bileşene düşer (0421 M-4).
var ErrNotConfigured = errors.New("ml client yapılandırılmadı (ML_SERVING_URL boş)")

// Client, ML serving API'ye HTTP çağrıları yapan istemcidir.
type Client struct {
	baseURL    string
	httpClient *http.Client
}

// NewClient ML_SERVING_URL tabanlı yeni bir istemci döndürür. timeout <= 0
// ise defaultTimeout kullanılır. baseURL boşsa nil döner — çağıran taraf
// fallback kullanır.
func NewClient(baseURL string, timeout time.Duration) *Client {
	if baseURL == "" {
		return nil
	}
	if timeout <= 0 {
		timeout = defaultTimeout
	}
	return &Client{
		baseURL:    baseURL,
		httpClient: &http.Client{Timeout: timeout},
	}
}

// PredictResult serving API'nin /v1/predict yanıtının genelleştirilmiş halidir.
type PredictResult struct {
	Model        string         `json:"model"`
	ModelVersion string         `json:"model_version"`
	Outputs      map[string]any `json:"outputs"`
}

// postJSON, baseURL'deki path'e JSON POST gönderir ve HTTP 200 yanıtını out'a çözer.
// 200 dışı yanıtlar hata döndürür. (Predict / DetectHallucinations ortak yardımcısı)
func (c *Client) postJSON(ctx context.Context, path string, payload any, out any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("ml: payload serialize hatası: %w", err)
	}

	u, err := url.JoinPath(c.baseURL, path)
	if err != nil {
		return fmt.Errorf("ml: url hatası: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("ml: istek oluşturma hatası: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("ml: serving çağrı hatası: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("ml: yanıt okuma hatası: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("ml: serving HTTP %d: %s", resp.StatusCode, string(raw))
	}
	if err := json.Unmarshal(raw, out); err != nil {
		return fmt.Errorf("ml: yanıt çözümleme hatası: %w", err)
	}
	return nil
}

// Predict tek örnek inference çağrısı yapar.
// payload örn: {"model": "sentiment", "lang": "tr", "text": "..."}
func (c *Client) Predict(ctx context.Context, payload map[string]any) (*PredictResult, error) {
	if c == nil {
		return nil, ErrNotConfigured
	}
	var out PredictResult
	if err := c.postJSON(ctx, "/v1/predict", payload, &out); err != nil {
		return nil, err
	}
	return &out, nil
}

// SentimentPrediction — serving "sentiment" modeli sonucu (0421 A2-1).
// Label sırası eğitimdeki LABELS = [negative, neutral, positive] ile eşittir.
type SentimentPrediction struct {
	ModelVersion string
	Label        string // negative | neutral | positive
	Confidence   float64
	// Probabilities — [negative, neutral, positive] softmax olasılıkları (toplam 1.0).
	Probabilities [3]float64
}

// sentimentLabels, ml/geolens/sentiment/train.py LABELS sırasıdır.
var sentimentLabels = [3]string{"negative", "neutral", "positive"}

// PredictSentiment, serving "sentiment" modeline tek metin inference çağrısı yapar.
// 0421 M-4: serving hata dönerse bu fonksiyon hata döndürür — çağıran kural
// tabanlı bileşene fallback eder. Model çok dilli olduğundan lang opsiyoneldir.
func (c *Client) PredictSentiment(ctx context.Context, text, lang string) (*SentimentPrediction, error) {
	if c == nil {
		return nil, ErrNotConfigured
	}
	res, err := c.Predict(ctx, map[string]any{"model": "sentiment", "lang": lang, "text": text})
	if err != nil {
		return nil, err
	}
	return parseSentimentPrediction(res)
}

// parseSentimentPrediction, serving yanıtındaki "logits" çıktısını (ONNX
// output_names=["logits"], shape [batch,3]) softmax ile olasılığa çevirir.
func parseSentimentPrediction(res *PredictResult) (*SentimentPrediction, error) {
	logitsAny, ok := res.Outputs["logits"]
	if !ok {
		return nil, fmt.Errorf("ml: sentiment yanıtında 'logits' çıktısı yok (outputs=%v)", res.Outputs)
	}
	probs, err := softmaxRow(logitsAny)
	if err != nil {
		return nil, err
	}
	labelIdx := 0
	for i := 1; i < len(probs); i++ {
		if probs[i] > probs[labelIdx] {
			labelIdx = i
		}
	}
	return &SentimentPrediction{
		ModelVersion:  res.ModelVersion,
		Label:         sentimentLabels[labelIdx],
		Confidence:    probs[labelIdx],
		Probabilities: probs,
	}, nil
}

// softmaxRow, JSON'dan gelen tek satırlı logits dizisini ([][]float64 benzeri)
// 3 sınıflı softmax olasılıklarına çevirir. JSON decode sonucu []any olduğundan
// tür dönüşümleri elle yapılır (sayısal kararlılık için max çıkarma).
func softmaxRow(raw any) ([3]float64, error) {
	rows, ok := raw.([]any)
	if !ok {
		return [3]float64{}, fmt.Errorf("ml: logits beklenen dizi değil (%T)", raw)
	}
	if len(rows) == 0 {
		return [3]float64{}, fmt.Errorf("ml: logits boş")
	}
	row, ok := rows[0].([]any)
	if !ok {
		return [3]float64{}, fmt.Errorf("ml: logits satırı beklenen dizi değil (%T)", rows[0])
	}
	if len(row) != 3 {
		return [3]float64{}, fmt.Errorf("ml: logits 3 sınıf olmalı, gerçek %d", len(row))
	}
	logits := [3]float64{}
	for i, v := range row {
		f, ok := v.(float64)
		if !ok {
			return [3]float64{}, fmt.Errorf("ml: logits öğesi sayı değil (%T)", v)
		}
		logits[i] = f
	}

	max := logits[0]
	for _, l := range logits[1:] {
		if l > max {
			max = l
		}
	}
	exp := [3]float64{}
	sum := 0.0
	for i, l := range logits {
		exp[i] = math.Exp(l - max)
		sum += exp[i]
	}
	for i := range exp {
		exp[i] /= sum
	}
	return exp, nil
}

// PromptLabel — prompt sınıflandırıcıda tek hedefin (intent/topic/persona/funnel) tahmini.
type PromptLabel struct {
	Label      string  `json:"label"`
	Confidence float64 `json:"confidence"`
}

// PromptClassification — serving /v1/prompt/classify çıktısı (0421 A2-3).
type PromptClassification struct {
	Intent  PromptLabel `json:"intent"`
	Topic   PromptLabel `json:"topic"`
	Persona PromptLabel `json:"persona"`
	Funnel  PromptLabel `json:"funnel"`
}

// ClassifyPrompt, serving'deki prompt sınıflandırıcıyı (intent/topic/persona/funnel)
// çağırır. Hata (serving yok/model eksik) dönerse çağıran varsayılan ağırlıkları
// kullanır (0421 M-4).
func (c *Client) ClassifyPrompt(ctx context.Context, text string) (*PromptClassification, error) {
	if c == nil {
		return nil, ErrNotConfigured
	}
	var out PromptClassification
	if err := c.postJSON(ctx, "/v1/prompt/classify", map[string]any{"text": text}, &out); err != nil {
		return nil, err
	}
	return &out, nil
}

// HallucinationResponse — serving cross-source tespiti için girdi yanıtı (0421 A2-4).
type HallucinationResponse struct {
	ID     string `json:"id"`
	Engine string `json:"engine"`
	Text   string `json:"text"`
}

// HallucinationFinding — serving /v1/hallucination/detect çıktısı.
type HallucinationFinding struct {
	Type        string  `json:"type"`
	Severity    string  `json:"severity"`
	Description string  `json:"description"`
	Confidence  float64 `json:"confidence"`
	Engine      string  `json:"engine"`
}

// DetectHallucinations, serving'deki cross-source hallüsinasyon tespitini çağırır
// (ml/geolens/features/hallucination.py cross_source_check). En az 2 yanıt gerekir;
// <2 yanıtta çağrı yapılmadan nil döner. Hata dönerse çağıran T1-T4 kural tabanlıya
// düşer (0421 M-4).
func (c *Client) DetectHallucinations(ctx context.Context, responses []HallucinationResponse) ([]HallucinationFinding, error) {
	if c == nil {
		return nil, ErrNotConfigured
	}
	if len(responses) < 2 {
		return nil, nil
	}
	var out struct {
		Findings []HallucinationFinding `json:"findings"`
	}
	if err := c.postJSON(ctx, "/v1/hallucination/detect", map[string]any{"responses": responses}, &out); err != nil {
		return nil, err
	}
	return out.Findings, nil
}

// Health serving API'nin canlılık kontrolüdür.
func (c *Client) Health(ctx context.Context) (map[string]any, error) {
	if c == nil {
		return nil, ErrNotConfigured
	}
	u, err := url.JoinPath(c.baseURL, "/health")
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	var out map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return nil, err
	}
	return out, nil
}
