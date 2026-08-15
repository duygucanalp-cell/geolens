package dev.geolens.search;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Go {@code internal/search/indexer.go} portu — indeks gövdesi ve bool sorgu
 * yapısı Go ile birebir doğrulanır (parity; Go'da bu paketin testi yoktur).
 */
class AuditIndexerTest {

    private static AuditEntry entry() {
        return new AuditEntry("a1", "T01", "u1", "measure", "brand", "b1", "run",
                Map.of("k", "v"), "1.2.3.4", Instant.parse("2026-08-01T10:00:00Z"));
    }

    @Test
    void indexAuditLog_buildsGeolensAuditDoc() {
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(true);
        AuditIndexer indexer = new AuditIndexer(client);

        indexer.indexAuditLog(entry());

        org.mockito.ArgumentCaptor<SearchClient.IndexDoc> cap =
                org.mockito.ArgumentCaptor.forClass(SearchClient.IndexDoc.class);
        verify(client).index(cap.capture());
        SearchClient.IndexDoc doc = cap.getValue();

        assertEquals("geolens-audit", doc.index());
        assertEquals("a1", doc.id());
        Map<String, Object> body = doc.body();
        assertEquals("T01", body.get("tenant_id"));
        assertEquals("u1", body.get("user_id"));
        assertEquals("measure", body.get("event_type"));
        assertEquals("brand", body.get("resource_type"));
        assertEquals("b1", body.get("resource_id"));
        assertEquals("run", body.get("action"));
        assertEquals(Map.of("k", "v"), body.get("metadata"));
        assertEquals("1.2.3.4", body.get("ip_address"));
        // Go: entry.CreatedAt.Format(time.RFC3339)
        assertEquals("2026-08-01T10:00:00Z", body.get("created_at"));
    }

    @Test
    void indexAuditLog_whenNotConfigured_skips() {
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(false);
        AuditIndexer indexer = new AuditIndexer(client);

        indexer.indexAuditLog(entry());

        verify(client, never()).index(any());
    }

    @Test
    void indexAuditLog_esError_propagates() {
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(true);
        doThrow(new SearchException("es hatası (HTTP 500): boom"))
                .when(client).index(any());
        AuditIndexer indexer = new AuditIndexer(client);

        org.junit.jupiter.api.Assertions.assertThrows(SearchException.class,
                () -> indexer.indexAuditLog(entry()));
    }

    @Test
    void searchAuditLog_buildsBoolQuery() {
        SearchClient client = mock(SearchClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.search(anyString(), any())).thenReturn(new SearchResult(0, List.of()));
        AuditIndexer indexer = new AuditIndexer(client);

        indexer.searchAuditLog("T01", "denetim", 10);

        org.mockito.ArgumentCaptor<Map<String, Object>> cap =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(client).search(eq("geolens-audit"), cap.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> bool = (Map<String, Object>) cap.getValue().get("bool");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> must = (List<Map<String, Object>>) bool.get("must");
        assertEquals(2, must.size());
        assertEquals(Map.of("tenant_id", "T01"), must.get(0).get("term"));
        @SuppressWarnings("unchecked")
        Map<String, Object> multiMatch = (Map<String, Object>) must.get(1).get("multi_match");
        assertEquals("denetim", multiMatch.get("query"));
        assertEquals(List.of("event_type", "action", "resource_type"), multiMatch.get("fields"));
        assertTrue(must.get(1).containsKey("multi_match"));
    }
}
