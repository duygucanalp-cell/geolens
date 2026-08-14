package dev.geolens.config.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go config/{handler_test,competitor_test}.go parity testleri — marka + rakip yönetimi. */
@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbc;

    @MockBean
    private TransactionTemplate tx;

    private static final String TENANT = "T01";

    private void runInTx() {
        when(tx.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<String[]> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
    }

    // ---------- SearchBrands ----------

    @Test
    void searchBrandsSuccess() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(2);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of(
                        row("B01", "Acme Corp", "https://acme.com"),
                        row("B02", "Acme Ltd", "https://acme-ltd.com")));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.data[0].id").value("B01"))
                .andExpect(jsonPath("$.data[0].name").value("Acme Corp"))
                .andExpect(jsonPath("$.data[1].id").value("B02"));
    }

    @Test
    void searchBrandsMissingQueryReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("q parametresi gerekli"));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBrandsEmptyResults() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "nonexistent")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void searchBrandsWithExclude() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of(row("B02", "Acme Ltd", "https://acme-ltd.com")));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .param("exclude", "B01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value("B02"));
    }

    @Test
    void searchBrandsWithPagination() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(2);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of(row("B01", "Acme Corp", "https://acme.com")));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .param("offset", "1")
                        .param("limit", "1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(1))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void searchBrandsWithLargeLimitCappedAt100() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(150);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of(row("B01", "Acme Corp", "https://acme.com")));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .param("limit", "200")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100));
    }

    @Test
    void searchBrandsWithInvalidOffsetDefaultsTo0() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .param("offset", "-5")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offset").value(0));
    }

    @Test
    void searchBrandsCountErrorReturns500() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("count error"));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("sorgu hatası"));
    }

    @Test
    void searchBrandsDBErrorReturns500() throws Exception {
        when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Integer.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/search")
                        .param("q", "Acme")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError());
    }

    // ---------- CreateBrand ----------

    @Test
    void createBrandInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/brands")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createBrandMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/brands")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"website_url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("marka adı ve web sitesi zorunludur"));

        mockMvc.perform(post("/v1/workspaces/WS01/brands")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/workspaces/WS01/brands")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBrandSuccess() throws Exception {
        runInTx();
        when(jdbc.queryForObject(anyString(), eq(String.class), any(), any(), any(), any()))
                .thenReturn("B-new");

        mockMvc.perform(post("/v1/workspaces/WS01/brands")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Acme Corp\",\"website_url\":\"https://acme.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("B-new"))
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.website_url").value("https://acme.com"));
    }

    @Test
    void createBrandCompetitorNotFoundReturns400() throws Exception {
        runInTx();
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(false);

        mockMvc.perform(post("/v1/workspaces/WS01/brands")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Acme\",\"website_url\":\"https://acme.com\",\"competitors\":[\"nope\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rakip bulunamadı: nope"));
    }

    // ---------- UpdateBrand ----------

    @Test
    void updateBrandSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForMap(contains("FROM config.brands WHERE id"), any(), any(), any()))
                .thenReturn(row("B01", "Yeni Marka", "https://yeni-site.com"));

        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Yeni Marka\",\"website_url\":\"https://yeni-site.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("B01"))
                .andExpect(jsonPath("$.name").value("Yeni Marka"))
                .andExpect(jsonPath("$.website_url").value("https://yeni-site.com"));
    }

    @Test
    void updateBrandInvalidJSONReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBrandNoFieldsReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("en az bir alan gerekli (name veya website_url)"));
    }

    @Test
    void updateBrandNotFoundReturns404() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(put("/v1/workspaces/WS01/brands/nonexistent")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Yeni\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void updateBrandDBErrorReturns500() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Yeni\"}"))
                .andExpect(status().isInternalServerError());
    }

    // ---------- DeleteBrand ----------

    @Test
    void deleteBrandSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"))
                .andExpect(jsonPath("$.brand_id").value("B01"));
    }

    @Test
    void deleteBrandNotFoundReturns404() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBrandDBErrorReturns500() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError());
    }

    // ---------- DeleteBrandCompetitor ----------

    @Test
    void deleteBrandCompetitorSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01/competitors/C01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"))
                .andExpect(jsonPath("$.competitor_id").value("C01"));
    }

    @Test
    void deleteBrandCompetitorNotFoundReturns404() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01/competitors/nonexistent")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBrandCompetitorSelfReferenceReturns400() throws Exception {
        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01/competitors/B01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("kendi kendine rakip ilişkisi silinemez"));
    }

    @Test
    void deleteBrandCompetitorDBErrorReturns500() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(delete("/v1/workspaces/WS01/brands/B01/competitors/C01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError());
    }

    // ---------- ListBrandCompetitors ----------

    @Test
    void listBrandCompetitorsSuccess() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of(
                        competitorRow("comp-1", "Rakip A"),
                        competitorRow("comp-2", "Rakip B")));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/B01/competitors")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competitor_id").value("comp-1"))
                .andExpect(jsonPath("$[0].competitor_name").value("Rakip A"))
                .andExpect(jsonPath("$[1].competitor_id").value("comp-2"));
    }

    @Test
    void listBrandCompetitorsEmpty() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/v1/workspaces/WS01/brands/B01/competitors")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listBrandCompetitorsBrandNotFoundReturns404() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(false);

        mockMvc.perform(get("/v1/workspaces/WS01/brands/unknown/competitors")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void listBrandCompetitorsQueryErrorReturns500() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true);
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/WS01/brands/B01/competitors")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError());
    }

    // ---------- UpdateBrandCompetitors ----------

    @Test
    void updateBrandCompetitorsSuccess() throws Exception {
        runInTx();
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true, true);

        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01/competitors")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"competitors\":[\"comp-1\",\"comp-2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"))
                .andExpect(jsonPath("$.competitors[0]").value("comp-1"));
    }

    @Test
    void updateBrandCompetitorsInvalidJSONReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01/competitors")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBrandCompetitorsBrandNotFoundReturns404() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(false);

        mockMvc.perform(put("/v1/workspaces/WS01/brands/unknown/competitors")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"competitors\":[\"comp-1\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBrandCompetitorsCompetitorNotFoundReturns400() throws Exception {
        runInTx();
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true, false);

        mockMvc.perform(put("/v1/workspaces/WS01/brands/B01/competitors")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"competitors\":[\"nonexistent\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rakip bulunamadı: nonexistent"));
    }

    // ---------- GetSetupStatus ----------

    @Test
    void setupStatusAllDone() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(3);

        mockMvc.perform(get("/v1/workspaces/WS01/setup-status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setup_complete").value(true))
                .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    void setupStatusNotComplete() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(0);

        mockMvc.perform(get("/v1/workspaces/WS01/setup-status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setup_complete").value(false));
    }

    // ---------- ListWorkspacePanorama ----------

    @Test
    void panoramaSuccess() throws Exception {
        when(jdbc.queryForList(anyString(), (Object[]) any()))
                .thenReturn(java.util.List.of(panoramaRow("WS1", "Ajans A", 75.5, 3, 12, false)));

        mockMvc.perform(get("/v1/tenant/panorama")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces[0].id").value("WS1"))
                .andExpect(jsonPath("$.workspaces[0].avg_score").value(75.5))
                .andExpect(jsonPath("$.workspaces[0].brand_count").value(3));
    }

    @Test
    void panoramaQueryErrorReturnsEmpty() throws Exception {
        when(jdbc.queryForList(anyString(), (Object[]) any())).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/tenant/panorama")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces").isArray());
    }

    // ---------- helpers ----------

    private static java.util.Map<String, Object> row(String id, String name, String url) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("website_url", url);
        return m;
    }

    private static java.util.Map<String, Object> competitorRow(String id, String name) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("competitor_id", id);
        m.put("competitor_name", name);
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }

    private static java.util.Map<String, Object> panoramaRow(String id, String name, double avg, int brands, int meas, boolean archived) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("avg_score", avg);
        m.put("brand_count", brands);
        m.put("measurement_count", meas);
        m.put("archived", archived);
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
