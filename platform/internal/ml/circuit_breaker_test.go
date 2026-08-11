package ml

import (
	"testing"
	"time"

	"github.com/geolens/platform/platform/metrics"
	"github.com/prometheus/client_golang/prometheus/testutil"
)

// TestCircuitBreaker_InitiallyOpen: başlangıçta cooldown aktif değildir.
func TestCircuitBreaker_InitiallyOpen(t *testing.T) {
	b := NewCircuitBreaker(DefaultCooldown)
	if b.InCooldown() {
		t.Error("yeni devre kesici cooldown'da olmamalı")
	}
}

// TestCircuitBreaker_FailTriggersCooldown: Fail() sonrası cooldown başlar.
func TestCircuitBreaker_FailTriggersCooldown(t *testing.T) {
	b := NewCircuitBreaker(DefaultCooldown)
	b.Fail()
	if !b.InCooldown() {
		t.Error("Fail() sonrası cooldown aktif olmalı")
	}
}

// TestCircuitBreaker_SuccessResets: Success() cooldown'ı sıfırlar.
func TestCircuitBreaker_SuccessResets(t *testing.T) {
	b := NewCircuitBreaker(DefaultCooldown)
	b.Fail()
	if !b.InCooldown() {
		t.Fatal("Fail() sonrası cooldown aktif olmalı")
	}
	b.Success()
	if b.InCooldown() {
		t.Error("Success() sonrası cooldown kapanmalı")
	}
}

// TestCircuitBreaker_CooldownExpires: cooldown süresi geçince tekrar çağrı serbesttir.
func TestCircuitBreaker_CooldownExpires(t *testing.T) {
	b := NewCircuitBreaker(10 * time.Millisecond)
	b.Fail()
	if !b.InCooldown() {
		t.Fatal("Fail() sonrası cooldown aktif olmalı")
	}
	time.Sleep(30 * time.Millisecond)
	if b.InCooldown() {
		t.Error("cooldown süresi geçince kapanmalı")
	}
}

// TestCircuitBreaker_Concurrent: eşzamanlı erişim güvenli olmalı (data race yok).
func TestCircuitBreaker_Concurrent(t *testing.T) {
	b := NewCircuitBreaker(DefaultCooldown)
	done := make(chan struct{})
	for i := 0; i < 10; i++ {
		go func() {
			defer func() { done <- struct{}{} }()
			for j := 0; j < 100; j++ {
				b.InCooldown()
				b.Fail()
				b.Success()
			}
		}()
	}
	for i := 0; i < 10; i++ {
		<-done
	}
}

// TestCircuitBreaker_DefaultCooldown: sıfır/negatif cooldown varsayılana düşer.
func TestCircuitBreaker_DefaultCooldown(t *testing.T) {
	b := NewCircuitBreaker(0)
	if b.cooldown != DefaultCooldown {
		t.Errorf("sıfır cooldown varsayılana düşmeli: %v", b.cooldown)
	}
	if b2 := NewCircuitBreaker(-5 * time.Second); b2.cooldown != DefaultCooldown {
		t.Errorf("negatif cooldown varsayılana düşmeli: %v", b2.cooldown)
	}
}

// TestCircuitBreaker_Metrics: component etiketli kurulumda Fail → failures_total
// artar ve cooldown gauge 1 olur; Success → gauge 0'a döner (0421 M-4 gözlemlenebilirlik).
func TestCircuitBreaker_Metrics(t *testing.T) {
	// Benzersiz component: metrikler global olduğundan testler arası sıra
	// bağımlılığını önlemek için her test kendi label değerini kullanır.
	const comp = "metrics-test-sentiment"
	b := NewCircuitBreakerFor(comp, DefaultCooldown)

	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues(comp)); got != 0 {
		t.Errorf("başlangıçta cooldown gauge 0 olmalı, gerçek %f", got)
	}

	b.Fail()
	if got := testutil.ToFloat64(metrics.MLBreakerFailuresTotal.WithLabelValues(comp)); got != 1 {
		t.Errorf("Fail sonrası failures_total 1 olmalı, gerçek %f", got)
	}
	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues(comp)); got != 1 {
		t.Errorf("Fail sonrası cooldown gauge 1 olmalı, gerçek %f", got)
	}
	if !b.InCooldown() {
		t.Fatal("Fail sonrası cooldown aktif olmalı")
	}

	b.Success()
	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues(comp)); got != 0 {
		t.Errorf("Success sonrası cooldown gauge 0 olmalı, gerçek %f", got)
	}
	// Fail sayacı Success ile sıfırlanmaz (kümülatif toplam).
	if got := testutil.ToFloat64(metrics.MLBreakerFailuresTotal.WithLabelValues(comp)); got != 1 {
		t.Errorf("failures_total kümülatif kalmalı, gerçek %f", got)
	}
}

// TestCircuitBreaker_MetricsComponentIsolation: farklı component'ler aynı
// metriklerde ayrı label değerleriyle izole tutulur (sentiment vs measure).
func TestCircuitBreaker_MetricsComponentIsolation(t *testing.T) {
	bSent := NewCircuitBreakerFor("iso-sentiment", DefaultCooldown)
	bMeas := NewCircuitBreakerFor("iso-measure", DefaultCooldown)

	bSent.Fail()
	if got := testutil.ToFloat64(metrics.MLBreakerFailuresTotal.WithLabelValues("iso-sentiment")); got != 1 {
		t.Errorf("sentiment failures 1 olmalı, gerçek %f", got)
	}
	if got := testutil.ToFloat64(metrics.MLBreakerFailuresTotal.WithLabelValues("iso-measure")); got != 0 {
		t.Errorf("measure failures etkilenmemeli, gerçek %f", got)
	}
	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues("iso-sentiment")); got != 1 {
		t.Errorf("sentiment cooldown gauge 1 olmalı, gerçek %f", got)
	}
	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues("iso-measure")); got != 0 {
		t.Errorf("measure cooldown gauge etkilenmemeli, gerçek %f", got)
	}
	bMeas.Success() // measure hiç fail olmadı; Success zararsız olmalı (gauge 0)
}

// TestCircuitBreaker_MetricsGaugeReconciledOnExpiry: cooldown Fail/Success olmadan
// doğal olarak sona ererse InCooldown() gauge'i 0'a çeker (gauge takılı kalmaz).
func TestCircuitBreaker_MetricsGaugeReconciledOnExpiry(t *testing.T) {
	const comp = "metrics-test-expiry"
	b := NewCircuitBreakerFor(comp, 10*time.Millisecond)

	b.Fail()
	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues(comp)); got != 1 {
		t.Fatalf("Fail sonrası gauge 1 olmalı, gerçek %f", got)
	}

	time.Sleep(30 * time.Millisecond) // cooldown süresi geçer — Success çağrılmaz
	if b.InCooldown() {
		t.Fatal("cooldown süresi geçince kapanmalı")
	}
	if got := testutil.ToFloat64(metrics.MLBreakerInCooldown.WithLabelValues(comp)); got != 0 {
		t.Errorf("InCooldown gauge'i 0'a uzlaştırmalı, gerçek %f", got)
	}
}

// TestCircuitBreaker_NoMetricsWithoutComponent: component'siz kurulum (NewCircuitBreaker)
// metrik yazmaz — varsayılan kurulum ve eski testler için sessiz davranış.
func TestCircuitBreaker_NoMetricsWithoutComponent(t *testing.T) {
	b := NewCircuitBreaker(DefaultCooldown)
	b.Fail()
	if got := testutil.ToFloat64(metrics.MLBreakerFailuresTotal.WithLabelValues("")); got != 0 {
		t.Errorf("component'siz breaker metrik yazmamalı, gerçek %f", got)
	}
	b.Success()
}
