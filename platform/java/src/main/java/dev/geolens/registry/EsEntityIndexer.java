package dev.geolens.registry;

import dev.geolens.search.SearchClient;
import dev.geolens.search.SearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry varlığını Elasticsearch'e indeksleyen {@link EntityIndexer} — Go
 * {@code registry.esIndexer} portu (R1).
 * <p>Varlıklar {@code geolens-registry} index'ine yazılır; ES hatası non-fatal'dır
 * (loglanır, yanıtı engellemez — Go {@code slog.Warn + devam} davranışı).
 * {@link #deleteEntity} Go ile birebir gerçek DELETE yapmaz, yalnızca debug log bırakır
 * (Go: "registry ES entity deleted (cleanup needed)").
 */
public final class EsEntityIndexer implements EntityIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(EsEntityIndexer.class);
    private static final String INDEX = "geolens-registry";

    private final SearchClient client;

    public EsEntityIndexer(SearchClient client) {
        this.client = client;
    }

    @Override
    public void indexEntity(Entity entity) {
        if (client == null || !client.isConfigured()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", entity.tenantId());
        body.put("entity_type", entity.entityType());
        body.put("name", entity.name());
        body.put("description", entity.description());
        body.put("version", entity.version());
        body.put("provider", entity.provider());
        body.put("lifecycle_state", entity.lifecycleState());
        body.put("risk_class", entity.riskClass());
        body.put("owner", entity.owner());
        body.put("documentation_url", entity.documentationUrl());
        body.put("updated_at", entity.updatedAt());

        try {
            client.index(new SearchClient.IndexDoc(INDEX, entity.id(), body));
        } catch (SearchException e) {
            // Go: slog.Warn("registry ES indeksleme hatası", ...) — non-fatal
            LOG.warn("registry ES indeksleme hatası (non-fatal) — entity_id {}", entity.id(), e);
        }
    }

    @Override
    public void deleteEntity(String entityId) {
        // Go birebir: gerçek DELETE yok, yalnızca log (cleanup needed)
        LOG.debug("registry ES entity deleted (cleanup needed) — entity_id {}", entityId);
    }
}
