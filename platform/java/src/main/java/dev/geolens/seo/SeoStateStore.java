package dev.geolens.seo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth state token deposu — Go'da {@code governance.cache_store} tablosu (5 dk TTL) ile
 * yapılan geçici state saklamanın spike karşılığı. Redis yoksa memory'de tutulur
 * (Go yorumundaki "Redis yoksa geçici olarak DB kullan" senaryosuna karşılık);
 * spike'ta in-memory TTL store kullanılır.
 */
public class SeoStateStore {

    public static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public void put(String stateToken, String value) {
        store.put(stateToken, new Entry(value, Instant.now().plus(TTL)));
    }

    /** State token'ı döndürür; yoksa veya süresi dolmuşsa null. Tüketimde silinmez (Go ile aynı: ayrıca DELETE). */
    public String get(String stateToken) {
        Entry e = store.get(stateToken);
        if (e == null) {
            return null;
        }
        if (e.expiresAt().isBefore(Instant.now())) {
            store.remove(stateToken);
            return null;
        }
        return e.value();
    }

    /** Callback sonrası state token'ı temizler — Go {@code DELETE FROM governance.cache_store} portu. */
    public void remove(String stateToken) {
        store.remove(stateToken);
    }

    private record Entry(String value, Instant expiresAt) {
    }
}
