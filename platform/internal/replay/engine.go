// Package replay provides the core engine for Conversation Replay (FR-D12).
package replay

import (
	"context"
	"crypto/sha256"
	"fmt"
	"log/slog"
	"time"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
)

// Engine provides conversation replay logic.
type Engine struct {
	pool *db.Pool
}

// NewEngine creates a new replay engine.
func NewEngine(pool *db.Pool) *Engine {
	return &Engine{pool: pool}
}

// Snapshot represents a conversation snapshot.
type Snapshot struct {
	ID              string    `json:"id"`
	BrandID         string    `json:"brand_id"`
	PromptText      string    `json:"prompt_text"`
	EngineName      string    `json:"engine_name"`
	ResponsePreview string    `json:"response_preview"`
	ResponseFull    string    `json:"response_full,omitempty"`
	ContentHash     string    `json:"content_hash"`
	S3Ref           *string   `json:"s3_ref,omitempty"`
	ReplayID        string    `json:"replay_id,omitempty"`
	CreatedAt       time.Time `json:"created_at"`
}

// DiffResult represents the comparison between two snapshots.
type DiffResult struct {
	SnapshotA  string `json:"snapshot_a"`
	SnapshotB  string `json:"snapshot_b"`
	BrandID    string `json:"brand_id"`
	EngineName string `json:"engine_name"`
	PromptText string `json:"prompt_text"`
	HasChanged bool   `json:"has_changed"`
	Changes    string `json:"changes,omitempty"`
	AnalyzedAt string `json:"analyzed_at"`
}

// CaptureSnapshot captures a snapshot of current AI responses for a brand.
func (e *Engine) CaptureSnapshot(ctx context.Context, brandID, prompt, workspaceID, tenantID string) (*Snapshot, error) {
	// Get the latest raw response for each engine
	rows, err := e.pool.Query(ctx, `
		SELECT DISTINCT ON (rr.engine_name) rr.engine_name, rr.content_text, rr.id
		FROM measure.raw_responses rr
		WHERE rr.tenant_id = $1 AND rr.brand_id = $2
		ORDER BY rr.engine_name, rr.created_at DESC
	`, tenantID, brandID)
	if err != nil {
		return nil, fmt.Errorf("snapshot sorgu: %w", err)
	}
	defer rows.Close()

	type engineResp struct {
		EngineName string
		Content    string
		RawID      string
	}

	var responses []engineResp
	for rows.Next() {
		var r engineResp
		if err := rows.Scan(&r.EngineName, &r.Content, &r.RawID); err != nil {
			slog.Warn("snapshot satır okuma hatası", "error", err)
			continue
		}
		responses = append(responses, r)
	}
	if rows.Err() != nil {
		return nil, fmt.Errorf("rows iterasyon: %w", rows.Err())
	}

	if len(responses) == 0 {
		return nil, fmt.Errorf("hiç yanıt bulunamadı")
	}

	// Take the first engine response as the snapshot
	resp := responses[0]
	hash := sha256.Sum256([]byte(resp.Content))
	preview := resp.Content
	if len(preview) > 500 {
		preview = preview[:500]
	}

	snapshot := &Snapshot{
		ID:              id.New(),
		BrandID:         brandID,
		PromptText:      prompt,
		EngineName:      resp.EngineName,
		ResponsePreview: preview,
		ResponseFull:    resp.Content,
		ContentHash:     fmt.Sprintf("%x", hash),
		CreatedAt:       time.Now(),
	}

	// Save to DB
	_, err = e.pool.Exec(ctx, `
		INSERT INTO replay.conversation_snapshots
			(id, brand_id, prompt_text, engine_name, response_preview, response_full, content_hash, tenant_id, workspace_id, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`, snapshot.ID, snapshot.BrandID, snapshot.PromptText, snapshot.EngineName,
		snapshot.ResponsePreview, snapshot.ResponseFull, snapshot.ContentHash, tenantID, workspaceID, snapshot.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("snapshot kaydetme: %w", err)
	}

	metrics.ConversationSnapshotsCreated.WithLabelValues(tenantID).Inc()
	slog.Info("conversation snapshot alındı", "brand", brandID, "engine", resp.EngineName)

	return snapshot, nil
}

// Compare compares two snapshots and returns the diff.
func (e *Engine) Compare(ctx context.Context, snapshotA, snapshotB, workspaceID, tenantID string) (*DiffResult, error) {
	var sA, sB struct {
		BrandID    string
		PromptText string
		EngineName string
		Content    string
	}

	err := e.pool.QueryRow(ctx, `
		SELECT cs.brand_id, cs.prompt_text, cs.engine_name, COALESCE(cs.response_full, cs.response_preview)
		FROM replay.conversation_snapshots cs
		JOIN config.brands b ON b.id = cs.brand_id
		WHERE cs.id = $1 AND cs.tenant_id = $2 AND b.workspace_id = $3
	`, snapshotA, tenantID, workspaceID).Scan(&sA.BrandID, &sA.PromptText, &sA.EngineName, &sA.Content)
	if err != nil {
		return nil, fmt.Errorf("snapshot A bulunamadı: %w", err)
	}

	err = e.pool.QueryRow(ctx, `
		SELECT cs.brand_id, cs.prompt_text, cs.engine_name, COALESCE(cs.response_full, cs.response_preview)
		FROM replay.conversation_snapshots cs
		JOIN config.brands b ON b.id = cs.brand_id
		WHERE cs.id = $1 AND cs.tenant_id = $2 AND b.workspace_id = $3
	`, snapshotB, tenantID, workspaceID).Scan(&sB.BrandID, &sB.PromptText, &sB.EngineName, &sB.Content)
	if err != nil {
		return nil, fmt.Errorf("snapshot B bulunamadı: %w", err)
	}

	hasChanged := sA.Content != sB.Content
	changes := ""
	if hasChanged {
		changes = "Yanıt içeriği değişmiş. Detaylı karşılaştırma için snapshot'ların tam metinlerini inceleyin."
	}

	return &DiffResult{
		SnapshotA:  snapshotA,
		SnapshotB:  snapshotB,
		BrandID:    sA.BrandID,
		EngineName: sA.EngineName,
		PromptText: sA.PromptText,
		HasChanged: hasChanged,
		Changes:    changes,
		AnalyzedAt: time.Now().Format(time.RFC3339),
	}, nil
}
