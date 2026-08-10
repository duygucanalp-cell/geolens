package drift

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestRecord_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/drift/record", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Record(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecord_MissingFields(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{"entity_id": "", "metric": "score", "value": 10})
	req := httptest.NewRequest(http.MethodPost, "/v1/drift/record", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Record(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecord_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Values: []any{"obs-1", "t-1", "ent-1", "Marka A", "visibility_score", 72.5, "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z"}}
	}})
	body, _ := json.Marshal(map[string]interface{}{"entity_id": "ent-1", "entity_name": "Marka A", "metric": "visibility_score", "value": 72.5})
	req := httptest.NewRequest(http.MethodPost, "/v1/drift/record", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Record(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestListObservations_MissingParams(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/observations?entity_id=x", nil)
	w := httptest.NewRecorder()
	h.ListObservations(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestListObservations_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"obs-1", "t-1", "ent-1", "Marka A", "visibility_score", 72.5, "now", "now"},
			{"obs-2", "t-1", "ent-1", "Marka A", "visibility_score", 70.0, "now", "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/observations?entity_id=ent-1&metric=visibility_score", nil)
	w := httptest.NewRecorder()
	h.ListObservations(w, req)
	var body map[string]interface{}
	json.NewDecoder(w.Result().Body).Decode(&body)
	if len(body["observations"].([]interface{})) != 2 {
		t.Fatalf("expected 2 observations, got %v", body["observations"])
	}
}

func TestListEntities_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"ent-1", "Marka A", "visibility_score", 5, 71.4, "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/entities", nil)
	w := httptest.NewRecorder()
	h.ListEntities(w, req)
	var body map[string]interface{}
	json.NewDecoder(w.Result().Body).Decode(&body)
	entities := body["entities"].([]interface{})
	if len(entities) != 1 {
		t.Fatalf("expected 1 entity, got %d", len(entities))
	}
}

func TestAnalyze_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/analysis?entity_id=e&metric=m", nil)
	w := httptest.NewRecorder()
	h.Analyze(w, req)
	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestAnalyze_InsufficientData(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{float64(10)}, {float64(11)}, {float64(12)},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/analysis?entity_id=e&metric=m", nil)
	w := httptest.NewRecorder()
	h.Analyze(w, req)
	var body map[string]interface{}
	json.NewDecoder(w.Result().Body).Decode(&body)
	if body["severity"] != "insufficient_data" {
		t.Fatalf("expected insufficient_data, got %v", body["severity"])
	}
}

func TestAnalyze_WithDrift(t *testing.T) {
	// 6x10 + 6x50 → referans ortalama 10, güncel ortalama 30 → kritik sapma
	rows := make([][]any, 12)
	for i := 0; i < 12; i++ {
		v := float64(10)
		if i >= 6 {
			v = 50
		}
		rows[i] = []any{v}
	}

	alertCount := 0
	h := NewHandler(&testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows(rows), nil
		},
		ExecFunc: func(_ context.Context, sql string, _ ...any) (dbiface.CommandResult, error) {
			if strings.Contains(sql, "INSERT INTO drift.alerts") {
				alertCount++
			}
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
	})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/analysis?entity_id=e&metric=m", nil)
	w := httptest.NewRecorder()
	h.Analyze(w, req)
	var body map[string]interface{}
	json.NewDecoder(w.Result().Body).Decode(&body)
	if body["drift_score"].(float64) == 0 {
		t.Fatal("expected non-zero drift score")
	}
	if body["severity"] != "critical" {
		t.Fatalf("expected critical severity, got %v", body["severity"])
	}
	if alertCount != 1 {
		t.Fatalf("expected 1 alert insert, got %d", alertCount)
	}
}

func TestListAlerts_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"alert-1", "t-1", "ent-1", "Marka A", "visibility_score", 72.0, "warning", 60.0, 80.0, 20.0, "sapma", "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/drift/alerts", nil)
	w := httptest.NewRecorder()
	h.ListAlerts(w, req)
	var body map[string]interface{}
	json.NewDecoder(w.Result().Body).Decode(&body)
	if len(body["alerts"].([]interface{})) != 1 {
		t.Fatalf("expected 1 alert, got %v", body["alerts"])
	}
}

func TestComputeDriftScore(t *testing.T) {
	// Sabit referans + sabit güncel → maksimum sapma
	score, delta, refMean, curMean := computeDriftScore([]float64{10, 10, 10}, []float64{50, 50, 50})
	if score <= 0 {
		t.Fatalf("expected positive score, got %v", score)
	}
	if delta != 40 {
		t.Fatalf("expected delta 40, got %v", delta)
	}
	if refMean != 10 || curMean != 50 {
		t.Fatalf("expected means 10/50, got %v/%v", refMean, curMean)
	}

	// Aynı değerler → sapma yok
	score2, delta2, _, _ := computeDriftScore([]float64{10, 11, 12}, []float64{10, 11, 12})
	if score2 != 0 || delta2 != 0 {
		t.Fatalf("expected no drift, got score=%v delta=%v", score2, delta2)
	}

	// Boş giriş → 0
	score3, _, _, _ := computeDriftScore(nil, []float64{1, 2})
	if score3 != 0 {
		t.Fatalf("expected 0 for empty ref, got %v", score3)
	}
}

func TestSeverityFor(t *testing.T) {
	if severityFor(10) != "info" {
		t.Fatal("10 should be info")
	}
	if severityFor(30) != "warning" {
		t.Fatal("30 should be warning")
	}
	if severityFor(70) != "critical" {
		t.Fatal("70 should be critical")
	}
}

// TestDriftIdempotencyKey deterministik olmalı: aynı (entity, metric, skor, delta)
// kombinasyonu → aynı anahtar; farklı girdi → farklı anahtar.
func TestDriftIdempotencyKey(t *testing.T) {
	k1 := driftIdempotencyKey("brand-1", "visibility_score", 42.5, 3.25)
	k2 := driftIdempotencyKey("brand-1", "visibility_score", 42.5, 3.25)
	if k1 != k2 {
		t.Fatalf("same input should produce same key: %q vs %q", k1, k2)
	}

	k3 := driftIdempotencyKey("brand-2", "visibility_score", 42.5, 3.25)
	k4 := driftIdempotencyKey("brand-1", "refusal_rate", 42.5, 3.25)
	k5 := driftIdempotencyKey("brand-1", "visibility_score", 60.0, 3.25)
	if k3 == k1 || k4 == k1 || k5 == k1 {
		t.Fatal("different inputs should produce different keys")
	}

	if len(k1) == 0 || k1[:6] != "drift:" {
		t.Fatalf("unexpected key format: %q", k1)
	}
}
