package dev.geolens.registry.web;

import dev.geolens.registry.Entity;
import dev.geolens.registry.service.RegistryService;
import dev.geolens.registry.service.RegistryServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go registry/handler_test.go parity testleri — AI Registry REST (R1). */
@WebMvcTest(RegistryController.class)
class RegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistryService registryService;

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
        when(registryService.createEntity(anyString(), any()))
                .thenThrow(new RegistryServiceException(HttpStatus.BAD_REQUEST,
                        "geçersiz entity_type: model, agent, application, dataset"));
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
        when(registryService.createEntity(anyString(), any()))
                .thenReturn(entity("ent-001", "model", "MyModel"));

        mockMvc.perform(post("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"model\", \"name\": \"MyModel\", \"description\": \"Test model description\", \"version\": \"1.0.0\", \"provider\": \"openai\", \"owner\": \"user-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ent-001"))
                .andExpect(jsonPath("$.name").value("MyModel"))
                .andExpect(jsonPath("$.entity_type").value("model"));
    }

    @Test
    void createDbErrorReturns500() throws Exception {
        when(registryService.createEntity(anyString(), any()))
                .thenThrow(new RegistryServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "varlık kaydedilemedi"));

        mockMvc.perform(post("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"model\", \"name\": \"MyModel\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("varlık kaydedilemedi"));
    }

    @Test
    void createDefaultValues() throws Exception {
        when(registryService.createEntity(anyString(), any()))
                .thenReturn(entity("ent-002", "agent", "MyAgent"));

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
        when(registryService.listEntities(anyString(), any(), any(), any()))
                .thenReturn(List.of(entity("ent-001", "model", "Model A"),
                        entity("ent-002", "agent", "Agent B")));

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.length()").value(2))
                .andExpect(jsonPath("$.entities[0].name").value("Model A"))
                .andExpect(jsonPath("$.entities[1].entity_type").value("agent"));
    }

    @Test
    void listEmpty() throws Exception {
        when(registryService.listEntities(anyString(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray());
    }

    @Test
    void listQueryErrorGraceful() throws Exception {
        when(registryService.listEntities(anyString(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/registry/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities").isArray());
    }

    @Test
    void listWithFilters() throws Exception {
        when(registryService.listEntities(anyString(), any(), any(), any()))
                .thenReturn(List.of(entity("ent-001", "model", "Model A")));

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
        when(registryService.getEntity(anyString(), anyString()))
                .thenReturn(entity("ent-001", "model", "Model A"));

        mockMvc.perform(get("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ent-001"))
                .andExpect(jsonPath("$.name").value("Model A"));
    }

    @Test
    void getNotFound() throws Exception {
        when(registryService.getEntity(anyString(), anyString()))
                .thenThrow(new RegistryServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı"));

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
        Entity updated = new Entity("ent-001", TENANT, "model", "Updated Name", "Description", "2.0.0", "openai",
                "production", "high", "user-1", "https://docs.example.com",
                null, "2026-07-25T10:00:00Z", "2026-07-25T10:00:00Z");
        when(registryService.updateEntity(anyString(), anyString(), any()))
                .thenReturn(updated);

        mockMvc.perform(put("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\": \"Updated Name\", \"version\": \"2.0.0\", \"lifecycle_state\": \"production\", \"risk_class\": \"high\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.version").value("2.0.0"));
    }

    @Test
    void updateNotFound() throws Exception {
        when(registryService.updateEntity(anyString(), anyString(), any()))
                .thenThrow(new RegistryServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı"));

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
        when(registryService.deleteEntity(anyString(), anyString()))
                .thenReturn(Map.of("status", "silindi"));

        mockMvc.perform(delete("/v1/registry/entities/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("silindi"));
    }

    @Test
    void deleteNotFound() throws Exception {
        when(registryService.deleteEntity(anyString(), anyString()))
                .thenThrow(new RegistryServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı"));

        mockMvc.perform(delete("/v1/registry/entities/nonexistent")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlık bulunamadı"));
    }

    @Test
    void deleteDbErrorReturns500() throws Exception {
        when(registryService.deleteEntity(anyString(), anyString()))
                .thenThrow(new RegistryServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "silme hatası"));

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
        when(registryService.assessRisk(anyString(), anyString(), any()))
                .thenReturn(Map.of("id", "assessment-001", "status", "değerlendirildi"));

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
        when(registryService.assessRisk(anyString(), anyString(), any()))
                .thenThrow(new RegistryServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "değerlendirme kaydedilemedi"));

        mockMvc.perform(post("/v1/registry/entities/ent-001/assess")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"risk_class\": \"low\", \"score\": 10.0, \"summary\": \"Low risk\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("değerlendirme kaydedilemedi"));
    }

    // ---------- yardımcılar ----------

    private static Entity entity(String id, String entityType, String name) {
        return new Entity(id, TENANT, entityType, name, "Description", "1.0.0", "openai",
                "development", "medium", "user-1", "https://docs.example.com",
                null, "2026-07-25T10:00:00Z", "2026-07-25T10:00:00Z");
    }
}
