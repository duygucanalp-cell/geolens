package governance

import (
	"testing"
	"time"
)

func TestNewUsageRecorder(t *testing.T) {
	ur := NewUsageRecorder(nil)
	if ur == nil {
		t.Fatal("NewUsageRecorder should not return nil")
	}
}

func TestUsageMetricConstants(t *testing.T) {
	if MetricEngineCalls != "engine_calls" {
		t.Errorf("expected engine_calls, got %s", MetricEngineCalls)
	}
	if MetricAPIRequests != "api_requests" {
		t.Errorf("expected api_requests, got %s", MetricAPIRequests)
	}
	if MetricStorageBytes != "storage_bytes" {
		t.Errorf("expected storage_bytes, got %s", MetricStorageBytes)
	}
	if MetricScoresComputed != "scores_computed" {
		t.Errorf("expected scores_computed, got %s", MetricScoresComputed)
	}
}

func TestRecordUsage_NilPool(t *testing.T) {
	ur := NewUsageRecorder(nil)
	err := ur.RecordUsage(nil, "tenant-1", MetricEngineCalls, 1, "brand", "brand-1")
	if err == nil {
		t.Log("expected error with nil pool (no panic)")
	}
}

func TestIncrementUsage_NilPool(t *testing.T) {
	ur := NewUsageRecorder(nil)
	err := ur.IncrementUsage(nil, "tenant-1", MetricAPIRequests, "api", "req-1")
	if err == nil {
		t.Log("expected error with nil pool (no panic)")
	}
}

func TestGetUsageSummary_NilPool(t *testing.T) {
	ur := NewUsageRecorder(nil)
	_, err := ur.GetUsageSummary(nil, "tenant-1", time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC))
	if err == nil {
		t.Log("expected error with nil pool (no panic)")
	}
}
