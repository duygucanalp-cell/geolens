package incident

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

func TestIncidentNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("nil")
	}
}

func TestListIncidents_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{
		QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"i-1", "critical", "outage", "API Down", "open", "monitoring", "ent-1", "user-1", 9.5, "now", nil, "now"},
			}), nil
		},
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{1}}
		},
	})
	req := httptest.NewRequest(http.MethodGet, "/v1/incidents/events", nil)
	w := httptest.NewRecorder()
	h.ListIncidents(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}

func TestListIncidents_QueryError(t *testing.T) {
	h := NewHandler(&testutil.MockPool{QueryFunc: func(_ context.Context, _ string, _ ...any) (dbiface.RowsIter, error) {
		return nil, errors.New("db error")
	}})
	req := httptest.NewRequest(http.MethodGet, "/v1/incidents/events", nil)
	w := httptest.NewRecorder()
	h.ListIncidents(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200 (graceful), got %d", w.Result().StatusCode)
	}
}

func TestCreateIncident_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPost, "/v1/incidents/events", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreateIncident(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreateIncident_MissingTitle(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{"title": "", "severity": "high"})
	req := httptest.NewRequest(http.MethodPost, "/v1/incidents/events", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreateIncident(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreateIncident_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]interface{}{
		"title": "API Outage", "severity": "critical", "category": "outage",
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/incidents/events", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreateIncident(w, req)
	if w.Result().StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", w.Result().StatusCode)
	}
}

func TestUpdateIncident_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	req := httptest.NewRequest(http.MethodPut, "/v1/incidents/events/i-1", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateIncident(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateIncident_InvalidStatus(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"status": "invalid"})
	req := httptest.NewRequest(http.MethodPut, "/v1/incidents/events/i-1", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateIncident(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestUpdateIncident_NotFound(t *testing.T) {
	h := NewHandler(&testutil.MockPool{ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
		return testutil.MockCommandResult{RowsAffectedVal: 0}, nil
	}})
	body, _ := json.Marshal(map[string]string{"status": "resolved"})
	req := httptest.NewRequest(http.MethodPut, "/v1/incidents/events/i-1", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateIncident(w, req)
	if w.Result().StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Result().StatusCode)
	}
}

func TestUpdateIncident_Success(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	body, _ := json.Marshal(map[string]string{"status": "resolved"})
	req := httptest.NewRequest(http.MethodPut, "/v1/incidents/events/i-1", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.UpdateIncident(w, req)
	if w.Result().StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Result().StatusCode)
	}
}
