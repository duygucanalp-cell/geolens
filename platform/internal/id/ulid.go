// Package id provides id functionality.
package id

import (
	"crypto/rand"
	"sync"

	"github.com/oklog/ulid/v2"
)

var entropy = &lockedEntropy{}

type lockedEntropy struct {
	mu sync.Mutex
}

func (e *lockedEntropy) Read(p []byte) (int, error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	return rand.Read(p)
}

func New() string {
	return ulid.MustNew(ulid.Now(), entropy).String()
}
