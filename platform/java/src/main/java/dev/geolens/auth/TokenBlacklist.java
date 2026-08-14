package dev.geolens.auth;

import java.time.Duration;

/**
 * Token blacklist soyutlaması — Go {@code redis.Client} kullanımının karşılığı.
 * {@code null} verilirse blacklist kontrolü atlanır (Go nil rdb ile aynı davranış).
 * Gerçek ortamda Redis uygulaması; spike'ta bellek içi olabilir.
 */
public interface TokenBlacklist {

    /** {@code jti} blacklist'te mi? */
    boolean exists(String jti);

    /** {@code jti} değerini token'ın kalan ömrü boyunca blacklist'e ekler. */
    void set(String jti, Duration ttl);
}
