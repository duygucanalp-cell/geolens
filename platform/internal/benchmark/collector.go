// Package benchmark provides handlers and logic for benchmark functionality.
package benchmark

import (
	"context"
	"log/slog"
	"time"
)

// Collector manages periodic benchmark sector statistic aggregation.
// It wraps the Aggregator with a simpler interface for use in background workers.
type Collector struct {
	aggregator *Aggregator
	interval   time.Duration
}

// NewCollector creates a new benchmark data collector with the given aggregator.
// The collector runs the aggregation at the specified interval.
func NewCollector(aggregator *Aggregator, interval time.Duration) *Collector {
	if interval <= 0 {
		interval = 1 * time.Hour // default: hourly
	}
	return &Collector{
		aggregator: aggregator,
		interval:   interval,
	}
}

// Run starts the periodic data collection. Blocks until the context is cancelled.
// This is designed to be called from a worker goroutine or the main worker loop.
// Call Run in a separate goroutine:
//
//	go collector.Run(ctx)
func (c *Collector) Run(ctx context.Context) error {
	logger := slog.With("component", "benchmark-collector")
	logger.Info("benchmark veri toplayıcı başlatıldı", "interval", c.interval.String())

	// İlk çalıştırmayı hemen yap
	id, err := c.aggregator.Aggregate(ctx)
	if err != nil {
		logger.Warn("ilk toplulaştırma hatası", "error", err)
	} else if id != "" {
		logger.Debug("ilk toplulaştırma tamam", "stats_id", id)
	}

	ticker := time.NewTicker(c.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			logger.Info("benchmark veri toplayıcı durduruldu")
			return ctx.Err()
		case <-ticker.C:
			id, err := c.aggregator.Aggregate(ctx)
			if err != nil {
				logger.Warn("periyodik toplulaştırma hatası", "error", err)
			} else if id != "" {
				logger.Debug("periyodik toplulaştırma tamam", "stats_id", id)
			}
		}
	}
}
