package chatgpt

import (
	"context"
	"testing"

	"github.com/geolens/platform/engine"
)

func TestAdapter_Name(t *testing.T) {
	a := NewAdapter("test-key", nil)
	if a.Name() != "chatgpt" {
		t.Errorf("beklenen 'chatgpt', gerçek %s", a.Name())
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
	if ctxA.Name() != "chatgpt" {
		t.Error("WithContext adapter name değiştirmemeli")
	}
}

func TestParseResponse_Success(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{
		"id": "chatcmpl-abc123",
		"object": "chat.completion",
		"model": "gpt-4o",
		"choices": [{
			"index": 0,
			"message": {
				"role": "assistant",
				"content": "Acme pazar lideridir."
			},
			"finish_reason": "stop"
		}]
	}`)
	resp, err := a.parseResponse(context.Background(), raw, 150)
	if err != nil {
		t.Fatalf("parseResponse hata: %v", err)
	}
	if resp.EngineName != "chatgpt" {
		t.Errorf("beklenen 'chatgpt', gerçek %s", resp.EngineName)
	}
	if resp.RequestID != "chatcmpl-abc123" {
		t.Errorf("beklenen 'chatcmpl-abc123', gerçek %s", resp.RequestID)
	}
	if resp.Content != "Acme pazar lideridir." {
		t.Errorf("beklenen 'Acme pazar lideridir.', gerçek %s", resp.Content)
	}
}

func TestParseResponse_WithAnnotations(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{
		"id": "chatcmpl-def456",
		"object": "chat.completion",
		"model": "gpt-4o",
		"choices": [{
			"index": 0,
			"message": {
				"role": "assistant",
				"content": "Kaynaklara göre [Acme](https://example.com/acme) sektör lideridir.",
				"annotations": [
					{
						"type": "url_citation",
						"url_citation": {
							"url": "https://example.com/acme",
							"title": "Acme Sektör Raporu",
							"start_index": 0,
							"end_index": 10
						}
					}
				]
			},
			"finish_reason": "stop"
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
	if resp.Citations[0].Title != "Acme Sektör Raporu" {
		t.Errorf("beklenen Title 'Acme Sektör Raporu', gerçek %s", resp.Citations[0].Title)
	}
}

func TestParseResponse_EmptyChoices(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{"id":"r-1","object":"chat.completion","model":"gpt-4o","choices":[]}`)
	_, err := a.parseResponse(context.Background(), raw, 100)
	if err == nil {
		t.Error("boş choices için hata bekleniyor")
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
	if resp.EngineName != "chatgpt" {
		t.Errorf("beklenen 'chatgpt', gerçek %s", resp.EngineName)
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
	if resp.EngineName != "chatgpt" {
		t.Errorf("beklenen 'chatgpt', gerçek %s", resp.EngineName)
	}
}

func TestExecute_MockKey(t *testing.T) {
	a := NewAdapter("mock", nil)
	resp, err := a.Execute(context.Background(), "test prompt")
	if err != nil {
		t.Fatalf("mock Execute hata: %v", err)
	}
	if resp.EngineName != "chatgpt" {
		t.Errorf("beklenen 'chatgpt', gerçek %s", resp.EngineName)
	}
}
