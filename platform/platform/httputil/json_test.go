package httputil

import (
	"encoding/json"
	"net/http/httptest"
	"testing"
)

func TestWriteJSON(t *testing.T) {
	w := httptest.NewRecorder()
	data := map[string]string{"key": "value"}
	WriteJSON(w, 200, data)

	if w.Code != 200 {
		t.Errorf("beklenen 200, gerçek %d", w.Code)
	}
	if w.Header().Get("Content-Type") != "application/json" {
		t.Errorf("Content-Type application/json olmalı")
	}

	var decoded map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &decoded); err != nil {
		t.Fatalf("JSON decode hatası: %v", err)
	}
	if decoded["key"] != "value" {
		t.Errorf("beklenen 'value', gerçek %s", decoded["key"])
	}
}

func TestWriteError(t *testing.T) {
	w := httptest.NewRecorder()
	WriteError(w, 400, "bad request")

	if w.Code != 400 {
		t.Errorf("beklenen 400, gerçek %d", w.Code)
	}

	var decoded map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &decoded); err != nil {
		t.Fatalf("JSON decode hatası: %v", err)
	}
	if decoded["error"] != "bad request" {
		t.Errorf("beklenen 'bad request', gerçek %s", decoded["error"])
	}
}
