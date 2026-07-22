package engine

// ---- Domain Types ----

// Tier represents the access tier of an AI engine.
type Tier int

const (
	TierDirect        Tier = 1 // Doğrudan API erişimi (Kademe 1)
	TierOfficialProxy Tier = 2 // Resmî API vekili (Kademe 2)
	TierDirectional   Tier = 3 // Yönsel / tahmini (Kademe 3)
)

// RawResponse is the raw response from a single AI engine call.
type RawResponse struct {
	EngineName  string `json:"engine_name"`
	RequestID   string `json:"request_id"`
	Content     string `json:"content"`
	Citations   []Citation `json:"citations,omitempty"`
	HasSearch   bool   `json:"has_search"`
	Tier        Tier   `json:"tier"`
	FidelityLabel string `json:"fidelity_label"`
	S3Ref       string `json:"s3_ref,omitempty"`
}

// Citation represents a single citation extracted from an AI response.
type Citation struct {
	URL      string `json:"url"`
	Title    string `json:"title"`
	Position int    `json:"position"`
	Engine   string `json:"engine"`
	Domain   string `json:"domain"`
	Type     string `json:"type"` // direct, attribution, directional
}

// EngineMeta contains metadata about the engine call.
type EngineMeta struct {
	EngineName   string `json:"engine_name"`
	ModelVersion string `json:"model_version"`
	Tier         Tier   `json:"tier"`
	DurationMs   int64  `json:"duration_ms"`
}

// ---- Engine Adapter Interface ----

// Adapter defines the interface that all AI engine adapters must implement.
// Her adapter, Execute çağrısı içinde API isteğini yapar ve yanıtı ayrıştırır.
// Ham JSON ayrıştırma adapter içinde private metod olarak kalır, dışa açılmaz.
type Adapter interface {
	// Name returns the unique engine name (e.g., "chatgpt", "gemini", "perplexity").
	Name() string

	// Tier returns the access tier of this engine.
	Tier() Tier

	// Execute sends a prompt to the AI engine and returns the normalized response.
	// API çağrısı + yanıt ayrıştırma tek adımda yapılır.
	Execute(prompt string) (*RawResponse, error)
}

// ---- Engine Registry ----

// Registry manages registered engine adapters.
type Registry struct {
	adapters map[string]Adapter
}

// NewRegistry creates a new engine registry.
func NewRegistry() *Registry {
	return &Registry{
		adapters: make(map[string]Adapter),
	}
}

// Register adds an engine adapter to the registry.
func (r *Registry) Register(a Adapter) {
	r.adapters[a.Name()] = a
}

// Get returns an adapter by name. Returns nil if not found.
func (r *Registry) Get(name string) Adapter {
	return r.adapters[name]
}

// List returns all registered engine names.
func (r *Registry) List() []string {
	names := make([]string, 0, len(r.adapters))
	for name := range r.adapters {
		names = append(names, name)
	}
	return names
}

// Count returns the number of registered adapters.
func (r *Registry) Count() int {
	return len(r.adapters)
}
