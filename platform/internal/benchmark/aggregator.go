// Package benchmark provides handlers and logic for benchmark functionality.
package benchmark

import (
	"context"
	"log/slog"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/metrics"
)

// Aggregator computes sector-level statistics from measure.scores and caches
// them in benchmark.industry_stats with differential privacy protection.
type Aggregator struct {
	pool  dbiface.DB
	dpCfg DPConfig
}

// NewAggregator creates a new Aggregator with the given DB pool and DP config.
// If dpCfg is nil, DefaultDPConfig() is used. If dpCfg is provided, its non-zero
// fields are merged over the defaults — a partial config (e.g. only MinTenants)
// must NOT zero out Epsilon/Clamp bounds, which would clamp all stats to 0.
func NewAggregator(pool dbiface.DB, dpCfg *DPConfig) *Aggregator {
	cfg := DefaultDPConfig()
	if dpCfg != nil {
		if dpCfg.Epsilon != 0 {
			cfg.Epsilon = dpCfg.Epsilon
		}
		if dpCfg.Sensitivity != 0 {
			cfg.Sensitivity = dpCfg.Sensitivity
		}
		if dpCfg.ClampMin != 0 || dpCfg.ClampMax != 0 {
			cfg.ClampMin = dpCfg.ClampMin
			cfg.ClampMax = dpCfg.ClampMax
		}
		if dpCfg.MinTenants != 0 {
			cfg.MinTenants = dpCfg.MinTenants
		}
	}
	// NFR-13 eşiğini Prometheus gauge olarak expose et — Grafana'da
	// tenant_count >= min_tenants koşulu sufficient_data durumunu gösterir (0422).
	metrics.BenchmarkMinTenants.Set(float64(cfg.MinTenants))
	return &Aggregator{pool: pool, dpCfg: cfg}
}

// Aggregate computes sector statistics from all tenant scores and stores
// them in benchmark.industry_stats with DP protection.
// Returns the newly inserted record ID, or empty string on error/failure.
//
// This is designed to be called periodically (e.g., every 5 minutes) from
// a background goroutine or a cron-based worker.
func (a *Aggregator) Aggregate(ctx context.Context) (string, error) {
	logger := slog.With("component", "benchmark-aggregator")

	// Adım 1: Toplam kiracı ve marka sayısını hesapla (ham)
	var tenantCount, brandCount int
	err := a.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT tenant_id) AS tenant_count,
		       COUNT(DISTINCT brand_id) AS brand_count
		FROM measure.scores
	`).Scan(&tenantCount, &brandCount)
	if err != nil {
		logger.Warn("aggregator: tenant/brand count sorgu hatası", "error", err)
		return "", err
	}

	logger.Debug("aggregator: ham istatistikler",
		"tenant_count", tenantCount,
		"brand_count", brandCount,
	)

	// Son koşudaki kiracı sayısını gauge'a yaz — eşikle karşılaştırılabilir.
	metrics.BenchmarkTenantCount.Set(float64(tenantCount))

	// Adım 2: Yeterli veri yoksa hiçbir şey ekleme (NFR-13)
	if tenantCount < a.dpCfg.MinTenants {
		logger.Debug("aggregator: yetersiz veri, atlanıyor",
			"tenant_count", tenantCount,
			"min_tenants", a.dpCfg.MinTenants,
		)
		return "", nil
	}

	// Adım 3: Ham istatistikleri hesapla (brand başına en son skor)
	var rawAvg, rawMin, rawMax, rawStddev, rawMedian float64
	var rawP25, rawP75, rawP90 float64
	var scoreCount int

	// Ortalama, min, max
	err = a.pool.QueryRow(ctx, `
		SELECT AVG(sub.latest)::numeric(10,2)::double precision,
		       MIN(sub.latest)::numeric(10,2)::double precision,
		       MAX(sub.latest)::numeric(10,2)::double precision,
		       COALESCE(STDDEV(sub.latest)::numeric(10,2)::double precision, 0),
		       COUNT(*)::int
		FROM (
			SELECT DISTINCT ON (brand_id) value AS latest
			FROM measure.scores
			ORDER BY brand_id, freshness_at DESC
		) sub
	`).Scan(&rawAvg, &rawMin, &rawMax, &rawStddev, &scoreCount)
	if err != nil {
		logger.Warn("aggregator: temel istatistik sorgu hatası", "error", err)
		return "", err
	}

	// Medyan
	_ = a.pool.QueryRow(ctx, `
		WITH ranked AS (
			SELECT value, ROW_NUMBER() OVER (ORDER BY value) AS rn,
				COUNT(*) OVER () AS cnt
			FROM (
				SELECT DISTINCT ON (brand_id) value
				FROM measure.scores
				ORDER BY brand_id, freshness_at DESC
			) sub
		)
		SELECT AVG(value)::numeric(10,2)::double precision
		FROM ranked
		WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
	`).Scan(&rawMedian)

	// Yüzdelik dilimler (PG 16+ PERCENTILE_CONT)
	_ = a.pool.QueryRow(ctx, `
		WITH distinct_scores AS (
			SELECT DISTINCT ON (brand_id) value AS latest
			FROM measure.scores
			ORDER BY brand_id, freshness_at DESC
		)
		SELECT
			PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision,
			PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision,
			PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision
		FROM distinct_scores
	`).Scan(&rawP25, &rawP75, &rawP90)

	// Adım 4: Differansiyel gizlilik uygula
	raw := RawSectorStats{
		MyScore:      0, // aggregator'da kullanılmaz (kiracı bazlı değil)
		SectorAvg:    rawAvg,
		SectorMedian: rawMedian,
		SectorMin:    rawMin,
		SectorMax:    rawMax,
		SectorStdDev: rawStddev,
		Percentile25: rawP25,
		Percentile75: rawP75,
		Percentile90: rawP90,
		TenantCount:  tenantCount,
	}

	// Anonymize: tüm sektör istatistiklerine DP noise ekle
	dp := AnonymizeSectorStats(raw, a.dpCfg)
	if !dp.SufficientData {
		logger.Debug("aggregator: DP sonrası yetersiz veri, atlanıyor")
		return "", nil
	}

	// Adım 5: benchmark.industry_stats'a yaz
	var id string
	err = a.pool.QueryRow(ctx, `
		INSERT INTO benchmark.industry_stats
			(computed_at, tenant_count, brand_count,
			 sector_avg, sector_median, sector_min, sector_max, sector_stddev,
			 percentile_25, percentile_75, percentile_90,
			 score_count)
		VALUES
			($1, $2, $3,
			 $4, $5, $6, $7, $8,
			 $9, $10, $11,
			 $12)
		RETURNING id
	`,
		time.Now().UTC(),
		dp.TenantCount, brandCount,
		dp.SectorAvg, dp.SectorMedian, dp.SectorMin, dp.SectorMax, dp.SectorStdDev,
		dp.Percentile25, dp.Percentile75, dp.Percentile90,
		scoreCount,
	).Scan(&id)
	if err != nil {
		logger.Warn("aggregator: benchmark.industry_stats yazma hatası", "error", err)
		return "", err
	}

	logger.Info("aggregator: sektör istatistikleri hesaplandı ve kaydedildi",
		"stats_id", id,
		"tenant_count", dp.TenantCount,
		"brand_count", brandCount,
		"score_count", scoreCount,
		"sector_avg", dp.SectorAvg,
	)

	return id, nil
}

// GetLatestSectorStats returns the most recent cached sector statistics.
// Returns nil if no data is available yet.
// Note: MyScore, Difference, and Trend fields are set to zero — they must be
// populated by the caller with tenant-specific data before use.
func (a *Aggregator) GetLatestSectorStats(ctx context.Context) (*AggregatedSectorStats, error) {
	var stats AggregatedSectorStats
	err := a.pool.QueryRow(ctx, `
		SELECT
			tenant_count, sector_avg, sector_median,
			sector_min, sector_max, sector_stddev,
			percentile_25, percentile_75, percentile_90
		FROM benchmark.industry_stats
		ORDER BY computed_at DESC
		LIMIT 1
	`).Scan(
		&stats.TenantCount, &stats.SectorAvg, &stats.SectorMedian,
		&stats.SectorMin, &stats.SectorMax, &stats.SectorStdDev,
		&stats.Percentile25, &stats.Percentile75, &stats.Percentile90,
	)
	if err != nil {
		return nil, err
	}

	// Zero-out tenant-specific fields (not available from cached aggregate)
	stats.MyScore = 0
	stats.Difference = 0
	stats.Trend = ""

	stats.SufficientData = stats.TenantCount >= a.dpCfg.MinTenants
	return &stats, nil
}

// RunPeriodicAggregation starts a background goroutine that runs Aggregate
// at the specified interval. Stops when the context is cancelled.
// Returns a function that can be called to stop the goroutine early.
func (a *Aggregator) RunPeriodicAggregation(ctx context.Context, interval time.Duration) func() {
	ctx, cancel := context.WithCancel(ctx)

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		slog.Info("benchmark aggregator başlatıldı",
			"interval", interval.String(),
		)

		// İlk çalıştırmayı hemen yap
		id, err := a.Aggregate(ctx)
		if err != nil {
			slog.Warn("benchmark aggregator: ilk çalıştırma hatası", "error", err)
		} else if id != "" {
			slog.Debug("benchmark aggregator: ilk çalıştırma tamam", "stats_id", id)
		}

		for {
			select {
			case <-ctx.Done():
				slog.Info("benchmark aggregator durduruldu")
				return
			case <-ticker.C:
				id, err := a.Aggregate(ctx)
				if err != nil {
					slog.Warn("benchmark aggregator periyodik çalıştırma hatası", "error", err)
				} else if id != "" {
					slog.Debug("benchmark aggregator periyodik çalıştırma tamam", "stats_id", id)
				}
			}
		}
	}()

	return cancel
}
