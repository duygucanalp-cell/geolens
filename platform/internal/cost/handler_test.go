package cost

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

func TestCostNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestRecordCost_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/costs/entries", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordCost(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordCost_MissingEngine(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{"engine_name": "", "cost_usd": 0.5})
	req := httptest.NewRequest(http.MethodPost, "/v1/costs/entries", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordCost(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordCost_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{"engine_name": "chatgpt", "cost_usd": 0.05, "token_count": 500})
	req := httptest.NewRequest(http.MethodPost, "/v1/costs/entries", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordCost(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestListCosts_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"c-1", "chatgpt", "gpt-4o", "inference", 500, 0.05, "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/costs/entries", nil)
	w := httptest.NewRecorder()
	h.ListCosts(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListCosts_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/costs/entries", nil)
	w := httptest.NewRecorder()
	h.ListCosts(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", w.Result().StatusCode)
	}
}

func TestGetCostSummary_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{150.0, 50000}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"chatgpt", 100.0, 30000},
				{"perplexity", 50.0, 20000},
			}), nil
		},
	})
	req := httptest.NewRequest(http.MethodGet, "/v1/costs/summary", nil)
	w := httptest.NewRecorder()
	h.GetCostSummary(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var r map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&r)
	if r["total_cost_usd"].(float64) != 150.0 {
		t.Fatalf("got %v", r["total_cost_usd"])
	}
}
