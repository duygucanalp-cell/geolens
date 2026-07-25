package governance

import (
	"testing"
)

func TestNewQuotaChecker(t *testing.T) {
	qc := NewQuotaChecker(nil)
	if qc == nil {
		t.Fatal("NewQuotaChecker should not return nil")
	}
}

func TestDefaultBuckets(t *testing.T) {
	if len(defaultBuckets) != 3 {
		t.Fatalf("expected 3 default buckets, got %d", len(defaultBuckets))
	}

	expected := map[string]int64{
		"engine_calls_per_min":  30,
		"engine_calls_per_hour": 500,
		"api_requests_per_hour": 1000,
	}

	for _, b := range defaultBuckets {
		expectedMax, ok := expected[b.BucketName]
		if !ok {
			t.Errorf("unexpected bucket: %s", b.BucketName)
			continue
		}
		if b.MaxTokens != expectedMax {
			t.Errorf("bucket %s: expected max %d, got %d", b.BucketName, expectedMax, b.MaxTokens)
		}
	}
}

func TestEnsureBuckets_NilPool(t *testing.T) {
	qc := NewQuotaChecker(nil)
	err := qc.EnsureBuckets(nil, "tenant-1")
	if err != nil {
		t.Logf("expected no error with nil pool (just warning): %v", err)
	}
}

func TestCheckAndConsume_NilPool(t *testing.T) {
	qc := NewQuotaChecker(nil)
	allowed, err := qc.CheckAndConsume(nil, "tenant-1", "engine_calls_per_min")
	if err != nil {
		t.Fatal("expected no error from CheckAndConsume with nil pool")
	}
	if !allowed {
		t.Fatal("expected allowed=true with nil pool (fallback)")
	}
}
