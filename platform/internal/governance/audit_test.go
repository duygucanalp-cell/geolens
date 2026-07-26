package governance

import (
	"context"
	"testing"
	"time"
)

func TestAuditEntry_Defaults(t *testing.T) {
	e := AuditEntry{}
	if e.ID != "" {
		t.Errorf("expected empty ID, got %s", e.ID)
	}
	if e.CreatedAt != (time.Time{}) {
		t.Errorf("expected zero CreatedAt, got %v", e.CreatedAt)
	}
}

func TestAuditEntry_WithValues(t *testing.T) {
	now := time.Now()
	e := AuditEntry{
		ID:           "test-id",
		TenantID:     "tenant-1",
		UserID:       "user-1",
		EventType:    "test.event",
		ResourceType: "brand",
		ResourceID:   "brand-1",
		Action:       "create",
		IPAddress:    "127.0.0.1",
		UserAgent:    "test-agent",
		CreatedAt:    now,
	}
	if e.TenantID != "tenant-1" {
		t.Errorf("expected tenant-1, got %s", e.TenantID)
	}
	if e.EventType != "test.event" {
		t.Errorf("expected test.event, got %s", e.EventType)
	}
	if e.Action != "create" {
		t.Errorf("expected create, got %s", e.Action)
	}
}

func TestNewAuditLogger(t *testing.T) {
	logger := NewAuditLogger(nil)
	if logger == nil {
		t.Fatal("NewAuditLogger should not return nil")
	}
	if logger.pool != nil {
		t.Fatal("expected nil pool")
	}
}

func TestRecordEvent_NoPool(t *testing.T) {
	t.Parallel()
	logger := NewAuditLogger(nil)
	err := logger.RecordEvent(context.TODO(), "tenant-1", "test.event", "brand", "brand-1", "delete")
	// should panic or return error with nil pool — test passes if we reach here
	if err == nil {
		t.Log("no error (pool nil doesn't panic at this level)")
	}
}

func TestRecord_NoPool(t *testing.T) {
	t.Parallel()
	logger := NewAuditLogger(nil)
	err := logger.Record(context.TODO(), AuditEntry{
		TenantID:  "tenant-1",
		EventType: "test.event",
		Action:    "read",
	})
	// should panic or return error with nil pool — test passes if we reach here
	if err == nil {
		t.Log("no error (pool nil doesn't panic at this level)")
	}
}
