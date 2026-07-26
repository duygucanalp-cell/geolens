// Package httputil provides httputil related functionality.
package httputil

import (
	"encoding/json"
	"net/http"
)

// WriteJSON sends a JSON response with the given status code.
func WriteJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

// WriteError sends a JSON error response.
func WriteError(w http.ResponseWriter, status int, msg string) {
	WriteJSON(w, status, map[string]string{"error": msg})
}
