package claude

import (
	"context"
	"encoding/json"
	"testing"
)

func TestName(t *testing.T) {
	a := NewAdapter("mock", nil)
	if a.Name() != "claude" {
		t.Errorf("Name() = %s, want claude", a.Name())
	}
}

func TestTier(t *testing.T) {
	a := NewAdapter("mock", nil)
	if a.Tier() != tier {
		t.Errorf("Tier() = %d, want %d", a.Tier(), tier)
	}
}

func TestExecuteMock(t *testing.T) {
	a := NewAdapter("mock", nil)
	resp, err := a.Execute(context.Background(), "test prompt")
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if resp.EngineName != "claude" {
		t.Errorf("EngineName = %s, want claude", resp.EngineName)
	}
	if resp.Content == "" {
		t.Error("Content is empty")
	}
	if !resp.HasSearch {
		t.Error("HasSearch should be true for mock responses")
	}
}

func TestParseResponse(t *testing.T) {
	a := NewAdapter("", nil)

	raw := `{
		"id": "msg_01abc123",
		"type": "message",
		"role": "assistant",
		"model": "claude-sonnet-4-20260514",
		"content": [
			{"type": "text", "text": "Acme şirketi lider bir teknoloji firmasıdır."}
		],
		"stop_reason": "end_turn",
		"usage": {"input_tokens": 15, "output_tokens": 25}
	}`

	resp, err := a.parseResponse([]byte(raw), 150)
	if err != nil {
		t.Fatalf("parseResponse() error = %v", err)
	}
	if resp.Content != "Acme şirketi lider bir teknoloji firmasıdır. " {
		t.Errorf("Content = %q, want %q", resp.Content, "Acme şirketi lider bir teknoloji firmasıdır. ")
	}
	if resp.RequestID != "msg_01abc123" {
		t.Errorf("RequestID = %s", resp.RequestID)
	}
}

func TestParseResponseWithCitations(t *testing.T) {
	a := NewAdapter("", nil)

	raw := `{
		"id": "msg_cite_001",
		"type": "message",
		"role": "assistant",
		"content": [
			{"type": "text", "text": "Kaynak bilgisi:"},
			{"type": "text", "text": "Acme hakkında detay", "cite": {"type": "citation", "uri": "https://example.com/acme", "title": "Acme Raporu", "start": 1}},
			{"type": "text", "text": "Ek bilgi.", "source": {"type": "url", "url": "https://example.com/ek", "title": "Ek Kaynak"}}
		],
		"stop_reason": "end_turn"
	}`

	resp, err := a.parseResponse([]byte(raw), 200)
	if err != nil {
		t.Fatalf("parseResponse() error = %v", err)
	}
	if len(resp.Citations) != 2 {
		t.Fatalf("expected 2 citations, got %d", len(resp.Citations))
	}
	if resp.Citations[0].URL != "https://example.com/acme" {
		t.Errorf("first citation URL = %s", resp.Citations[0].URL)
	}
	if resp.Citations[1].URL != "https://example.com/ek" {
		t.Errorf("second citation URL = %s", resp.Citations[1].URL)
	}
}

func TestParseResponseInvalidJSON(t *testing.T) {
	a := NewAdapter("", nil)
	_, err := a.parseResponse([]byte(`not json`), 100)
	if err == nil {
		t.Error("expected error for invalid JSON")
	}
}

func TestMarshalRoundTrip(t *testing.T) {
	a := NewAdapter("mock", nil)
	resp, err := a.Execute(context.Background(), "test")
	if err != nil {
		t.Fatal(err)
	}
	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("Marshal error = %v", err)
	}
	if len(data) == 0 {
		t.Error("empty marshal result")
	}
}
