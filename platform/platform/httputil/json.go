// Package httputil provides httputil related functionality.
package httputil

import (
	"encoding/json"
	"log/slog"
	"net/http"
)

// WriteJSON sends a JSON response with the given status code.
func WriteJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Warn("json yanıt kodlanamadı", "status", status, "error", err)
	}
}

// WriteError sends a JSON error response.
func WriteError(w http.ResponseWriter, status int, msg string) {
	WriteJSON(w, status, map[string]string{"error": msg})
}
