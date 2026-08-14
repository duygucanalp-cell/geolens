package dev.geolens.registry;

/**
 * Registry varlığı indeksleyici — Go {@code Indexer} arayüzü portu (R1).
 * <p>Spike'ta Elasticsearch bağımlılığı yok; {@link #noop()} varsayılan noop
 * uygulamasıdır (Go {@code noopIndexer} karşılığı). Üretimde ES client ile
 * değiştirilir.
 */
public interface EntityIndexer {

    void indexEntity(Entity entity);

    void deleteEntity(String entityId);

    /** Go {@code noopIndexer} karşılığı — hiçbir şey yapmaz. */
    static EntityIndexer noop() {
        return new EntityIndexer() {
            @Override
            public void indexEntity(Entity entity) {
            }

            @Override
            public void deleteEntity(String entityId) {
            }
        };
    }
}
