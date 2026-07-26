package benchmark

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

func TestBenchmarkNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestRunBenchmark_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/benchmarks/models", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RunBenchmark(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRunBenchmark_MissingFields(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"model_name": "", "engine_name": ""})
	req := httptest.NewRequest(http.MethodPost, "/v1/benchmarks/models", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RunBenchmark(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRunBenchmark_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{
		"model_name": "gpt-4o", "engine_name": "chatgpt",
		"accuracy_score": 0.95, "latency_ms": 200,
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/benchmarks/models", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RunBenchmark(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}
	var r map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&r)
	if r["model_name"] != "gpt-4o" {
		t.Fatalf("got %v", r["model_name"])
	}
}

func TestListBenchmarks_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"b-1", "gpt-4o", "chatgpt", "llm", 0.95, 200, 0.001, 50.0, 4.5, 0.8, "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/benchmarks/models", nil)
	w := httptest.NewRecorder()
	h.ListBenchmarks(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListBenchmarks_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/benchmarks/models", nil)
	w := httptest.NewRecorder()
	h.ListBenchmarks(w, req)
	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestCompareModels_MissingEngines(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodGet, "/v1/benchmarks/compare", nil)
	w := httptest.NewRecorder()
	h.CompareModels(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCompareModels_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"chatgpt", "gpt-4o", 0.95, 200, 0.001, 50.0, 4.5, 0.8, "now"},
			{"perplexity", "sonar-pro", 0.88, 350, 0.005, 30.0, 4.2, 0.9, "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/benchmarks/compare?engines=chatgpt,perplexity", nil)
	w := httptest.NewRecorder()
	h.CompareModels(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}
