package errors

import (
	"net/http"
	"testing"
)

func TestErrorTypes(t *testing.T) {
	tests := []struct {
		err      *Error
		code     string
		httpCode int
	}{
		{ErrNotFound, "not_found", http.StatusNotFound},
		{ErrValidation, "validation_error", http.StatusBadRequest},
		{ErrUnauthorized, "unauthorized", http.StatusUnauthorized},
		{ErrForbidden, "forbidden", http.StatusForbidden},
		{ErrRateLimited, "rate_limited", http.StatusTooManyRequests},
		{ErrInternal, "internal_error", http.StatusInternalServerError},
	}
	for _, tt := range tests {
		if tt.err.Code != tt.code {
			t.Errorf("beklenen %s, gerçek %s", tt.code, tt.err.Code)
		}
		if tt.err.HTTPSC != tt.httpCode {
			t.Errorf("%s için beklenen HTTP %d, gerçek %d", tt.code, tt.httpCode, tt.err.HTTPSC)
		}
	}
}

func TestNotFound(t *testing.T) {
	err := NotFound("kayıt bulunamadı")
	if err.Code != "not_found" {
		t.Errorf("beklenen 'not_found', gerçek %s", err.Code)
	}
	if err.Message != "kayıt bulunamadı" {
		t.Errorf("beklenen 'kayıt bulunamadı', gerçek %s", err.Message)
	}
}

func TestStatusCode(t *testing.T) {
	err := Validation("geçersiz girdi")
	code := StatusCode(err)
	if code != http.StatusBadRequest {
		t.Errorf("beklenen 400, gerçek %d", code)
	}
}

func TestCode(t *testing.T) {
	err := Forbidden("yetkisiz")
	c := Code(err)
	if c != "forbidden" {
		t.Errorf("beklenen 'forbidden', gerçek %s", c)
	}
}
