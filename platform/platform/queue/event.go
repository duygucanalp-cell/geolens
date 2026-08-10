package queue

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/id"
)

// CommandResult aliases dbiface.CommandResult so that dbiface.DB and pgx.Tx
// structurally satisfy the Execer interface (O-6: outbox event taşıması).
type CommandResult = dbiface.CommandResult

// Execer executes a SQL statement and returns a command result.
type Execer interface {
	Exec(ctx context.Context, sql string, args ...any) (CommandResult, error)
}

// EnqueueEvent writes a domain event to the transactional outbox.
// Dispatcher, bekleyen kayıtları ilgili Redis Stream'ine iletir (O-6: Faz 4 olay taşıması).
// idempotencyKey boş bırakılırsa NULL yazılır (benzersizlik kısıtı uygulanmaz).
func EnqueueEvent(ctx context.Context, exec Execer, eventType, stream string, payload any, tenantID, idempotencyKey string) error {
	data, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("olay payload serileştirme: %w", err)
	}

	var idem any
	if idempotencyKey != "" {
		idem = idempotencyKey
	}

	eventID := id.New()
	_, err = exec.Exec(ctx, `
		INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
		VALUES ($1, $2, $3, $4::jsonb, $5, $6, now())
	`, eventID, eventType, stream, string(data), tenantID, idem)
	if err != nil {
		return fmt.Errorf("outbox olay ekleme: %w", err)
	}
	return nil
}
