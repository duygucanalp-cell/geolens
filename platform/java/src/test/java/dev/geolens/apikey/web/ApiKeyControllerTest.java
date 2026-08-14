package dev.geolens.apikey.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go apikey/handler_test.go parity testleri — API anahtarları. */
@WebMvcTest(ApiKeyController.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- List ----------

    @Test
    void listSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(keyRow("K1", "prod-key", "gls_abc123", "viewer", true)));

        mockMvc.perform(get("/v1/api-keys").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].id").value("K1"))
                .andExpect(jsonPath("$.keys[0].name").value("prod-key"))
                .andExpect(jsonPath("$.keys[0].key_prefix").value("gls_abc123"))
                .andExpect(jsonPath("$.keys[0].role").value("viewer"))
                .andExpect(jsonPath("$.keys[0].is_active").value(true));
    }

    @Test
    void listQueryErrorReturnsEmpty() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/api-keys").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray());
    }

    // ---------- Create ----------

    @Test
    void createInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/api-keys")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/v1/api-keys")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"role\":\"viewer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("isim zorunludur"));
    }

    @Test
    void createInvalidRoleReturns400() throws Exception {
        mockMvc.perform(post("/v1/api-keys")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"test-key\",\"role\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rol yalnızca viewer olabilir"));
    }

    @Test
    void createSuccess() throws Exception {
        when(dsl.fetchOne(contains("INSERT INTO identity.api_keys"), any(Object[].class)))
                .thenReturn(JooqTestData.record("new-key-id"));

        mockMvc.perform(post("/v1/api-keys")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"prod-key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-key-id"))
                .andExpect(jsonPath("$.api_key").isString())
                .andExpect(jsonPath("$.api_key").value(org.hamcrest.Matchers.startsWith("gls_")))
                .andExpect(jsonPath("$.key_prefix").isString())
                .andExpect(jsonPath("$.warning").value("anahtar yalnızca bir kez gösterilir; kopyalayın"));
    }

    // ---------- Delete ----------

    @Test
    void deleteSuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/api-keys/K1").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));
    }

    @Test
    void deleteNotFoundReturns404() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/api-keys/none").header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("anahtar bulunamadı"));
    }

    // ---------- helpers ----------

    private static Map<String, Object> keyRow(String id, String name, String prefix, String role, boolean active) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("key_prefix", prefix);
        m.put("role", role);
        m.put("is_active", active);
        m.put("last_used_at", null);
        m.put("expires_at", null);
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
