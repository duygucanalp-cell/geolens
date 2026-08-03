// Package archive provides the core engine for Response Archive (FR-D13).
package archive

import (
	"context"
	"crypto/sha256"
	"fmt"
	"log/slog"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
)

// Engine provides response archive logic.
type Engine struct {
	pool *db.Pool
}

// NewEngine creates a new archive engine.
func NewEngine(pool *db.Pool) *Engine {
	return &Engine{pool: pool}
}

// Entry represents an archived response entry.
type Entry struct {
	ID              string `json:"id"`
	BrandID         string `json:"brand_id"`
	EngineName      string `json:"engine_name"`
	PromptText      string `json:"prompt_text"`
	ResponsePreview string `json:"response_preview"`
	ResponseFull    string `json:"response_full,omitempty"`
	S3Ref           string `json:"s3_ref,omitempty"`
	Version         int    `json:"version"`
	ContentHash     string `json:"content_hash"`
	TenantID        string `json:"tenant_id"`
}

// Archive saves a response to the archive with versioning.
func (e *Engine) Archive(ctx context.Context, brandID, engineName, promptText, response, workspaceID, tenantID string) (*Entry, error) {
	hash := sha256.Sum256([]byte(response))
	contentHash := fmt.Sprintf("%x", hash)

	preview := response
	if len(preview) > 1000 {
		preview = preview[:1000]
	}

	// Determine next version
	var currentVersion int
	err := e.pool.QueryRow(ctx, `
		SELECT COALESCE(MAX(version), 0) FROM archive.response_entries
		WHERE brand_id = $1 AND engine_name = $2 AND tenant_id = $3
	`, brandID, engineName, tenantID).Scan(&currentVersion)
	if err != nil {
		slog.Warn("arsiv versiyon sorgu hatası", "error", err)
		currentVersion = 0
	}
	nextVersion := currentVersion + 1

	entry := &Entry{
		ID:              id.New(),
		BrandID:         brandID,
		EngineName:      engineName,
		PromptText:      promptText,
		ResponsePreview: preview,
		ResponseFull:    response,
		Version:         nextVersion,
		ContentHash:     contentHash,
		TenantID:        tenantID,
	}

	_, err = e.pool.Exec(ctx, `
		INSERT INTO archive.response_entries
			(id, brand_id, engine_name, prompt_text, response_preview, response_full,
			 version, content_hash, tenant_id, workspace_id, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, now())
	`, entry.ID, entry.BrandID, entry.EngineName, entry.PromptText,
		entry.ResponsePreview, entry.ResponseFull, entry.Version,
		entry.ContentHash, entry.TenantID, workspaceID)
	if err != nil {
		return nil, fmt.Errorf("arsiv kaydetme: %w", err)
	}

	metrics.ResponseArchiveEntriesCreated.WithLabelValues(tenantID).Inc()
	slog.Info("yanıt arşivlendi", "brand", brandID, "engine", engineName, "version", nextVersion)

	return entry, nil
}
