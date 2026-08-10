package guardrail

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestGuardrailNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestListRules_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/guardrails/rules", nil)
	w := httptest.NewRecorder()
	h.ListRules(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListRules_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"rule-1", "t-1", "SQL Injection", "prompt_injection", "/pattern/", "block", "critical", true, "now", "now"},
			{"rule-2", "t-1", "Email Leak", "pii_leakage", "/email/", "block", "high", true, "now", "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/guardrails/rules", nil)
	w := httptest.NewRecorder()
	h.ListRules(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	rules := body["rules"].([]interface{})
	if len(rules) != 2 {
		t.Fatalf("expected 2 rules, got %d", len(rules))
	}
}

func TestCreateRule_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/guardrails/rules", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreateRule(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreateRule_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Values: []any{"rule-3", "t-1", "Test Rule", "custom", "test", "block", "high", true, "now", "now"}}
	}})
	body, _ := json.Marshal(map[string]string{"name": "Test Rule", "category": "custom", "pattern": "test"})
	req := httptest.NewRequest(http.MethodPost, "/v1/guardrails/rules", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreateRule(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestDeleteRule_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodDelete, "/v1/guardrails/rules/rule-1", nil)
	w := httptest.NewRecorder()
	h.DeleteRule(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestToggle_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPut, "/v1/guardrails/rules/rule-1/toggle", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.ToggleRule(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestEvaluate_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/guardrails/evaluate", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Evaluate(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestEvaluate_PatternMatch(t *testing.T) {
	tests := []struct {
		name    string
		pattern string
		prompt  string
		want    bool
	}{
		{"exact match", "test", "this is a test prompt", true},
		{"no match", "xyz", "hello world", false},
		{"empty pattern", "", "anything", false},
		{"regex match", "/hello/", "say hello world", true},
		{"case insensitive", "/hello/i", "HELLO world", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := evaluateRule(guardRule{Pattern: tt.pattern}, tt.prompt, "")
			if got != tt.want {
				t.Fatalf("evaluateRule(%q, %q) = %v, want %v", tt.pattern, tt.prompt, got, tt.want)
			}
		})
	}
}

func TestCompilePattern(t *testing.T) {
	re, isRegex, err := compilePattern("/test/i")
	if err != nil {
		t.Fatal(err)
	}
	if !isRegex {
		t.Fatal("expected regex")
	}
	if !re.MatchString("TEST") {
		t.Fatal("expected case-insensitive match")
	}

	re2, isRegex2, _ := compilePattern("/test/")
	if !isRegex2 {
		t.Fatal("expected regex")
	}
	if !re2.MatchString("test") {
		t.Fatal("expected match")
	}

	_, isRegex3, _ := compilePattern("plain")
	if isRegex3 {
		t.Fatal("expected not regex")
	}
}

func TestSeedDefaults(t *testing.T) {
	count := 0
	h := NewHandler(&testutil.MockPool{ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
		count++
		return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
	}})
	h.SeedDefaults(httptest.NewRecorder(), httptest.NewRequest(http.MethodPost, "/v1/guardrails/seed-defaults", nil))
	if count != 8 {
		t.Fatalf("expected 8 seed inserts, got %d", count)
	}
}

// TestGuardrailIdempotencyKey deterministik olmalı: aynı girdi → aynı anahtar,
// farklı girdi → farklı anahtar (yinelenen outbox olayını engelleme garantisi).
func TestGuardrailIdempotencyKey(t *testing.T) {
	k1 := guardrailIdempotencyKey("T01", "R1", "prompt a", "response b")
	k2 := guardrailIdempotencyKey("T01", "R1", "prompt a", "response b")
	if k1 != k2 {
		t.Fatalf("same input should produce same key: %q vs %q", k1, k2)
	}

	k3 := guardrailIdempotencyKey("T02", "R1", "prompt a", "response b")
	k4 := guardrailIdempotencyKey("T01", "R2", "prompt a", "response b")
	k5 := guardrailIdempotencyKey("T01", "R1", "prompt b", "response b")
	if k3 == k1 || k4 == k1 || k5 == k1 {
		t.Fatal("different inputs should produce different keys")
	}

	if len(k1) == 0 || k1[:10] != "guardrail:" {
		t.Fatalf("unexpected key format: %q", k1)
	}
}
