package httpmw

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestPanicRecovery(t *testing.T) {
	handler := PanicRecovery(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		panic("test panic")
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("beklenen 500, gerçek %d", w.Code)
	}
}

func TestRequestID(t *testing.T) {
	handler := RequestID(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := GetRequestID(r.Context())
		if id == "" {
			t.Error("RequestID context'te bulunamadı")
		}
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Header().Get("X-Request-ID") == "" {
		t.Error("X-Request-ID header'ı eksik")
	}
}

func TestCORS_Options(t *testing.T) {
	handler := CORS(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("OPTIONS", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Errorf("OPTIONS beklenen 204, gerçek %d", w.Code)
	}
}

func TestGetTenantID_Empty(t *testing.T) {
	id := GetTenantID(context.Background())
	if id != "" {
		t.Errorf("beklenen '', gerçek %s", id)
	}
}

func TestHasSufficientRole(t *testing.T) {
	tests := []struct {
		user, min string
		expected  bool
	}{
		{"admin", "viewer", true},
		{"viewer", "admin", false},
		{"editor", "editor", true},
		{"viewer", "viewer", true},
		{"unknown", "viewer", false},
	}
	for _, tt := range tests {
		got := hasSufficientRole(tt.user, tt.min)
		if got != tt.expected {
			t.Errorf("hasSufficientRole(%q, %q) = %v, beklenen %v", tt.user, tt.min, got, tt.expected)
		}
	}
}
