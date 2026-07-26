package alert

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func newTestHandler() *Handler {
	return NewHandler(nil)
}

func TestCreate_InvalidJSON(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/alert-rules", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Create(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreate_MissingFields(t *testing.T) {
	h := newTestHandler()
	tests := []struct {
		name string
		body map[string]interface{}
	}{
		{name: "empty", body: map[string]interface{}{}},
		{name: "no brand_id", body: map[string]interface{}{"name": "test", "metric": "score_drop", "condition": "gt"}},
		{name: "no name", body: map[string]interface{}{"brand_id": "b1", "metric": "score_drop", "condition": "gt"}},
		{name: "no metric", body: map[string]interface{}{"brand_id": "b1", "name": "test", "condition": "gt"}},
		{name: "no condition", body: map[string]interface{}{"brand_id": "b1", "name": "test", "metric": "score_drop"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/alert-rules", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()
			h.Create(w, req)
			if w.Result().StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", w.Result().StatusCode)
			}
		})
	}
}

func TestUpdate_InvalidJSON(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodPut, "/v1/workspaces/ws/alert-rules/r1", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Update(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}
