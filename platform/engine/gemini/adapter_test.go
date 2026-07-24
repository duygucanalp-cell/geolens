package gemini

import (
	"testing"

	"github.com/geolens/platform/engine"
)

func TestAdapter_Name(t *testing.T) {
	a := NewAdapter("test-key", nil)
	if a.Name() != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", a.Name())
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
	if ctxA.Name() != "gemini" {
		t.Error("WithContext adapter name değiştirmemeli")
	}
}

func TestParseResponse_Success(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{
		"candidates": [{
			"content": {
				"parts": [{"text": "Acme pazar lideridir."}]
			},
			"finishReason": "STOP"
		}]
	}`)
	resp, err := a.parseResponse(raw, 150)
	if err != nil {
		t.Fatalf("parseResponse hata: %v", err)
	}
	if resp.EngineName != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", resp.EngineName)
	}
	if resp.Content != "Acme pazar lideridir." {
		t.Errorf("beklenen 'Acme pazar lideridir.', gerçek %s", resp.Content)
	}
}

func TestParseResponse_WithGrounding(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{
		"candidates": [{
			"content": {
				"parts": [{"text": "Kaynaklara gore Acme sektor lideridir."}]
			},
			"finishReason": "STOP",
			"groundingAttributions": [{
				"sourceId": {
					"webSource": {"uri": "https://example.com/acme"}
				},
				"content": {
					"parts": [{"text": "Acme Sektor Raporu 2026"}]
				}
			}]
		}]
	}`)
	resp, err := a.parseResponse(raw, 150)
	if err != nil {
		t.Fatalf("parseResponse hata: %v", err)
	}
	if len(resp.Citations) != 1 {
		t.Errorf("beklenen 1 alıntı, gerçek %d", len(resp.Citations))
	}
	if resp.Citations[0].URL != "https://example.com/acme" {
		t.Errorf("beklenen URL 'https://example.com/acme', gerçek %s", resp.Citations[0].URL)
	}
	if resp.Citations[0].Title != "Acme Sektor Raporu 2026" {
		t.Errorf("beklenen Title 'Acme Sektor Raporu 2026', gerçek %s", resp.Citations[0].Title)
	}
}

func TestParseResponse_EmptyCandidates(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{"candidates":[]}`)
	_, err := a.parseResponse(raw, 100)
	if err == nil {
		t.Error("boş candidates için hata bekleniyor")
	}
}

func TestParseResponse_InvalidJSON(t *testing.T) {
	a := NewAdapter("test-key", nil)
	_, err := a.parseResponse([]byte(`{invalid`), 100)
	if err == nil {
		t.Error("geçersiz JSON için hata bekleniyor")
	}
}

func TestMockResponse(t *testing.T) {
	resp := mockResponse("test prompt")
	if resp.EngineName != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", resp.EngineName)
	}
	if resp.Content == "" {
		t.Error("mock response content boş olmamalı")
	}
	if len(resp.Citations) == 0 {
		t.Error("mock response en az 1 alıntı içermeli")
	}
	if resp.Tier != engine.TierDirect {
		t.Errorf("beklenen TierDirect, gerçek %d", resp.Tier)
	}
}

func TestExecute_MockMode(t *testing.T) {
	a := NewAdapter("", nil)
	resp, err := a.Execute("test prompt")
	if err != nil {
		t.Fatalf("mock Execute hata: %v", err)
	}
	if resp.EngineName != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", resp.EngineName)
	}
}

func TestExecute_MockKey(t *testing.T) {
	a := NewAdapter("mock", nil)
	resp, err := a.Execute("test prompt")
	if err != nil {
		t.Fatalf("mock Execute hata: %v", err)
	}
	if resp.EngineName != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", resp.EngineName)
	}
}
