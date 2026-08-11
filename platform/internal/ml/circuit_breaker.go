package ml

import (
	"sync"
	"time"

	"github.com/geolens/platform/platform/metrics"
)

// DefaultCooldown — serving ardışık hatası sonrası ML çağrılarının askıya
// alındığı varsayılan süre (0421 M-4). Sentiment ve measure servisleri ortak
// kullanır: serving kapalıyken her ölçümde motor × ML_TIMEOUT gecikme birikmez.
const DefaultCooldown = 60 * time.Second

// CircuitBreaker — ML serving ardışık hatalarında çağrıları geçici olarak
// askıya alan basit devre kesici (0421 M-4).
//
// Kullanım: serving çağrısı öncesi InCooldown() kontrol edilir; hata alınırsa
// Fail() çağrılır ve sonraki çağrılar cooldown süresince atlanır. Başarılı
// bir çağrı Success() ile cooldown'ı sıfırlar (serving geri geldiyse hızlı dönüş).
//
// Sentiment ve measure servislerinde önceden kopyalanmış mutex+nextAttempt
// mantığının ortak hali — tek uygulama, tutarlı davranış.
//
// Gözlemlenebilirlik (0421 M-4): her devre kesici bir component etiketiyle
// (sentiment | measure) Prometheus metrikleri yazar — geolens_ml_breaker_failures_total
// ve geolens_ml_breaker_in_cooldown. Component boşsa (NewCircuitBreaker) metrik
// yazılmaz (varsayılan kurulum, testler).
type CircuitBreaker struct {
	mu          sync.Mutex
	cooldown    time.Duration
	nextAttempt time.Time
	component   string
}

// NewCircuitBreaker, verilen cooldown süresiyle yeni bir devre kesici kurar.
// cooldown <= 0 ise DefaultCooldown kullanılır. Component boş olduğundan
// metrik yazmaz — bileşen izlemesi gereken çağrılar NewCircuitBreakerFor kullanır.
func NewCircuitBreaker(cooldown time.Duration) *CircuitBreaker {
	return newCircuitBreaker("", cooldown)
}

// NewCircuitBreakerFor, component etiketiyle (sentiment | measure) devre kesici
// kurar. Breaker olayları Prometheus metriklerine component ayrımıyla yazılır.
func NewCircuitBreakerFor(component string, cooldown time.Duration) *CircuitBreaker {
	return newCircuitBreaker(component, cooldown)
}

func newCircuitBreaker(component string, cooldown time.Duration) *CircuitBreaker {
	if cooldown <= 0 {
		cooldown = DefaultCooldown
	}
	return &CircuitBreaker{cooldown: cooldown, component: component}
}

// InCooldown — serving cooldown penceresi aktif mi? Aktifse çağıran ML çağrısını
// atlayıp kural tabanlı bileşene düşer (0421 M-4).
// Gauge'i dönen durumla uzlaştırır: cooldown doğal olarak sona erdiyse (Fail/Success
// olmadan) gauge 1'de takılı kalmaması için burada 0'a çekilir — gözlemlenebilirlik.
func (b *CircuitBreaker) InCooldown() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	inCooldown := time.Now().Before(b.nextAttempt)
	if b.component != "" {
		v := 0.0
		if inCooldown {
			v = 1
		}
		metrics.MLBreakerInCooldown.WithLabelValues(b.component).Set(v)
	}
	return inCooldown
}

// Fail — serving hatasını kaydeder ve ML çağrılarını cooldown süresince askıya alır.
func (b *CircuitBreaker) Fail() {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.nextAttempt = time.Now().Add(b.cooldown)
	b.recordFail()
}

// Success — serving yanıt verdiğinde cooldown'ı sıfırlar (serving geri geldiyse
// hızlı dönüş — başarı devre kesiciyi kapatır).
func (b *CircuitBreaker) Success() {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.nextAttempt = time.Time{}
	b.recordSuccess()
}

// recordFail, component etiketli hata sayacını artırır ve cooldown gauge'ini 1 yapar.
func (b *CircuitBreaker) recordFail() {
	if b.component == "" {
		return
	}
	metrics.MLBreakerFailuresTotal.WithLabelValues(b.component).Inc()
	metrics.MLBreakerInCooldown.WithLabelValues(b.component).Set(1)
}

// recordSuccess, component etiketli cooldown gauge'ini 0 yapar.
func (b *CircuitBreaker) recordSuccess() {
	if b.component == "" {
		return
	}
	metrics.MLBreakerInCooldown.WithLabelValues(b.component).Set(0)
}
