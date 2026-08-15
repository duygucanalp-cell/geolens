package dev.geolens.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit günlüğü indeksleyici — Go {@code search.Indexer} portu.
 * <p>GeoLens denetim kayıtlarını {@code geolens-audit} index'ine yazar ve arar.
 * Client endpoint'i boşsa indeksleme atlanır (Go {@code endpoint == "" → nil}).
 */
public final class AuditIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(AuditIndexer.class);
    private static final String INDEX = "geolens-audit";

    private final SearchClient client;

    public AuditIndexer(SearchClient client) {
        this.client = client;
    }

    /**
     * Audit kaydını indeksler — Go {@code Indexer.IndexAuditLog} portu.
     * ES hatası {@link SearchException} fırlatır (çağıran karar verir).
     */
    public void indexAuditLog(AuditEntry entry) {
        if (!client.isConfigured()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", entry.tenantId());
        body.put("user_id", entry.userId());
        body.put("event_type", entry.eventType());
        body.put("resource_type", entry.resourceType());
        body.put("resource_id", entry.resourceId());
        body.put("action", entry.action());
        body.put("metadata", entry.metadata());
        body.put("ip_address", entry.ipAddress());
        body.put("created_at", DateTimeFormatter.ISO_INSTANT.format(entry.createdAt()));

        try {
            client.index(new SearchClient.IndexDoc(INDEX, entry.id(), body));
        } catch (SearchException e) {
            LOG.error("es audit log indexing hatası — id {}", entry.id(), e);
            throw e;
        }
    }

    /**
     * Audit günlüğünde arar — Go {@code Indexer.SearchAuditLog} portu.
     * <p>Go ile birebir: {@code term(tenant_id)} + {@code multi_match} (event_type,
     * action, resource_type). {@code limit} Go tarafında da sorguya yansımaz.
     */
    public SearchResult searchAuditLog(String tenantId, String query, int limit) {
        Map<String, Object> bool = new LinkedHashMap<>();
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(Map.of("term", Map.of("tenant_id", tenantId)));
        must.add(Map.of("multi_match", Map.of(
                "query", query,
                "fields", List.of("event_type", "action", "resource_type"))));
        bool.put("must", must);
        return client.search(INDEX, Map.of("bool", bool));
    }
}
