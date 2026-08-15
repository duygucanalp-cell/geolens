package dev.geolens.registry;

import dev.geolens.search.SearchClient;
import dev.geolens.search.SearchException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Go {@code registry.handler_test.go} TestESIndexer_* parity testleri —
 * {@code esIndexer} portu (R1).
 */
class EsEntityIndexerTest {

    private static Entity entity() {
        return new Entity("ent-1", "T01", "model", "MyModel", "desc", "1.0.0", "openai",
                "production", "medium", "u1", "https://docs.example.com",
                "", "2026-07-25T10:00:00Z", "2026-07-25T10:00:00Z");
    }

    @Test
    void indexEntity_buildsRegistryDoc() {
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(true);
        EsEntityIndexer indexer = new EsEntityIndexer(client);

        indexer.indexEntity(entity());

        org.mockito.ArgumentCaptor<SearchClient.IndexDoc> cap =
                org.mockito.ArgumentCaptor.forClass(SearchClient.IndexDoc.class);
        verify(client).index(cap.capture());
        SearchClient.IndexDoc doc = cap.getValue();

        assertEquals("geolens-registry", doc.index());
        assertEquals("ent-1", doc.id());
        Map<String, Object> body = doc.body();
        assertEquals("T01", body.get("tenant_id"));
        assertEquals("model", body.get("entity_type"));
        assertEquals("MyModel", body.get("name"));
        assertEquals("desc", body.get("description"));
        assertEquals("1.0.0", body.get("version"));
        assertEquals("openai", body.get("provider"));
        assertEquals("production", body.get("lifecycle_state"));
        assertEquals("medium", body.get("risk_class"));
        assertEquals("u1", body.get("owner"));
        assertEquals("https://docs.example.com", body.get("documentation_url"));
        assertEquals("2026-07-25T10:00:00Z", body.get("updated_at"));
    }

    @Test
    void indexEntity_whenNotConfigured_noop() {
        // Go: esIndexer{client: nil}.IndexEntity → nil, HTTP çağrısı yok
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(false);
        EsEntityIndexer indexer = new EsEntityIndexer(client);

        indexer.indexEntity(entity());

        verify(client, never()).index(any());
    }

    @Test
    void indexEntity_esError_nonFatal() {
        // Go: handler slog.Warn + yanıtı engellemez — non-fatal
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(true);
        doThrow(new SearchException("es hatası (HTTP 500): boom")).when(client).index(any());
        EsEntityIndexer indexer = new EsEntityIndexer(client);

        assertDoesNotThrow(() -> indexer.indexEntity(entity()));
    }

    @Test
    void deleteEntity_noHttpCall() {
        // Go: esIndexer.DeleteEntity yalnızca debug log bırakır (cleanup needed)
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(true);
        EsEntityIndexer indexer = new EsEntityIndexer(client);

        assertDoesNotThrow(() -> indexer.deleteEntity("ent-1"));

        verify(client, never()).index(any());
    }
}
