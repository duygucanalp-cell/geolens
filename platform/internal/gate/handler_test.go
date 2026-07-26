package gate

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestCheck_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/gate/check", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Check(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCheck_NoEntityID(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"entity_id": "", "entity_type": "model"})
	req := httptest.NewRequest(http.MethodPost, "/v1/gate/check", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Check(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

func TestCheck_AllChecksPassed(t *testing.T) {
	queryRowCount := 0
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			queryRowCount++
			switch queryRowCount {
			case 1:
				return &testutil.MockRow{Values: []any{"ent-001", "model", "production"}}
			case 2:
				return &testutil.MockRow{Values: []any{3}}
			case 3:
				return &testutil.MockRow{Values: []any{"https://docs.example.com"}}
			case 4:
				return &testutil.MockRow{Values: []any{2}}
			case 5:
				return &testutil.MockRow{Values: []any{10, 8}}
			case 6:
				return &testutil.MockRow{Values: []any{3}}
			default:
				return &testutil.MockRow{}
			}
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{
		"entity_id": "ent-001", "entity_type": "model",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/gate/check", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Check(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["decision"] != "approved" {
		t.Fatalf("expected 'approved', got %v", result["decision"])
	}
}

func TestCheck_AllChecksFailed(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"", ""}}
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{
		"entity_id": "nonexistent", "entity_type": "agent",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/gate/check", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Check(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["decision"] != "blocked" {
		t.Fatalf("expected 'blocked', got %v", result["decision"])
	}
}

func TestCheck_PartialPass(t *testing.T) {
	queryRowCount := 0
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			queryRowCount++
			switch queryRowCount {
			case 1:
				return &testutil.MockRow{Values: []any{"ent-001", "model", "staging"}}
			case 2:
				return &testutil.MockRow{Values: []any{0}}
			case 3:
				return &testutil.MockRow{Values: []any{""}}
			case 4:
				return &testutil.MockRow{Values: []any{1}}
			case 5:
				return &testutil.MockRow{Values: []any{4, 3}}
			case 6:
				return &testutil.MockRow{Values: []any{0}}
			default:
				return &testutil.MockRow{}
			}
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{
		"entity_id": "ent-001", "target_environment": "staging",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/gate/check", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Check(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["decision"] != "flagged" {
		t.Fatalf("expected 'flagged', got %v", result["decision"])
	}
}

func TestHistory_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			now := time.Now()
			return testutil.NewMockRows([][]any{
				{"ch-001", "ent-001", "model", "production", "1.0.0", "approved", 6, 6, now},
				{"ch-002", "ent-001", "model", "staging", "1.0.0", "flagged", 4, 6, now.Add(-24 * time.Hour)},
			}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/gate/history/ent-001", nil)
	w := httptest.NewRecorder()
	h.History(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	history := body["history"].([]interface{})
	if len(history) != 2 {
		t.Fatalf("expected 2 history entries, got %d", len(history))
	}
}

func TestHistory_Empty(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/gate/history/ent-001", nil)
	w := httptest.NewRecorder()
	h.History(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	history := body["history"].([]interface{})
	if len(history) != 0 {
		t.Fatalf("expected empty history, got %d", len(history))
	}
	if body["has_more"] != false {
		t.Fatalf("expected has_more=false, got %v", body["has_more"])
	}
}

func TestHistory_QueryError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("db error")
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/gate/history/ent-001", nil)
	w := httptest.NewRecorder()
	h.History(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", resp.StatusCode)
	}
}

func TestHelperFunctions(t *testing.T) {
	if aktifPackStr(1) != "1 pack" {
		t.Fatalf("expected '1 pack', got %q", aktifPackStr(1))
	}
	if aktifPackStr(3) != "3 pack" {
		t.Fatalf("expected '3 pack', got %q", aktifPackStr(3))
	}
	if guardrailSayisi(1) != "1 guardrail" {
		t.Fatalf("expected '1 guardrail', got %q", guardrailSayisi(1))
	}
	if guardrailSayisi(5) != "5 guardrail" {
		t.Fatalf("expected '5 guardrail', got %q", guardrailSayisi(5))
	}
	if controlPct(7, 10) != "%70 ge\u00e7ti" {
		t.Fatalf("expected '%%70 ge\u00e7ti', got %q", controlPct(7, 10))
	}
	if controlPct(0, 5) != "%0 ge\u00e7ti" {
		t.Fatalf("expected '%%0 ge\u00e7ti', got %q", controlPct(0, 5))
	}
}
