package apikey

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/testutil"
)

func newTestHandler() *Handler {
	return NewHandler(&testutil.MockPool{})
}

func TestCreate_InvalidJSON(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodPost, "/v1/api-keys", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Create(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreate_MissingName(t *testing.T) {
	h := newTestHandler()
	body, _ := json.Marshal(map[string]string{"role": "viewer"})
	req := httptest.NewRequest(http.MethodPost, "/v1/api-keys", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Create(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreate_InvalidRole(t *testing.T) {
	h := newTestHandler()
	body, _ := json.Marshal(map[string]string{"name": "test-key", "role": "admin"})
	req := httptest.NewRequest(http.MethodPost, "/v1/api-keys", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.Create(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}
