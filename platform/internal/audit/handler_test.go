package audit

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/testutil"
)

func TestAuditNewHandler(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestRunAudit_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/audit", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.RunAudit(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestRunAudit_MissingFields(t *testing.T) {
	h := NewHandler(nil)

	tests := []struct {
		name string
		body map[string]string
	}{
		{name: "no brand_id", body: map[string]string{"website_url": "https://example.com"}},
		{name: "no website_url", body: map[string]string{"brand_id": "b1"}},
		{name: "empty", body: map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/audit", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()

			h.RunAudit(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", resp.StatusCode)
			}
		})
	}
}
