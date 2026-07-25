package privacy

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestNewHandler(t *testing.T) {
	h := NewHandler(nil)
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestRequestDeletion_NoUserID(t *testing.T) {
	h := NewHandler(nil)

	body, _ := json.Marshal(deletionRequest{Reason: "test"})
	req := httptest.NewRequest(http.MethodPost, "/v1/account/deletion", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.RequestDeletion(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401, got %d", resp.StatusCode)
	}
}

func TestRequestDeletion_InvalidBody(t *testing.T) {
	h := NewHandler(nil)

	req := httptest.NewRequest(http.MethodPost, "/v1/account/deletion", bytes.NewReader([]byte("invalid")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.RequestDeletion(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusUnauthorized {
		t.Logf("expected 401 (no auth context), got %d", resp.StatusCode)
	}
}

func TestListDeletionRequests_NoAuth(t *testing.T) {
	h := NewHandler(nil)

	req := httptest.NewRequest(http.MethodGet, "/v1/deletion-requests", nil)
	w := httptest.NewRecorder()

	h.ListDeletionRequests(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusForbidden {
		t.Logf("expected 403 (no admin role), got %d", resp.StatusCode)
	}
}

func TestProcessDeletionRequest_NoAuth(t *testing.T) {
	h := NewHandler(nil)

	body, _ := json.Marshal(processRequest{Action: "approve"})
	req := httptest.NewRequest(http.MethodPost, "/v1/deletion-requests/123/process", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.ProcessDeletionRequest(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusForbidden {
		t.Logf("expected 403 (no admin role), got %d", resp.StatusCode)
	}
}
