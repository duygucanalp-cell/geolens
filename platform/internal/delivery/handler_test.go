package delivery

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestDeliveryNewHandler(t *testing.T) {
	h := NewHandler(nil, EmailConfig{})
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestUpdateSettings_InvalidJSON(t *testing.T) {
	h := NewHandler(nil, EmailConfig{})

	req := httptest.NewRequest(http.MethodPut, "/v1/workspaces/ws/notifications/settings", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.UpdateSettings(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestSendTestEmail_InvalidJSON(t *testing.T) {
	h := NewHandler(nil, EmailConfig{})

	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/notifications/test", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.SendTestEmail(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}
