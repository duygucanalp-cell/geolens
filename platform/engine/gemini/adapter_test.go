package gemini

import (
	"context"
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

func TestAIOverview_WithContext_PreservesWrapper(t *testing.T) {
	a := NewAdapter("test-key", nil)
	overview := a.WithAIOverview("", "")
	if overview.Name() != "google_ai_overview" {
		t.Errorf("beklenen 'google_ai_overview', gerçek %s", overview.Name())
	}
	if overview.Tier() != engine.TierDirectional {
		t.Errorf("AI Overview beklenen TierDirectional(3), gerçek %d", overview.Tier())
	}

	ctxA := overview.WithContext("tenant-1", "ws-1")
	if ctxA.Name() != "google_ai_overview" {
		t.Errorf("WithContext AI Overview wrapper'ı korumalı; beklenen 'google_ai_overview', gerçek %s", ctxA.Name())
	}
	if ctxA.Tier() != engine.TierDirectional {
		t.Errorf("WithContext Tier değiştirmemeli; gerçek %d", ctxA.Tier())
	}
}

func TestAIOverview_Execute_MockMode(t *testing.T) {
	a := NewAdapter("", nil)
	overview := a.WithAIOverview("tenant-1", "ws-1")
	resp, err := overview.Execute(context.Background(), "test prompt")
	if err != nil {
		t.Fatalf("mock AI Overview Execute hata: %v", err)
	}
	if resp.EngineName != "google_ai_overview" {
		t.Errorf("beklenen 'google_ai_overview', gerçek %s", resp.EngineName)
	}
	if resp.Tier != engine.TierDirectional {
		t.Errorf("AI Overview yanıtı TierDirectional(3) olmalı, gerçek %d", resp.Tier)
	}
	if resp.FidelityLabel == "" {
		t.Error("fidelity label boş olmamalı")
	}
}

func TestAIMode_WithContext_PreservesWrapper(t *testing.T) {
	a := NewAdapter("test-key", nil)
	mode := a.WithAIMode("", "")
	if mode.Name() != "google_ai_mode" {
		t.Errorf("beklenen 'google_ai_mode', gerçek %s", mode.Name())
	}
	if mode.Tier() != engine.TierDirectional {
		t.Errorf("AI Mode beklenen TierDirectional(3), gerçek %d", mode.Tier())
	}

	ctxA := mode.WithContext("tenant-1", "ws-1")
	if ctxA.Name() != "google_ai_mode" {
		t.Errorf("WithContext AI Mode wrapper'ı korumalı; beklenen 'google_ai_mode', gerçek %s", ctxA.Name())
	}
	if ctxA.Tier() != engine.TierDirectional {
		t.Errorf("WithContext Tier değiştirmemeli; gerçek %d", ctxA.Tier())
	}
}

func TestAIMode_Execute_MockMode(t *testing.T) {
	a := NewAdapter("", nil)
	mode := a.WithAIMode("tenant-1", "ws-1")
	resp, err := mode.Execute(context.Background(), "test prompt")
	if err != nil {
		t.Fatalf("mock AI Mode Execute hata: %v", err)
	}
	if resp.EngineName != "google_ai_mode" {
		t.Errorf("beklenen 'google_ai_mode', gerçek %s", resp.EngineName)
	}
	if resp.Tier != engine.TierDirectional {
		t.Errorf("AI Mode yanıtı TierDirectional(3) olmalı, gerçek %d", resp.Tier)
	}
	if resp.FidelityLabel == "" {
		t.Error("fidelity label boş olmamalı")
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
	resp, err := a.parseResponse(context.Background(), raw, 150)
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
	resp, err := a.parseResponse(context.Background(), raw, 150)
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
	_, err := a.parseResponse(context.Background(), raw, 100)
	if err == nil {
		t.Error("boş candidates için hata bekleniyor")
	}
}

func TestParseResponse_InvalidJSON(t *testing.T) {
	a := NewAdapter("test-key", nil)
	_, err := a.parseResponse(context.Background(), []byte(`{invalid`), 100)
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
	resp, err := a.Execute(context.Background(), "test prompt")
	if err != nil {
		t.Fatalf("mock Execute hata: %v", err)
	}
	if resp.EngineName != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", resp.EngineName)
	}
}

func TestExecute_MockKey(t *testing.T) {
	a := NewAdapter("mock", nil)
	resp, err := a.Execute(context.Background(), "test prompt")
	if err != nil {
		t.Fatalf("mock Execute hata: %v", err)
	}
	if resp.EngineName != "gemini" {
		t.Errorf("beklenen 'gemini', gerçek %s", resp.EngineName)
	}
}
