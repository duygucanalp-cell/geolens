package benchmark

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestNewAggregator_DefaultConfig(t *testing.T) {
	a := NewAggregator(nil, nil)
	if a == nil {
		t.Fatal("NewAggregator nil döndü")
	}
	if a.dpCfg.Epsilon != 1.0 {
		t.Errorf("varsayılan epsilon 1.0 olmalı, gerçek %f", a.dpCfg.Epsilon)
	}
	if a.dpCfg.MinTenants != 5 {
		t.Errorf("varsayılan MinTenants 5 olmalı, gerçek %d", a.dpCfg.MinTenants)
	}
}

func TestNewAggregator_CustomConfig(t *testing.T) {
	cfg := DPConfig{Epsilon: 2.0, Sensitivity: 100, ClampMin: 0, ClampMax: 100, MinTenants: 3}
	a := NewAggregator(nil, &cfg)
	if a.dpCfg.Epsilon != 2.0 {
		t.Errorf("custom epsilon 2.0 olmalı, gerçek %f", a.dpCfg.Epsilon)
	}
	if a.dpCfg.MinTenants != 3 {
		t.Errorf("custom MinTenants 3 olmalı, gerçek %d", a.dpCfg.MinTenants)
	}
}

func TestAggregate_QueryError(t *testing.T) {
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("db error")}
		},
	}, nil)
	id, err := a.Aggregate(context.Background())
	if err == nil {
		t.Fatal("db hatası bekleniyordu")
	}
	if id != "" {
		t.Errorf("hata durumunda id boş olmalı, gerçek %q", id)
	}
}

func TestAggregate_InsufficientTenants(t *testing.T) {
	// Mock: tenant_count < 5 (NFR-13 eşiği)
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, sql string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{3, 5}} // tenant_count=3, brand_count=5
		},
	}, nil)
	id, err := a.Aggregate(context.Background())
	if err != nil {
		t.Fatalf("beklenmeyen hata: %v", err)
	}
	if id != "" {
		t.Errorf("yetersiz tenant ile id boş olmalı, gerçek %q", id)
	}
}

func TestAggregate_SufficientTenants(t *testing.T) {
	callCount := 0
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, sql string, _ ...any) dbiface.RowScanner {
			callCount++
			switch callCount {
			case 1:
				// tenant_count=10, brand_count=20
				return &testutil.MockRow{Values: []any{10, 20}}
			case 2:
				// AVG=54.5, MIN=12, MAX=95, STDDEV=14.2, COUNT=20
				return &testutil.MockRow{Values: []any{54.5, 12.0, 95.0, 14.2, 20}}
			case 3:
				// Median = 52.0
				return &testutil.MockRow{Values: []any{52.0}}
			case 4:
				// P25=35, P75=68, P90=82
				return &testutil.MockRow{Values: []any{35.0, 68.0, 82.0}}
			case 5:
				// INSERT RETURNING id
				return &testutil.MockRow{Values: []any{"stats-001"}}
			default:
				return &testutil.MockRow{Values: []any{0}}
			}
		},
	}, nil)

	id, err := a.Aggregate(context.Background())
	if err != nil {
		t.Fatalf("beklenmeyen hata: %v", err)
	}
	if id != "stats-001" {
		t.Errorf("beklenen stats-001, gerçek %q", id)
	}
	if callCount != 5 {
		t.Errorf("5 query çağrısı bekleniyordu, gerçek %d", callCount)
	}
}

func TestAggregate_InsertError(t *testing.T) {
	callCount := 0
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, sql string, _ ...any) dbiface.RowScanner {
			callCount++
			switch callCount {
			case 1:
				return &testutil.MockRow{Values: []any{10, 20}}
			case 2:
				return &testutil.MockRow{Values: []any{54.5, 12.0, 95.0, 14.2, 20}}
			case 3:
				return &testutil.MockRow{Values: []any{52.0}}
			case 4:
				return &testutil.MockRow{Values: []any{35.0, 68.0, 82.0}}
			case 5:
				return &testutil.MockRow{Err: errors.New("insert error")}
			default:
				return &testutil.MockRow{Values: []any{0}}
			}
		},
	}, nil)

	id, err := a.Aggregate(context.Background())
	if err == nil {
		t.Fatal("insert hatası bekleniyordu")
	}
	if id != "" {
		t.Errorf("hata durumunda id boş olmalı, gerçek %q", id)
	}
}

func TestGetLatestSectorStats_Success(t *testing.T) {
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{
				24,   // tenant_count
				54.5, // sector_avg
				52.0, // sector_median
				12.0, // sector_min
				95.0, // sector_max
				14.2, // sector_stddev
				35.0, // percentile_25
				68.0, // percentile_75
				82.0, // percentile_90
			}}
		},
	}, nil)

	stats, err := a.GetLatestSectorStats(context.Background())
	if err != nil {
		t.Fatalf("beklenmeyen hata: %v", err)
	}
	if stats == nil {
		t.Fatal("nil döndü")
	}
	if stats.TenantCount != 24 {
		t.Errorf("beklenen 24, gerçek %d", stats.TenantCount)
	}
	if stats.SectorAvg != 54.5 {
		t.Errorf("beklenen 54.5, gerçek %f", stats.SectorAvg)
	}
	if !stats.SufficientData {
		t.Error("24 tenant ile SufficientData true olmalı")
	}
}

func TestGetLatestSectorStats_NoData(t *testing.T) {
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: errors.New("no rows")}
		},
	}, nil)

	stats, err := a.GetLatestSectorStats(context.Background())
	if err == nil {
		t.Fatal("no rows hatası bekleniyordu")
	}
	if stats != nil {
		t.Error("hata durumunda nil dönmeli")
	}
}

func TestRunPeriodicAggregation_Cancel(t *testing.T) {
	callCount := 0
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, sql string, _ ...any) dbiface.RowScanner {
			callCount++
			if callCount <= 5 {
				return &testutil.MockRow{Values: []any{10, 20}}
			}
			return &testutil.MockRow{Values: []any{}}
		},
	}, nil)

	ctx := context.Background()
	cancel := a.RunPeriodicAggregation(ctx, 50*time.Millisecond)

	// Wait for initial run + at least one tick
	time.Sleep(250 * time.Millisecond)
	cancel()

	// Should have run at least 2 times (initial + at least 1 tick)
	if callCount < 2 {
		t.Errorf("en az 2 aggregation bekleniyordu, gerçek %d", callCount)
	}
}

func TestRunPeriodicAggregation_ContextCancelled(t *testing.T) {
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{10, 20}}
		},
	}, nil)

	ctx, cancel := context.WithCancel(context.Background())
	_ = a.RunPeriodicAggregation(ctx, 1*time.Hour) // never tick
	cancel()
	// Should not panic — clean shutdown
}

func TestAggregate_ScoreCountZero(t *testing.T) {
	callCount := 0
	a := NewAggregator(&testutil.MockPool{
		QueryRowFunc: func(_ context.Context, _ string, _ ...any) dbiface.RowScanner {
			callCount++
			switch callCount {
			case 1:
				return &testutil.MockRow{Values: []any{10, 20}}
			case 2:
				// All NULLs because no distinct brand scores → COALESCE returns 0
				return &testutil.MockRow{Values: []any{0.0, 0.0, 0.0, 0.0, 0}}
			default:
				return &testutil.MockRow{Values: []any{0.0}}
			}
		},
	}, nil)

	id, err := a.Aggregate(context.Background())
	if err != nil {
		t.Fatalf("beklenmeyen hata: %v", err)
	}
	// With scoreCount=0, all stats will be 0, and DP noise will shift them slightly
	// but the function should complete without error
	_ = id
}
