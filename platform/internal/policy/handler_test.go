package policy

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestListPacks_QueryError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("db error")
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/packs", nil)
	w := httptest.NewRecorder()
	h.ListPacks(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", resp.StatusCode)
	}
}

func TestListPacks_Empty(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/packs", nil)
	w := httptest.NewRecorder()
	h.ListPacks(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

func TestListPacks_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{
					"pack-1", "tenant-1", "EU AI Act Compliance", "eu_ai_act",
					"EU AI Act description", "1.0.0", true, testutil.StrPtr("2026-07-25T00:00:00Z"),
					"2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z",
				},
				{
					"pack-2", "tenant-1", "NIST AI RMF", "nist_ai_rmf",
					"NIST description", "1.0.0", true, (*string)(nil),
					"2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z",
				},
			}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/packs", nil)
	w := httptest.NewRecorder()
	h.ListPacks(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	packs := body["packs"].([]interface{})
	if len(packs) != 2 {
		t.Fatalf("expected 2 packs, got %d", len(packs))
	}
}

func TestListControls_QueryError(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("db error")
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/packs/pack-1/controls", nil)
	w := httptest.NewRecorder()
	h.ListControls(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", resp.StatusCode)
	}
}

func TestListControls_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{
					"ctrl-1", "pack-1", "tenant-1", "Art.9",
					"Risk Y\u00f6netim Sistemi", "S\u00fcrekli risk y\u00f6netim s\u00fcreci",
					"Risk Management", "passed", "evidence doc", nil,
					"2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z",
				},
				{
					"ctrl-2", "pack-1", "tenant-1", "Art.10",
					"E\u011fitim Verisi Y\u00f6netimi", "Veri kalitesi ve bias",
					"Data Governance", "pending", "", nil,
					"2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z",
				},
			}), nil
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/packs/pack-1/controls", nil)
	w := httptest.NewRecorder()
	h.ListControls(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	controls := body["controls"].([]interface{})
	if len(controls) != 2 {
		t.Fatalf("expected 2 controls, got %d", len(controls))
	}
}

func TestApplyPack_NotFound(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("not found")}
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodPost, "/v1/policies/packs/nonexistent/apply", nil)
	w := httptest.NewRecorder()
	h.ApplyPack(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestApplyPack_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{
				Values: []any{
					"pack-1", "tenant-1", "EU AI Act Compliance", "eu_ai_act",
					"Description", "1.0.0", true, testutil.StrPtr("2026-07-25T00:00:00Z"),
					"2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z",
				},
			}
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodPost, "/v1/policies/packs/pack-1/apply", nil)
	w := httptest.NewRecorder()
	h.ApplyPack(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

func TestUpdateControl_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPut, "/v1/policies/controls/ctrl-1", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateControl(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateControl_Success(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{"status": "passed", "evidence": "test evidence"})
	req := httptest.NewRequest(http.MethodPut, "/v1/policies/controls/ctrl-1", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateControl(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestUpdateControl_DBError(t *testing.T) {
	pool := &testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return nil, errors.New("db error")
		},
	}
	h := NewHandler(pool)
	body, _ := json.Marshal(map[string]string{"status": "failed"})
	req := httptest.NewRequest(http.MethodPut, "/v1/policies/controls/ctrl-1", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateControl(w, req)
	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestSeedPacks_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"pack-seeded-1"}}
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodPost, "/v1/policies/seed", nil)
	w := httptest.NewRecorder()
	h.SeedPacks(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestGetCompliance_Success(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, args ...any) dbiface.RowScanner {
			if len(args) == 2 {
				return &testutil.MockRow{Values: []any{"high"}}
			}
			return &testutil.MockRow{Values: []any{10, 7, 2, 1}}
		},
	}
	h := NewHandler(pool)
	chiCtx := chi.NewRouteContext()
	chiCtx.URLParams.Add("entityId", "ent-001")
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/compliance/ent-001", nil)
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, chiCtx))
	w := httptest.NewRecorder()
	h.GetCompliance(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
	var body map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&body)
	if body["total_controls"].(float64) != 10 {
		t.Fatalf("expected total_controls 10, got %v", body["total_controls"])
	}
	if body["entity_risk_class"] != "high" {
		t.Fatalf("expected entity_risk_class 'high', got %v", body["entity_risk_class"])
	}
}

func TestGetCompliance_NoEntityID(t *testing.T) {
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{5, 3, 1, 1}}
		},
	}
	h := NewHandler(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/policies/compliance/", nil)
	w := httptest.NewRecorder()
	h.GetCompliance(w, req)
	resp := w.Result()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", resp.StatusCode)
	}
}

func TestFrameworkControls(t *testing.T) {
	tests := []struct {
		name      string
		framework string
		minCtls   int
	}{
		{"EU AI Act", "eu_ai_act", 7},
		{"NIST AI RMF", "nist_ai_rmf", 7},
		{"KVKK", "kvkk", 6},
		{"ISO 42001", "iso_42001", 6},
		{"Custom", "custom", 1},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctls := frameworkControls(tt.framework)
			if len(ctls) < tt.minCtls {
				t.Fatalf("%s: expected at least %d controls, got %d", tt.framework, tt.minCtls, len(ctls))
			}
		})
	}
}

func TestSeedDefaultPacks_AllFrameworks(t *testing.T) {
	called := 0
	pool := &testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			called++
			return &testutil.MockRow{Values: []any{"pack-" + string(rune('0'+called))}}
		},
	}
	SeedDefaultPacks(context.Background(), pool, "tenant-1")
	if called != 4 {
		t.Fatalf("expected 4 framework seeds, got %d", called)
	}
}
