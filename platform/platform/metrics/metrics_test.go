package metrics

import (
	"testing"

	"github.com/prometheus/client_golang/prometheus/testutil"
)

func TestMetricsRegistered(t *testing.T) {
	// Ensure metrics are registered without panic
	_ = EngineCallsTotal
	_ = EngineCallDuration
	_ = EngineResponseSize
	_ = EngineCallsFailed
	_ = QueueDepth
	_ = QueueDeadLetterSize
	_ = QueueMessagesConsumed
	_ = QueueMessagesFailed
	_ = QueueMessageProcessingDuration
	_ = ActiveUsers
	_ = TotalBrands
	_ = MeasurementsCompleted
	_ = AuditsCompleted
}

func TestEngineCallsTotal(t *testing.T) {
	EngineCallsTotal.WithLabelValues("chatgpt", "tenant-1").Inc()
	EngineCallsTotal.WithLabelValues("chatgpt", "tenant-1").Inc()

	count := testutil.CollectAndCount(EngineCallsTotal)
	if count < 1 {
		t.Error("expected at least 1 metric family")
	}
}

func TestEngineCallDuration(t *testing.T) {
	EngineCallDuration.WithLabelValues("gemini").Observe(1.5)
	EngineCallDuration.WithLabelValues("gemini").Observe(2.0)
}

func TestQueueDepth(t *testing.T) {
	QueueDepth.WithLabelValues("measure").Set(10)
	QueueDepth.WithLabelValues("notify").Set(5)
}

func TestActiveUsers(t *testing.T) {
	ActiveUsers.WithLabelValues("tenant-1").Set(3)
	val := testutil.ToFloat64(ActiveUsers.WithLabelValues("tenant-1"))
	if val != 3 {
		t.Errorf("expected 3, got %f", val)
	}
}

func TestTotalBrands(t *testing.T) {
	TotalBrands.WithLabelValues("tenant-1").Set(5)
	val := testutil.ToFloat64(TotalBrands.WithLabelValues("tenant-1"))
	if val != 5 {
		t.Errorf("expected 5, got %f", val)
	}
}

func TestMeasurementsCompleted(t *testing.T) {
	MeasurementsCompleted.WithLabelValues("tenant-1").Set(100)
}

func TestAuditsCompleted(t *testing.T) {
	AuditsCompleted.WithLabelValues("tenant-1").Set(10)
}
