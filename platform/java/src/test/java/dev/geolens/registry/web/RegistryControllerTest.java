package dev.geolens.registry.web;

import dev.geolens.registry.Entity;
import dev.geolens.registry.EntityIndexer;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go registry/handler_test.go parity testleri — AI Registry REST (R1). */
@WebMvcTest(RegistryController.class)
@Import(RegistryControllerTest.IndexerConfig.class)
class RegistryControllerTest {

    @TestConfiguration
    static class IndexerConfig {
        @Bean
        public EntityIndexer entityIndexer() {
            return Mockito.mock(EntityIndexer.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @Autowired
    private EntityIndexer indexer;

    private static final String TENANT = "T01";

    // ---------- Create ----------

    @Test
    void createInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createInvalidEntityTypeReturns400() throws Exception {
        // Go: unsupported type, empty type, random string → 400
        for (String et : new String[]{"llm", "", "foobar"}) {
            mockMvc.perform(post("/v1/registry/entities")
                            .header("X-Tenant-ID", TENANT)
                            .contentType("application/json")
                            .content("{\"entity_type\": \"" + et + "\", \"name\": \"test-model\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("geçersiz entity_type: model, agent, application, dataset"));
        }
    }

    @Test
    void createSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(entityRow("ent-001", "model", "MyModel")));

        mockMvc.perform(post("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"model\", \"name\": \"MyModel\", \"description\": \"Test model description\", \"version\": \"1.0.0\", \"provider\": \"openai\", \"owner\": \"user-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ent-001"))
                .andExpect(jsonPath("$.name").value("MyModel"))
                .andExpect(jsonPath("$.entity_type").value("model"));

        verify(indexer).indexEntity(any(Entity.class));
    }

    @Test
    void createDbErrorReturns500() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"model\", \"name\": \"MyModel\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("varlık kaydedilemedi"));
    }

    @Test
    void createDefaultValues() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(entityRow("ent-002", "agent", "MyAgent")));

        mockMvc.perform(post("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"agent\", \"name\": \"MyAgent\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ent-002"));
    }

    // ---------- List ----------

    @Test
    void listSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        entityRow("ent-001", "model", "Model A"),
                        entityRow("ent-002", "agent", "Agent B"))));

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.length()").value(2))
                .andExpect(jsonPath("$.entities[0].name").value("Model A"))
                .andExpect(jsonPath("$.entities[1].entity_type").value("agent"));
    }

    @Test
    void listEmpty() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray());
    }

    @Test
    void listQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection error"));

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray());
    }

    @Test
    void listWithFilters() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(entityRow("ent-001", "model", "Model A"))));

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_type", "model")
                        .param("lifecycle_state", "production")
                        .param("risk_class", "medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[0].name").value("Model A"));
    }

    // ---------- Get ----------

    @Test
    void getSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(entityRow("ent-001", "model", "Model A")));

        mockMvc.perform(get("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ent-001"))
                .andExpect(jsonPath("$.name").value("Model A"));
    }

    @Test
    void getNotFound() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/v1/registry/entities/nonexistent")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlık bulunamadı"));
    }

    // ---------- Update ----------

    @Test
    void updateInvalidJsonReturns400() throws Exception {
        mockMvc.perform(put("/v1/registry/entities/123")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateSuccess() throws Exception {
        Map<String, Object> row = entityRow("ent-001", "model", "Updated Name");
        row.put("version", "2.0.0");
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(put("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\": \"Updated Name\", \"version\": \"2.0.0\", \"lifecycle_state\": \"production\", \"risk_class\": \"high\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.version").value("2.0.0"));

        verify(indexer).indexEntity(any(Entity.class));
    }

    @Test
    void updateNotFound() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(put("/v1/registry/entities/nonexistent")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\": \"New Name\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlık bulunamadı"));
    }

    // ---------- Delete ----------

    @Test
    void deleteSuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("silindi"));

        verify(indexer).deleteEntity("ent-001");
    }

    @Test
    void deleteNotFound() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/registry/entities/nonexistent")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlık bulunamadı"));
    }

    @Test
    void deleteDbErrorReturns500() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(delete("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("silme hatası"));
    }

    // ---------- AssessRisk ----------

    @Test
    void assessRiskInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/registry/entities/123/assess")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void assessRiskSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("id", "assessment-001")));

        mockMvc.perform(post("/v1/registry/entities/ent-001/assess")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"risk_class\": \"high\", \"score\": 85.5, \"summary\": \"High risk due to PII processing\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("assessment-001"))
                .andExpect(jsonPath("$.status").value("değerlendirildi"));
    }

    @Test
    void assessRiskDbErrorReturns500() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/registry/entities/ent-001/assess")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"risk_class\": \"low\", \"score\": 10.0, \"summary\": \"Low risk\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("değerlendirme kaydedilemedi"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> entityRow(String id, String entityType, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenant_id", TENANT);
        m.put("entity_type", entityType);
        m.put("name", name);
        m.put("description", "Description");
        m.put("version", "1.0.0");
        m.put("provider", "openai");
        m.put("lifecycle_state", "development");
        m.put("risk_class", "medium");
        m.put("owner", "user-1");
        m.put("documentation_url", "https://docs.example.com");
        m.put("deployed_at", null);
        m.put("created_at", "2026-07-25T10:00:00Z");
        m.put("updated_at", "2026-07-25T10:00:00Z");
        return m;
    }
}
