// Package queue provides queue related functionality.
package queue

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

// EventOutbox represents a pending event in the transactional outbox table.
type EventOutbox struct {
	ID        string          `json:"id"`
	EventType string          `json:"event_type"`
	Stream    string          `json:"stream"`
	Payload   json.RawMessage `json:"payload"`
	TenantID  string          `json:"tenant_id"`
}

// Dispatcher reads pending outbox records from PostgreSQL and dispatches them to Redis Streams.
type Dispatcher struct {
	pool          *pgxpool.Pool
	rdb           *redis.Client
	pollInterval  time.Duration
	consumerGroup string
}

// NewDispatcher creates a new outbox dispatcher.
func NewDispatcher(pool *pgxpool.Pool, rdb *redis.Client, pollInterval time.Duration, consumerGroup string) *Dispatcher {
	return &Dispatcher{
		pool:          pool,
		rdb:           rdb,
		pollInterval:  pollInterval,
		consumerGroup: consumerGroup,
	}
}

// Streams constants
const (
	StreamMeasure      = "q:measure"
	StreamAudit        = "q:audit"
	StreamReport       = "q:report"
	StreamNotify       = "q:notify"
	StreamDead         = "q:dead"
	StreamSentiment    = "q:sentiment"
	StreamReplay       = "q:replay"
	StreamArchive      = "q:archive"
	StreamGap          = "q:gap"
	StreamTechnicalGeo = "q:technical-geo"
	StreamContentGeo   = "q:content-geo"
	StreamGovernance   = "q:governance" // Faz 4 olayları: guardrail, gate, incident, drift, redteam (O-6)
)

// Start begins the dispatch loop. Runs until the context is cancelled. //nolint:misspell
func (d *Dispatcher) Start(ctx context.Context) {
	// Consumer gruplarını oluştur (ilk seferde hata vermez)
	for _, stream := range []string{StreamMeasure, StreamAudit, StreamReport, StreamNotify, StreamDead, StreamSentiment, StreamReplay, StreamArchive, StreamGap, StreamTechnicalGeo, StreamContentGeo, StreamGovernance} {
		if err := d.rdb.XGroupCreateMkStream(ctx, stream, d.consumerGroup, "0").Err(); err != nil {
			// BUSYGROUP: grup zaten var — ilk çalıştırmada sorun değil
			slog.Debug("redis stream grubu", "stream", stream, "error", err)
		}
	}

	slog.Info("outbox dağıtıcı başlatıldı", "poll_interval", d.pollInterval)

	ticker := time.NewTicker(d.pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("outbox dağıtıcı durduruldu")
			return
		case <-ticker.C:
			if err := d.dispatchPending(ctx); err != nil {
				slog.Error("outbox dağıtım hatası", "error", err)
			}
		}
	}
}

// dispatchPending reads all pending outbox records and dispatches them.
func (d *Dispatcher) dispatchPending(ctx context.Context) error {
	rows, err := d.pool.Query(ctx, `
		SELECT id, event_type, stream, payload, tenant_id
		FROM public.event_outbox
		WHERE dispatched_at IS NULL
		ORDER BY created_at ASC
		LIMIT 100
		FOR UPDATE SKIP LOCKED
	`)
	if err != nil {
		return fmt.Errorf("pending outbox sorgu: %w", err)
	}
	defer rows.Close()

	var count int
	for rows.Next() {
		var outbox EventOutbox
		if err := rows.Scan(&outbox.ID, &outbox.EventType, &outbox.Stream, &outbox.Payload, &outbox.TenantID); err != nil {
			slog.Error("outbox satır okuma hatası", "error", err)
			continue
		}

		if err := d.dispatchOne(ctx, outbox); err != nil {
			slog.Error("outbox gönderme hatası", "event_id", outbox.ID, "error", err)
			continue
		}
		count++
	}

	if count > 0 {
		slog.Debug("outbox mesajları dağıtıldı", "count", count)
	}

	return rows.Err()
}

// dispatchOne dispatches a single outbox record to Redis Streams.
func (d *Dispatcher) dispatchOne(ctx context.Context, outbox EventOutbox) error {
	// Event'i JSON olarak stream'e yaz
	data, err := json.Marshal(map[string]interface{}{
		"event_id":   outbox.ID,
		"event_type": outbox.EventType,
		"tenant_id":  outbox.TenantID,
		"payload":    outbox.Payload,
		"timestamp":  time.Now().UTC().Format(time.RFC3339),
	})
	if err != nil {
		return fmt.Errorf("event serileştirme: %w", err)
	}

	stream := outbox.Stream
	if stream == "" {
		stream = StreamMeasure // varsayılan
	}

	if err := d.rdb.XAdd(ctx, &redis.XAddArgs{
		Stream: stream,
		Values: map[string]interface{}{
			"event":     outbox.EventType,
			"tenant_id": outbox.TenantID,
			"data":      string(data),
		},
	}).Err(); err != nil {
		return fmt.Errorf("redis stream xadd: %w", err)
	}

	// Dispatched olarak işaretle
	_, err = d.pool.Exec(ctx,
		`UPDATE public.event_outbox SET dispatched_at = now() WHERE id = $1`,
		outbox.ID,
	)
	if err != nil {
		return fmt.Errorf("outbox dispatched güncelleme: %w", err)
	}

	return nil
}
