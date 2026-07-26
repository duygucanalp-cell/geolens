package version

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

func TestVersionNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestRecordVersion_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/versions/entries", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordVersion(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordVersion_MissingFields(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"entity_type": "", "entity_id": ""})
	req := httptest.NewRequest(http.MethodPost, "/v1/versions/entries", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordVersion(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRecordVersion_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{
		"entity_type": "model", "entity_id": "mdl-1",
		"entity_name": "gpt-4o", "old_version": "1.0.0", "new_version": "2.0.0",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/versions/entries", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RecordVersion(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestListVersions_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return testutil.NewMockRows([][]any{
			{"v-1", "model", "mdl-1", "gpt-4o", "1.0.0", "2.0.0", "update desc", "user-1", "now"},
		}), nil
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/versions/entries", nil)
	w := httptest.NewRecorder()
	h.ListVersions(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListVersions_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/versions/entries", nil)
	w := httptest.NewRecorder()
	h.ListVersions(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", w.Result().StatusCode)
	}
}

func TestGetVersionDiff_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Err: errors.New("not found")}
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/versions/entries/v-1", nil)
	w := httptest.NewRecorder()
	h.GetVersionDiff(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestGetVersionDiff_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
		return &testutil.MockRow{Values: []any{"v-1", "model", "mdl-1", "gpt-4o", "1.0.0", "2.0.0", "notes", "user-1", "now"}}
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/versions/entries/v-1", nil)
	w := httptest.NewRecorder()
	h.GetVersionDiff(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}
