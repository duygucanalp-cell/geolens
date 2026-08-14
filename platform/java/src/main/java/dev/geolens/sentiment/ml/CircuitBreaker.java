package dev.geolens.sentiment.ml;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Devre kesici — ML serving ardışık hatalarında çağrıları geçici olarak askıya alır (0421 M-4).
 * Go {@code ml.CircuitBreaker} portu. Serving çağrısı öncesi {@link #inCooldown()}
 * kontrol edilir; hata alınırsa {@link #fail()} çağrılır ve sonraki çağrılar cooldown
 * süresince atlanır. Başarılı çağrı {@link #success()} ile cooldown'ı sıfırlar.
 */
public final class CircuitBreaker {

    /** Varsayılan askıya alma süresi (0421 M-4) — serving kapalıyken motor×timeout gecikme birikmez. */
    public static final Duration DEFAULT_COOLDOWN = Duration.ofSeconds(60);

    private final ReentrantLock lock = new ReentrantLock();
    private final Duration cooldown;
    private final String component;
    private Instant nextAttempt = Instant.EPOCH;

    public CircuitBreaker(String component, Duration cooldown) {
        this.component = component;
        this.cooldown = cooldown == null || cooldown.isZero() || cooldown.isNegative()
                ? DEFAULT_COOLDOWN
                : cooldown;
    }

    /** Devre kesici cooldown penceresinde mi? Aktifse çağıran ML'i atlayıp kural tabanlıya düşer. */
    public boolean inCooldown() {
        lock.lock();
        try {
            return Instant.now().isBefore(nextAttempt);
        } finally {
            lock.unlock();
        }
    }

    /** Serving hatasını kaydeder ve ML çağrılarını cooldown süresince askıya alır. */
    public void fail() {
        lock.lock();
        try {
            nextAttempt = Instant.now().plus(cooldown);
        } finally {
            lock.unlock();
        }
    }

    /** Serving yanıt verdiğinde cooldown'ı sıfırlar (serving geri geldiyse hızlı dönüş). */
    public void success() {
        lock.lock();
        try {
            nextAttempt = Instant.EPOCH;
        } finally {
            lock.unlock();
        }
    }
}