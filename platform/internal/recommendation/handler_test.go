package recommendation

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/testutil"
)

func TestRecommendationNewHandler(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})
	if h == nil {
		t.Fatal("NewHandler should not return nil")
	}
}

func TestMarkApplied_EmptyRecID(t *testing.T) {
	h := NewHandler(&testutil.MockPool{})

	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/recommendations//apply", nil)
	w := httptest.NewRecorder()

	h.MarkApplied(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}

func TestMarkDismissed_EmptyRecID(t *testing.T) {
	h := NewHandler(nil)

	req := httptest.NewRequest(http.MethodPost, "/v1/workspaces/ws/recommendations//dismiss", nil)
	w := httptest.NewRecorder()

	h.MarkDismissed(w, req)

	resp := w.Result()
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", resp.StatusCode)
	}
}
