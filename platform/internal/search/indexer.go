package search

import (
	"context"
	"log/slog"
	"time"
)

type AuditEntry struct {
	ID           string                 `json:"id"`
	TenantID     string                 `json:"tenant_id"`
	UserID       string                 `json:"user_id"`
	EventType    string                 `json:"event_type"`
	ResourceType string                 `json:"resource_type"`
	ResourceID   string                 `json:"resource_id"`
	Action       string                 `json:"action"`
	Metadata     map[string]interface{} `json:"metadata"`
	IPAddress    string                 `json:"ip_address"`
	CreatedAt    time.Time              `json:"created_at"`
}

type Indexer struct {
	client *Client
}

func NewIndexer(client *Client) *Indexer {
	return &Indexer{client: client}
}

func (idx *Indexer) IndexAuditLog(ctx context.Context, entry AuditEntry) error {
	if idx.client.endpoint == "" {
		return nil
	}

	doc := IndexDoc{
		Index: "geolens-audit",
		ID:    entry.ID,
		Body: map[string]interface{}{
			"tenant_id":     entry.TenantID,
			"user_id":       entry.UserID,
			"event_type":    entry.EventType,
			"resource_type": entry.ResourceType,
			"resource_id":   entry.ResourceID,
			"action":        entry.Action,
			"metadata":      entry.Metadata,
			"ip_address":    entry.IPAddress,
			"created_at":    entry.CreatedAt.Format(time.RFC3339),
		},
	}

	if err := idx.client.Index(ctx, doc); err != nil {
		slog.Error("es audit log indexing hatası", "error", err, "id", entry.ID)
		return err
	}

	return nil
}

func (idx *Indexer) SearchAuditLog(ctx context.Context, tenantID, query string, limit int) (*SearchResult, error) {
	esQuery := map[string]interface{}{
		"bool": map[string]interface{}{
			"must": []map[string]interface{}{
				{"term": map[string]interface{}{"tenant_id": tenantID}},
				{"multi_match": map[string]interface{}{
					"query":  query,
					"fields": []string{"event_type", "action", "resource_type"},
				}},
			},
		},
	}
	return idx.client.Search(ctx, "geolens-audit", esQuery)
}
