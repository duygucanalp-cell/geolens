package governance

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/geolens/platform/internal/errors"
	"github.com/geolens/platform/platform/db"
)

// AuditEntry represents a single audit log record.
type AuditEntry struct {
	ID           string                 `json:"id"`
	TenantID     string                 `json:"tenant_id"`
	UserID       string                 `json:"user_id,omitempty"`
	EventType    string                 `json:"event_type"`
	ResourceType string                 `json:"resource_type"`
	ResourceID   string                 `json:"resource_id,omitempty"`
	Action       string                 `json:"action"`
	Metadata     map[string]interface{} `json:"metadata,omitempty"`
	IPAddress    string                 `json:"ip_address,omitempty"`
	UserAgent    string                 `json:"user_agent,omitempty"`
	CreatedAt    time.Time              `json:"created_at"`
}

// AuditLogger provides methods to record audit events.
type AuditLogger struct {
	pool *db.Pool
}

// NewAuditLogger creates a new audit logger.
func NewAuditLogger(pool *db.Pool) *AuditLogger {
	return &AuditLogger{pool: pool}
}

// Record saves an audit entry to the database.
func (a *AuditLogger) Record(ctx context.Context, entry AuditEntry) error {
	id := fmt.Sprintf("%d-%s", time.Now().UnixMicro(), randomSuffix(6))

	metaJSON, err := json.Marshal(entry.Metadata)
	if err != nil {
		metaJSON = []byte("{}")
	}

	_, err = a.pool.Exec(ctx, `
		INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata, ip_address, user_agent, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9, $10, now())
	`, id, entry.TenantID, entry.UserID, entry.EventType, entry.ResourceType,
		entry.ResourceID, entry.Action, string(metaJSON),
		entry.IPAddress, entry.UserAgent)

	if err != nil {
		return errors.Internal("audit kaydı başarısız", err)
	}
	return nil
}

// RecordEvent is a convenience method for common audit events.
func (a *AuditLogger) RecordEvent(ctx context.Context, tenantID, eventType, resourceType, resourceID, action string) error {
	return a.Record(ctx, AuditEntry{
		TenantID:     tenantID,
		EventType:    eventType,
		ResourceType: resourceType,
		ResourceID:   resourceID,
		Action:       action,
		Metadata:     map[string]interface{}{},
	})
}

// randomSuffix generates a short random string for ID generation.
func randomSuffix(n int) string {
	const letters = "abcdefghijklmnopqrstuvwxyz0123456789"
	b := make([]byte, n)
	for i := range b {
		b[i] = letters[(time.Now().UnixNano()+int64(i*13))%int64(len(letters))]
	}
	return string(b)
}
