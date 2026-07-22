package perplexity

import (
	"github.com/geolens/platform/engine"
)

// Tier constant
const tier = engine.TierDirect // Perplexity Sonar API — Kademe 1 (direct)

// Adapter implements engine.Adapter for Perplexity Sonar API.
type Adapter struct {
	apiKey string
}

// NewAdapter creates a new Perplexity adapter.
func NewAdapter(apiKey string) *Adapter {
	return &Adapter{
		apiKey: apiKey,
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

// Execute sends a prompt to Perplexity Sonar API.
// API çağrısı + yanıt ayrıştırma tek adımda yapılır.
// Dilim 1 H1'de detaylandırılacak: gerçek API çağrısı, alıntı çıkarma, hata sınıfları.
func (a *Adapter) Execute(prompt string) (*engine.RawResponse, error) {
	// TODO(H1): Gerçek Perplexity Sonar API çağrısı + parseResponse çağrısı
	return a.parseResponse(nil)
}

// parseResponse parses a raw Perplexity API response into RawResponse.
// Private metod — Adapter arayüzünde değil, yalnızca Execute içinden çağrılır.
func (a *Adapter) parseResponse(raw []byte) (*engine.RawResponse, error) {
	// TODO(H1): JSON yanıt ayrıştırma, alıntı çıkarma, hata sınıfları
	return nil, nil
}
