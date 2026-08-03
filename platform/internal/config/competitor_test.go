package config

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
	"github.com/geolens/platform/platform/httpmw"
)

// contextWithAuth injects tenant_id and workspace_id into context.
func contextWithAuth(tenantID, workspaceID string) context.Context {
	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, tenantID)
	ctx = context.WithValue(ctx, httpmw.CtxKeyWorkspaceID, workspaceID)
	return ctx
}

// withChiParams adds chi URL route context with the given params.
func withChiParams(ctx context.Context, params ...string) context.Context {
	chiCtx := chi.NewRouteContext()
	for i := 0; i+1 < len(params); i += 2 {
		chiCtx.URLParams.Add(params[i], params[i+1])
	}
	return context.WithValue(ctx, chi.RouteCtxKey, chiCtx)
}

// newCompetitorRequest creates an HTTP request with the given method, path, body, and context.
func newCompetitorRequest(method string, body []byte, ctx context.Context) *http.Request {
	req := httptest.NewRequest(method, "/", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	return req.WithContext(ctx)
}

// ---------------------------------------------------------------------------
// ListBrandCompetitors — GET /brands/{brandId}/competitors
// ---------------------------------------------------------------------------

func TestListBrandCompetitors_Success(t *testing.T) {
	now := time.Now()
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"comp-1", "Rakip A", now},
				{"comp-2", "Rakip B", now},
			}), nil
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodGet, nil, ctx)
	w := httptest.NewRecorder()
	h.ListBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp []map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if len(resp) != 2 {
		t.Fatalf("expected 2 competitors, got %d", len(resp))
	}
	if resp[0]["competitor_id"] != "comp-1" || resp[0]["competitor_name"] != "Rakip A" {
		t.Fatalf("unexpected first competitor: %+v", resp[0])
	}
}

func TestListBrandCompetitors_Empty(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows(nil), nil
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodGet, nil, ctx)
	w := httptest.NewRecorder()
	h.ListBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp []interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if len(resp) != 0 {
		t.Fatalf("expected empty list, got %d items", len(resp))
	}
}

func TestListBrandCompetitors_BrandNotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{false}}
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "unknown")
	req := newCompetitorRequest(http.MethodGet, nil, ctx)
	w := httptest.NewRecorder()
	h.ListBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestListBrandCompetitors_QueryRowError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("db error")}
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodGet, nil, ctx)
	w := httptest.NewRecorder()
	h.ListBrandCompetitors(w, req)

	// QueryRow hatası olduğunda brandExists false olur → 404
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestListBrandCompetitors_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("db error")
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodGet, nil, ctx)
	w := httptest.NewRecorder()
	h.ListBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestListBrandCompetitors_MissingBrandID(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	// brandId empty — chi.URLParam returns ""
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "")
	req := newCompetitorRequest(http.MethodGet, nil, ctx)
	w := httptest.NewRecorder()
	h.ListBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

// ---------------------------------------------------------------------------
// UpdateBrandCompetitors — PUT /brands/{brandId}/competitors
// ---------------------------------------------------------------------------

func TestUpdateBrandCompetitors_Success(t *testing.T) {
	var txStarted bool
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}} // brand exists
		},
		BeginFunc: func(_ context.Context) (dbiface.Tx, error) {
			txStarted = true
			return &testutil.MockTx{
				ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
					return testutil.MockCommandResult{RowsAffectedVal: 2}, nil
				},
				QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
					return &testutil.MockRow{Values: []any{true}} // competitor exists
				},
				CommitFunc: func(_ context.Context) error {
					return nil
				},
			}, nil
		},
	})

	body, _ := json.Marshal(map[string]interface{}{
		"competitors": []string{"comp-1", "comp-2"},
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
	if !txStarted {
		t.Fatal("expected transaction to be started")
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if resp["status"] != "updated" {
		t.Fatalf("expected status 'updated', got %v", resp["status"])
	}
}

func TestUpdateBrandCompetitors_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}} // brand exists
		},
	})
	// Valid brandId but invalid JSON body — should fail at JSON decode, not brandID check
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, []byte("not json"), ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrandCompetitors_BrandNotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{false}}
		},
	})

	body, _ := json.Marshal(map[string]interface{}{
		"competitors": []string{"comp-1"},
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "unknown")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrandCompetitors_CompetitorNotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}} // brand exists
		},
		BeginFunc: func(_ context.Context) (dbiface.Tx, error) {
			return &testutil.MockTx{
				ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
					return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
				},
				QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
					return &testutil.MockRow{Values: []any{false}}
				},
				RollbackFunc: func(_ context.Context) error {
					return nil
				},
			}, nil
		},
	})

	body, _ := json.Marshal(map[string]interface{}{
		"competitors": []string{"nonexistent"},
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrandCompetitors_BeginError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}}
		},
		BeginFunc: func(_ context.Context) (dbiface.Tx, error) {
			return nil, errors.New("begin error")
		},
	})

	body, _ := json.Marshal(map[string]interface{}{
		"competitors": []string{"comp-1"},
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrandCompetitors_CommitError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{true}}
		},
		BeginFunc: func(_ context.Context) (dbiface.Tx, error) {
			return &testutil.MockTx{
				ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
					return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
				},
				QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
					return &testutil.MockRow{Values: []any{true}}
				},
				CommitFunc: func(_ context.Context) error {
					return errors.New("commit error")
				},
				RollbackFunc: func(_ context.Context) error {
					return nil
				},
			}, nil
		},
	})

	body, _ := json.Marshal(map[string]interface{}{
		"competitors": []string{"comp-1"},
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrandCompetitors_MissingBrandID(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	body, _ := json.Marshal(map[string]interface{}{
		"competitors": []string{"comp-1"},
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrandCompetitors(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

// ---------------------------------------------------------------------------
// DeleteBrandCompetitor — DELETE /brands/{brandId}/competitors/{competitorId}
// ---------------------------------------------------------------------------

func TestDeleteBrandCompetitor_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01", "competitorId", "C01")
	req := newCompetitorRequest(http.MethodDelete, nil, ctx)
	w := httptest.NewRecorder()
	h.DeleteBrandCompetitor(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if resp["status"] != "deleted" {
		t.Fatalf("expected status 'deleted', got %v", resp["status"])
	}
	if resp["competitor_id"] != "C01" {
		t.Fatalf("expected competitor_id 'C01', got %v", resp["competitor_id"])
	}
}

func TestDeleteBrandCompetitor_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01", "competitorId", "nonexistent")
	req := newCompetitorRequest(http.MethodDelete, nil, ctx)
	w := httptest.NewRecorder()
	h.DeleteBrandCompetitor(w, req)

	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestDeleteBrandCompetitor_DBError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return nil, errors.New("db error")
		},
	})

	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01", "competitorId", "C01")
	req := newCompetitorRequest(http.MethodDelete, nil, ctx)
	w := httptest.NewRecorder()
	h.DeleteBrandCompetitor(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestDeleteBrandCompetitor_SelfReference(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	// brandId == competitorId → self-reference check
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01", "competitorId", "B01")
	req := newCompetitorRequest(http.MethodDelete, nil, ctx)
	w := httptest.NewRecorder()
	h.DeleteBrandCompetitor(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400 for self-reference, got %d", w.Result().StatusCode)
	}
}

func TestDeleteBrandCompetitor_MissingParams(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	// Both empty → validation error
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "", "competitorId", "")
	req := newCompetitorRequest(http.MethodDelete, nil, ctx)
	w := httptest.NewRecorder()
	h.DeleteBrandCompetitor(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

// ---------------------------------------------------------------------------
// UpdateBrand — PUT /brands/{brandId}
// ---------------------------------------------------------------------------

func TestUpdateBrand_Success_NameOnly(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"B01", "Yeni Marka", "https://example.com"}}
		},
	})

	body, _ := json.Marshal(map[string]string{
		"name": "Yeni Marka",
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if resp["name"] != "Yeni Marka" {
		t.Fatalf("expected name 'Yeni Marka', got %v", resp["name"])
	}
	if resp["id"] != "B01" {
		t.Fatalf("expected id 'B01', got %v", resp["id"])
	}
}

func TestUpdateBrand_Success_BothFields(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"B01", "Yeni Marka", "https://yeni-site.com"}}
		},
	})

	body, _ := json.Marshal(map[string]string{
		"name":        "Yeni Marka",
		"website_url": "https://yeni-site.com",
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if resp["name"] != "Yeni Marka" {
		t.Fatalf("expected name 'Yeni Marka', got %v", resp["name"])
	}
	if resp["website_url"] != "https://yeni-site.com" {
		t.Fatalf("expected website_url 'https://yeni-site.com', got %v", resp["website_url"])
	}
}

func TestUpdateBrand_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, []byte("not json"), ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrand_NoFields(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrand_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
		},
	})

	body, _ := json.Marshal(map[string]string{
		"name": "Olmayan Marka",
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "nonexistent")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrand_DBError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return nil, errors.New("db error")
		},
	})

	body, _ := json.Marshal(map[string]string{
		"name": "Yeni Marka",
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrand_ReadBackError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
			return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
		},
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("db error")}
		},
	})

	body, _ := json.Marshal(map[string]string{
		"name": "Yeni Marka",
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "B01")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestUpdateBrand_MissingBrandID(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{
		"name": "Yeni Marka",
	})
	ctx := withChiParams(contextWithAuth("T01", "WS01"), "brandId", "")
	req := newCompetitorRequest(http.MethodPut, body, ctx)
	w := httptest.NewRecorder()
	h.UpdateBrand(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

// ---------------------------------------------------------------------------
// SearchBrands — GET /brands/search?q=...&exclude=...
// ---------------------------------------------------------------------------

func TestSearchBrands_Success(t *testing.T) {
	countCalled := false
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			countCalled = true
			return &testutil.MockRow{Values: []any{2}} // total count
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"B01", "Acme Corp", "https://acme.com"},
				{"B02", "Acme Ltd", "https://acme-ltd.com"},
			}), nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	if !countCalled {
		t.Fatal("expected count query to be called")
	}

	data, ok := resp["data"].([]interface{})
	if !ok {
		t.Fatal("expected response to have 'data' array")
	}
	if len(data) != 2 {
		t.Fatalf("expected 2 brands, got %d", len(data))
	}

	first := data[0].(map[string]interface{})
	if first["id"] != "B01" || first["name"] != "Acme Corp" {
		t.Fatalf("unexpected first brand: %+v", first)
	}

	second := data[1].(map[string]interface{})
	if second["id"] != "B02" || second["name"] != "Acme Ltd" {
		t.Fatalf("unexpected second brand: %+v", second)
	}

	if resp["total"] != float64(2) {
		t.Fatalf("expected total 2, got %v", resp["total"])
	}
	if resp["offset"] != float64(0) {
		t.Fatalf("expected offset 0, got %v", resp["offset"])
	}
	if resp["limit"] != float64(20) {
		t.Fatalf("expected limit 20, got %v", resp["limit"])
	}
}

func TestSearchBrands_EmptyResults(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{0}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows(nil), nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=nonexistent", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	data, ok := resp["data"].([]interface{})
	if !ok {
		t.Fatalf("expected 'data' array, got %T", resp["data"])
	}
	if len(data) != 0 {
		t.Fatalf("expected empty list, got %d items", len(data))
	}
	if resp["total"] != float64(0) {
		t.Fatalf("expected total 0, got %v", resp["total"])
	}
}

func TestSearchBrands_MissingQuery(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}
	if resp["error"] != "q parametresi gerekli" {
		t.Fatalf("expected 'q parametresi gerekli', got %v", resp["error"])
	}
}

func TestSearchBrands_MissingQueryEmptyParam(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestSearchBrands_CountError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("count error")}
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestSearchBrands_DBError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{0}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return nil, errors.New("db error")
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Result().StatusCode)
	}
}

func TestSearchBrands_WithExclude(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{1}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"B02", "Acme Ltd", "https://acme-ltd.com"},
			}), nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme&exclude=B01", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	data, ok := resp["data"].([]interface{})
	if !ok {
		t.Fatalf("expected 'data' array, got %T", resp["data"])
	}
	if len(data) != 1 {
		t.Fatalf("expected 1 brand, got %d", len(data))
	}
	first := data[0].(map[string]interface{})
	if first["id"] != "B02" {
		t.Fatalf("expected brand B02, got %v", first["id"])
	}
	if resp["total"] != float64(1) {
		t.Fatalf("expected total 1, got %v", resp["total"])
	}
}

// errScanRows wraps MockRows and returns an error on all Scan calls.
type errScanRows struct {
	testutil.MockRows
}

func (e *errScanRows) Scan(dest ...any) error {
	return errors.New("scan error")
}

func TestSearchBrands_WithPagination(t *testing.T) {
	countCalled := false
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			countCalled = true
			return &testutil.MockRow{Values: []any{2}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"B01", "Acme Corp", "https://acme.com"},
			}), nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme&offset=1&limit=1", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	if !countCalled {
		t.Fatal("expected count query to be called")
	}

	if resp["offset"] != float64(1) {
		t.Fatalf("expected offset 1, got %v", resp["offset"])
	}
	if resp["limit"] != float64(1) {
		t.Fatalf("expected limit 1, got %v", resp["limit"])
	}
	if resp["total"] != float64(2) {
		t.Fatalf("expected total 2, got %v", resp["total"])
	}

	data, ok := resp["data"].([]interface{})
	if !ok {
		t.Fatalf("expected 'data' array, got %T", resp["data"])
	}
	if len(data) != 1 {
		t.Fatalf("expected 1 brand, got %d", len(data))
	}
}

func TestSearchBrands_WithLargeLimit(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{150}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"B01", "Acme Corp", "https://acme.com"},
			}), nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	// limit=200 should be capped to 100
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme&limit=200", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	if resp["limit"] != float64(100) {
		t.Fatalf("expected limit capped at 100, got %v", resp["limit"])
	}
}

func TestSearchBrands_WithInvalidOffset(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{0}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows(nil), nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	// Negative offset should be treated as 0
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme&offset=-5", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	if resp["offset"] != float64(0) {
		t.Fatalf("expected offset defaulted to 0, got %v", resp["offset"])
	}
}

func TestSearchBrands_ScanError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{0}}
		},
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return &errScanRows{MockRows: *testutil.NewMockRows([][]any{{}})}, nil
		},
	})

	ctx := contextWithAuth("T01", "WS01")
	req := httptest.NewRequest(http.MethodGet, "/?q=Acme", nil).WithContext(ctx)
	w := httptest.NewRecorder()
	h.SearchBrands(w, req)

	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}

	var resp map[string]interface{}
	if err := json.NewDecoder(w.Result().Body).Decode(&resp); err != nil {
		t.Fatalf("json decode hatası: %v", err)
	}

	data, ok := resp["data"].([]interface{})
	if !ok {
		t.Fatalf("expected 'data' array, got %T", resp["data"])
	}
	if len(data) != 0 {
		t.Fatalf("expected empty list due to scan error, got %d items", len(data))
	}
}
