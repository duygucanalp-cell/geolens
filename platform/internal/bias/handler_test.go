package bias

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

func TestBiasNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestEvaluate_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/bias/evaluate", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Evaluate(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestEvaluate_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{
		"model_id": "model-1", "metric_type": "demographic_parity",
		"data": map[string]interface{}{"group_a": 0.8, "group_b": 0.6},
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/bias/evaluate", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Evaluate(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}
	var result map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&result)
	score, ok := result["fairness_score"].(float64)
	if !ok {
		t.Fatal("fairness_score not a float64")
	}
	if score < 0.79 || score > 0.81 {
		t.Fatalf("expected ~0.8, got %v", score)
	}
}

func TestListTests_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"test-1", "model-1", "demographic_parity", 0.85, false, 0.15, `{}`, `[]`, "now"},
			{"test-2", "model-2", "equal_opportunity", 0.65, true, 0.35, `{}`, `["retrain"]`, "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/bias/tests", nil)
	w := httptest.NewRecorder()
	h.ListTests(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListTests_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/bias/tests", nil)
	w := httptest.NewRecorder()
	h.ListTests(w, req)
	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestDemographicParity(t *testing.T) {
	h := NewHandler(nil)
	r := h.demographicParity(map[string]interface{}{"group_a": 0.9, "group_b": 0.5})
	fs, _ := r["fairness_score"].(float64)
	if fs != 0.6 {
		t.Fatalf("expected 0.6, got %v", fs)
	}
	hb, _ := r["has_bias"].(bool)
	if !hb {
		t.Fatal("expected bias=true")
	}
}

func TestDemographicParity_Empty(t *testing.T) {
	h := NewHandler(nil)
	r := h.demographicParity(map[string]interface{}{})
	fs, _ := r["fairness_score"].(float64)
	if fs != 1.0 {
		t.Fatalf("expected 1.0, got %v", fs)
	}
}

func TestEqualOpportunity(t *testing.T) {
	h := NewHandler(nil)
	r := h.equalOpportunity(map[string]interface{}{"group_a": 0.95, "group_b": 0.92})
	fs, _ := r["fairness_score"].(float64)
	if fs < 0.96 || fs > 0.98 {
		t.Fatalf("expected ~0.97, got %v", fs)
	}
}

func TestDisparateImpact(t *testing.T) {
	h := NewHandler(nil)
	r := h.disparateImpact(map[string]interface{}{"protected_group_rate": 0.6, "non_protected_group_rate": 0.9})
	fs, _ := r["fairness_score"].(float64)
	if fs != 0.6666666666666666 {
		t.Fatalf("expected ~0.67, got %v", fs)
	}
	fr, _ := r["four_fifths_rule"].(bool)
	if fr {
		t.Fatal("expected four_fifths_rule=false (0.67 < 0.8)")
	}
}

func TestComputeBias_UnknownMetric(t *testing.T) {
	h := NewHandler(nil)
	r := h.computeBias("unknown", nil)
	if r["error"] == nil {
		t.Fatal("expected error for unknown metric")
	}
}

func TestFormatPct(t *testing.T) {
	if formatPct(0.256) != "25.6%" {
		t.Fatalf("got %q", formatPct(0.256))
	}
}
