package dev.geolens.config.web;

import dev.geolens.config.service.PanelService;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go config/{handler_test,panel.go} parity testleri — panel + prompt seti yönetimi. */
@WebMvcTest(PanelController.class)
@Import(PanelService.class)
class PanelControllerTest {

    @MockBean
    private DSLContext dsl;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    private static final String TENANT = "T01";

    @Test
    void createPanelInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/panels")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createPanelMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/panels")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"description\":\"no name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("panel adı zorunludur"));
    }

    @Test
    void createPanelSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record("P-new"));

        mockMvc.perform(post("/v1/workspaces/WS01/panels")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Aylık Panel\",\"brand_ids\":[\"B01\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("P-new"))
                .andExpect(jsonPath("$.name").value("Aylık Panel"))
                .andExpect(jsonPath("$.is_active").value(true));
    }

    @Test
    void listPanelsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(panelRow("P01", "Aylık Panel")));

        mockMvc.perform(get("/v1/workspaces/WS01/panels")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("P01"))
                .andExpect(jsonPath("$[0].name").value("Aylık Panel"));
    }

    @Test
    void getPanelNotFoundReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("no row"));

        mockMvc.perform(get("/v1/workspaces/WS01/panels/unknown")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("panel bulunamadı"));
    }

    @Test
    void createPromptSetInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/prompt-sets")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createPromptSetMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/prompt-sets")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"prompt_text\":\"some prompt\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ad ve prompt metni zorunludur"));

        mockMvc.perform(post("/v1/workspaces/WS01/prompt-sets")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/workspaces/WS01/prompt-sets")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPromptSetSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record("PS-new"));

        mockMvc.perform(post("/v1/workspaces/WS01/prompt-sets")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Marka Anlatımı\",\"prompt_text\":\"Bilgi ver.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("PS-new"))
                .andExpect(jsonPath("$.name").value("Marka Anlatımı"))
                .andExpect(jsonPath("$.is_active").value(true));
    }

    private static Map<String, Object> panelRow(String id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", "");
        m.put("prompt_set_id", "");
        m.put("schedule_cron", "");
        m.put("is_active", true);
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
