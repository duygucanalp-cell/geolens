package pdf

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func newTestHandler() *Handler {
	return &Handler{pool: nil, svc: nil}
}

func TestRequestReport_InvalidJSON(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/reports", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RequestReport(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRequestReport_MissingReportType(t *testing.T) {
	h := newTestHandler()
	body, _ := json.Marshal(map[string]string{"brand_id": "b1"})
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/reports", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RequestReport(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestRequestReport_InvalidReportType(t *testing.T) {
	h := newTestHandler()
	body, _ := json.Marshal(map[string]string{"report_type": "invalid"})
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/reports", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.RequestReport(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}
