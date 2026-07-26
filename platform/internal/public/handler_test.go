package public

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/geolens/platform/internal/testutil"
)

func newTestHandler() *Handler {
	return NewHandler(&testutil.MockPool{})
}

func TestGetScore_EmptyBrandID(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodGet, "/public/v1/scores/", nil)
	w := httptest.NewRecorder()
	h.GetScore(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestListTrends_NoQuery(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodGet, "/public/v1/trends", nil)
	w := httptest.NewRecorder()
	h.ListTrends(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}
