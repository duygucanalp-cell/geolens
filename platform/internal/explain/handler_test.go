package explain

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestExplainNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestExplain_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Err: errors.New("not found")}
	}})
	req := httptest.NewRequest(http.MethodPost, "/v1/explain/ent-001", nil)
	w := httptest.NewRecorder()
	h.Explain(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestExplain_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, args ...any) dbiface.RowScanner {
			if len(args) == 2 {
				return &testutil.MockRow{Values: []any{"Test Entity", "model", "openai", "high", 0.75}}
			}
			return &testutil.MockRow{Err: errors.New("no scores")}
		},
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/explain/ent-001", nil)
	w := httptest.NewRecorder()
	h.Explain(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	if result["entity_name"] != "Test Entity" {
		t.Fatalf("got %v", result["entity_name"])
	}
	if result["entity_type"] != "model" {
		t.Fatalf("got %v", result["entity_type"])
	}
}

func TestListAnalyses_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"a-1", "ent-001", "SHAP", 50.0, 65.4, `{"f1":0.5}`, `[{"feature":"f1","shap":15.4}]`, "High score due to visibility", "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/explain/results", nil)
	w := httptest.NewRecorder()
	h.ListAnalyses(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListAnalyses_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/explain/results", nil)
	w := httptest.NewRecorder()
	h.ListAnalyses(w, req)
	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestComputeFeatureImportance(t *testing.T) {
	h := NewHandler(nil)
	w := h.computeFeatureImportance("high", 0.75)
	if w["citation_accuracy"] != 0.30 {
		t.Fatalf("high risk: expected citation_accuracy 0.30, got %v", w["citation_accuracy"])
	}
	w2 := h.computeFeatureImportance("low", 0.75)
	if w2["citation_accuracy"] != 0.20 {
		t.Fatalf("low risk: expected citation_accuracy 0.20, got %v", w2["citation_accuracy"])
	}
}

func TestComputeFeatureImportance_LowConfidence(t *testing.T) {
	h := NewHandler(nil)
	w := h.computeFeatureImportance("low", 0.3)
	if w["brand_consistency"] >= 0.12 {
		t.Fatalf("low confidence: expected brand_consistency < 0.12, got %v", w["brand_consistency"])
	}
}

func TestComputeShapValues(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Values: []any{75.0}}
	}})
	sv := h.computeShapValues(context.Background(), "ent-001", "high", 0.8)
	if len(sv) != 5 {
		t.Fatalf("expected 5 shap values, got %d", len(sv))
	}
	shap2, _ := sv[2]["shap"].(float64)
	if shap2 != -5.8 {
		t.Fatalf("high risk: expected shap -5.8, got %v", shap2)
	}

	sv2 := h.computeShapValues(context.Background(), "ent-002", "low", 0.5)
	shap2v, _ := sv2[2]["shap"].(float64)
	if shap2v != -3.2 {
		t.Fatalf("low risk: expected shap -3.2, got %v", shap2v)
	}
}
