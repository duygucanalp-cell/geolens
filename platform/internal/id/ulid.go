// Package id provides shared ID generation for the GeoLens platform.
// Tüm ID'ler ULID formatında üretilir (26 karakter, base32, zaman sıralı).
package id

import (
	"math/rand"
	"sync"
	"time"

	"github.com/oklog/ulid/v2"
)

var entropy = &lockedEntropy{source: rand.New(rand.NewSource(time.Now().UnixNano()))}

type lockedEntropy struct {
	source *rand.Rand
	mu     sync.Mutex
}

func (e *lockedEntropy) Read(p []byte) (int, error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.source.Read(p)
}

// New generates a new ULID string.
func New() string {
	return ulid.MustNew(ulid.Now(), entropy).String()
}
