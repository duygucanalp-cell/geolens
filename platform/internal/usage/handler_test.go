package usage

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

func TestUsageNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestRecordUsage_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/usage/metrics", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordUsage(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordUsage_MissingEndpoint(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{"endpoint": ""})
	req := httptest.NewRequest(http.MethodPost, "/v1/usage/metrics", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordUsage(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordUsage_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{"endpoint": "/v1/health", "method": "GET", "latency_ms": 45})
	req := httptest.NewRequest(http.MethodPost, "/v1/usage/metrics", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordUsage(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestListUsage_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{{"u-1", "/v1/health", "GET", 200, 45, "now"}}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/usage/metrics", nil)
	w := httptest.NewRecorder()
	h.ListUsage(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListUsage_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/usage/metrics", nil)
	w := httptest.NewRecorder()
	h.ListUsage(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", w.Result().StatusCode)
	}
}

func TestGetUsageSummary_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{1000.0, 5.0, 120.0}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"/v1/health", 500, 10.0},
				{"/v1/scores", 300, 200.0},
			}), nil
		},
	})
	req := httptest.NewRequest(http.MethodGet, "/v1/usage/summary", nil)
	w := httptest.NewRecorder()
	h.GetUsageSummary(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}
