package queue

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
)

func TestNewRedisClient_InvalidURL(t *testing.T) {
	tests := []struct {
		name    string
		url     string
		wantErr string
	}{
		{name: "empty", url: "", wantErr: "redis url parse"},
		{name: "bad scheme", url: "bad://host:6379/0", wantErr: "redis url parse"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client, err := NewRedisClient(tt.url)
			if client != nil {
				client.Close()
			}
			if err == nil {
				t.Fatal("expected error, got nil")
			}
			if !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("expected error containing %q, got %q", tt.wantErr, err.Error())
			}
		})
	}
}

func TestNewDispatcher(t *testing.T) {
	d := NewDispatcher(nil, nil, 5*time.Second, "cg:test")
	if d == nil {
		t.Fatal("expected non-nil dispatcher")
	}
	if d.pollInterval != 5*time.Second {
		t.Fatalf("expected pollInterval 5s, got %v", d.pollInterval)
	}
	if d.consumerGroup != "cg:test" {
		t.Fatalf("expected consumerGroup cg:test, got %s", d.consumerGroup)
	}
}

func TestStreamConstants(t *testing.T) {
	if StreamMeasure != "q:measure" {
		t.Fatalf("StreamMeasure = %q, want q:measure", StreamMeasure)
	}
	if StreamAudit != "q:audit" {
		t.Fatalf("StreamAudit = %q, want q:audit", StreamAudit)
	}
	if StreamReport != "q:report" {
		t.Fatalf("StreamReport = %q, want q:report", StreamReport)
	}
	if StreamNotify != "q:notify" {
		t.Fatalf("StreamNotify = %q, want q:notify", StreamNotify)
	}
	if StreamDead != "q:dead" {
		t.Fatalf("StreamDead = %q, want q:dead", StreamDead)
	}
}

func TestEventOutboxStruct(t *testing.T) {
	e := EventOutbox{
		ID:        "evt-001",
		EventType: "measurement.requested",
		Stream:    StreamMeasure,
		Payload:   []byte(`{"foo":"bar"}`),
		TenantID:  "tenant-1",
	}

	if e.ID != "evt-001" {
		t.Fatalf("ID = %q, want evt-001", e.ID)
	}
	if e.EventType != "measurement.requested" {
		t.Fatalf("EventType = %q, want measurement.requested", e.EventType)
	}
	if string(e.Payload) != `{"foo":"bar"}` {
		t.Fatalf("Payload = %q, want json", string(e.Payload))
	}
	if e.TenantID != "tenant-1" {
		t.Fatalf("TenantID = %q, want tenant-1", e.TenantID)
	}
}

func TestNewDispatcher_NilFields(t *testing.T) {
	d := NewDispatcher(nil, nil, 0, "")
	if d.pool != nil {
		t.Fatal("expected nil pool")
	}
	if d.rdb != nil {
		t.Fatal("expected nil rdb")
	}
}

// ---- O-6: Outbox Olay Taşıması Testleri ----

func TestStreamGovernanceConstant(t *testing.T) {
	if StreamGovernance != "q:governance" {
		t.Fatalf("StreamGovernance = %q, want q:governance", StreamGovernance)
	}
}

func TestEnqueueEvent(t *testing.T) {
	execCount := 0
	pool := &testutil.MockPool{ExecFunc: func(_ context.Context, sql string, _ ...any) (dbiface.CommandResult, error) {
		execCount++
		if !strings.Contains(sql, "INSERT INTO public.event_outbox") {
			t.Fatalf("beklenen outbox INSERT, SQL: %s", sql)
		}
		return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
	}}

	if err := EnqueueEvent(context.Background(), pool, "guardrail.violation", StreamGovernance,
		map[string]interface{}{"rule_id": "r-1"}, "tenant-1", "idem-1"); err != nil {
		t.Fatalf("EnqueueEvent hatası: %v", err)
	}
	if execCount != 1 {
		t.Fatalf("beklenen 1 Exec, gerçek %d", execCount)
	}
}

func TestEnqueueEvent_EmptyIdempotencyKey(t *testing.T) {
	pool := &testutil.MockPool{ExecFunc: func(_ context.Context, _ string, args ...any) (dbiface.CommandResult, error) {
		// idempotency_key boş bırakılırsa NULL (nil) yazılmalı
		if args[5] != nil {
			t.Fatalf("beklenen nil idempotency key, gerçek %v", args[5])
		}
		return testutil.MockCommandResult{RowsAffectedVal: 1}, nil
	}}

	if err := EnqueueEvent(context.Background(), pool, "drift.alert.triggered", StreamGovernance,
		map[string]interface{}{"entity_id": "e-1"}, "tenant-1", ""); err != nil {
		t.Fatalf("EnqueueEvent hatası: %v", err)
	}
}

func TestEnqueueEvent_Error(t *testing.T) {
	pool := &testutil.MockPool{ExecFunc: func(_ context.Context, _ string, _ ...any) (dbiface.CommandResult, error) {
		return nil, errors.New("db error")
	}}
	if err := EnqueueEvent(context.Background(), pool, "incident.opened", StreamGovernance,
		map[string]interface{}{}, "t-1", ""); err == nil {
		t.Fatal("beklenen hata, nil geldi")
	}
}

func TestNewRedisClient_ParseError(t *testing.T) {
	_, err := NewRedisClient("not-a-valid-url-at-all!!!!")
	if err == nil {
		t.Fatal("expected error for invalid URL")
	}
	if !strings.Contains(err.Error(), "redis url parse") {
		t.Fatalf("expected parse error, got: %v", err)
	}
}
