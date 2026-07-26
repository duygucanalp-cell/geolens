package measure

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/testutil"
)

func TestMeasureNewHandler(t *testing.T) {
	h := NewHandler(&testutil.MockPool{}, engine.NewRegistry())
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestTriggerMeasurement_InvalidJSON(t *testing.T) {
	h := NewHandler(&testutil.MockPool{}, engine.NewRegistry())

	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/measurements", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.TriggerMeasurement(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestTriggerMeasurement_MissingBrandID(t *testing.T) {
	h := NewHandler(nil, engine.NewRegistry())

	tests := []struct {
		name string
		body map[string]string
	}{
		{name: "no brand_id", body: map[string]string{"panel_id": "p1"}},
		{name: "empty", body: map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/measurements", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			w := httptest.NewRecorder()

			h.TriggerMeasurement(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("expected 400, got %d", resp.StatusCode)
			}
		})
	}
}
