package config

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

func newTestPanelHandler() *PanelHandler {
	return NewPanelHandler(&testutil.MockPool{})
}

func TestCreateBrand_InvalidJSON(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/brands", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreateBrand(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreateBrand_MissingFields(t *testing.T) {
	h := newTestHandler()
	tests := []struct {
		name string
		body map[string]string
	}{
		{name: "empty name", body: map[string]string{"website_url": "https://example.com"}},
		{name: "empty website_url", body: map[string]string{"name": "Test"}},
		{name: "all empty", body: map[string]string{}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/brands", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()
			h.CreateBrand(w, req)
			if w.Result().StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", w.Result().StatusCode)
			}
		})
	}
}

func TestCreatePanel_InvalidJSON(t *testing.T) {
	h := newTestPanelHandler()
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/panels", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreatePanel(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreatePanel_MissingName(t *testing.T) {
	h := newTestPanelHandler()
	body, _ := json.Marshal(map[string]string{"description": "no name"})
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/panels", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreatePanel(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreatePromptSet_InvalidJSON(t *testing.T) {
	h := newTestPanelHandler()
	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/prompt-sets", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.CreatePromptSet(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestCreatePromptSet_MissingFields(t *testing.T) {
	h := newTestPanelHandler()
	tests := []struct {
		name string
		body map[string]string
	}{
		{name: "empty name", body: map[string]string{"prompt_text": "some prompt"}},
		{name: "empty prompt_text", body: map[string]string{"name": "Test"}},
		{name: "all empty", body: map[string]string{}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/prompt-sets", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()
			h.CreatePromptSet(w, req)
			if w.Result().StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", w.Result().StatusCode)
			}
		})
	}
}
