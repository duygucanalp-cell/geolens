package optimize

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

func TestOptimizeNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestListRecommendations_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"r-1", "measurement", "Test Rec", "description", "high", "low", "pending", 15.0, "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/optimizations/recommendations", nil)
	w := httptest.NewRecorder()
	h.ListRecommendations(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListRecommendations_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/optimizations/recommendations", nil)
	w := httptest.NewRecorder()
	h.ListRecommendations(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", w.Result().StatusCode)
	}
}

func TestGenerateRecommendations_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{3}}
		},
	})
	body, _ := json.Marshal(map[string]interface{}{"auto_save": true})
	req := httptest.NewRequest(http.MethodPost, "/v1/optimizations/recommendations/generate", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.GenerateRecommendations(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestUpdateStatus_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPut, "/v1/optimizations/recommendations/r-1/status", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateStatus(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateStatus_InvalidStatus(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"status": "invalid"})
	req := httptest.NewRequest(http.MethodPut, "/v1/optimizations/recommendations/r-1/status", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateStatus(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateStatus_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
		return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
	}})
	body, _ := json.Marshal(map[string]string{"status": "implemented"})
	req := httptest.NewRequest(http.MethodPut, "/v1/optimizations/recommendations/r-1/status", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateStatus(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestAnalyze(t *testing.T) {
	h := NewHandler(nil)
	r := h.analyze(0)
	if len(r) != 4 {
		t.Fatalf("expected 4 recommendations, got %d", len(r))
	}
	r2 := h.analyze(10)
	if len(r2) != 3 {
		t.Fatalf("expected 3 recommendations (scoreCount>=5 skips measurement), got %d", len(r2))
	}
}
