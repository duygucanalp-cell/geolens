package perplexity

import (
	"testing"

	"github.com/geolens/platform/engine"
)

func TestAdapter_Name(t *testing.T) {
	a := NewAdapter("test-key", nil)
	if a.Name() != "perplexity" {
		t.Errorf("beklenen 'perplexity', gerçek %s", a.Name())
	}
}

func TestAdapter_Tier(t *testing.T) {
	a := NewAdapter("test-key", nil)
	if a.Tier() != engine.TierDirect {
		t.Errorf("beklenen TierDirect(1), gerçek %d", a.Tier())
	}
}

func TestAdapter_WithContext(t *testing.T) {
	a := NewAdapter("test-key", nil)
	ctxA := a.WithContext("tenant-1", "ws-1")
	if ctxA.Name() != "perplexity" {
		t.Error("WithContext adapter name değiştirmemeli")
	}
}

func TestParseResponse_Success(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{
		"id": "req-123",
		"model": "sonar-pro",
		"choices": [{"index": 0, "message": {"role": "assistant", "content": "Acme pazar lideridir."}}],
		"citations": ["https://example.com"]
	}`)
	resp, err := a.parseResponse(raw, 150)
	if err != nil {
		t.Fatalf("parseResponse hata: %v", err)
	}
	if resp.EngineName != "perplexity" {
		t.Errorf("beklenen 'perplexity', gerçek %s", resp.EngineName)
	}
	if resp.RequestID != "req-123" {
		t.Errorf("beklenen 'req-123', gerçek %s", resp.RequestID)
	}
	if len(resp.Citations) != 1 {
		t.Errorf("beklenen 1 alıntı, gerçek %d", len(resp.Citations))
	}
}

func TestParseResponse_EmptyChoices(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{"id": "req-1", "model": "sonar-pro", "choices": [], "citations": []}`)
	_, err := a.parseResponse(raw, 100)
	if err == nil {
		t.Error("boş choices için hata bekleniyor")
	}
}

func TestParseResponse_InvalidJSON(t *testing.T) {
	a := NewAdapter("test-key", nil)
	_, err := a.parseResponse([]byte(`{invalid`), 100)
	if err == nil {
		t.Error("geçersiz JSON için hata bekleniyor")
	}
}
