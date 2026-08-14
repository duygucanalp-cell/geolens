package dev.geolens.measure.web;

import dev.geolens.engine.Registry;
import dev.geolens.measure.MeasureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go measure handler davranışı ile route/response parity testleri. */
@WebMvcTest(MeasureController.class)
class MeasureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeasureService service;

    @MockBean
    private Registry registry;

    @MockBean
    private JdbcTemplate jdbc;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    @Test
    void triggerInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void triggerMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void triggerEmptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void triggerQueuedReturns202() throws Exception {
        when(registry.list()).thenReturn(List.of("chatgpt", "gemini"));
        when(jdbc.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(Map.of("name", "Acme", "website_url", "https://acme.com"));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.brand").value("Acme"))
                .andExpect(jsonPath("$.engines.length()").value(2));
    }

    @Test
    void triggerUnknownBrandReturns404() throws Exception {
        when(registry.list()).thenReturn(List.of("chatgpt"));
        when(jdbc.queryForMap(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("yok") {
                });

        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B99\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void triggerNoEnginesReturns500() throws Exception {
        when(registry.list()).thenReturn(List.of());

        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("kayıtlı motor bulunamadı"));
    }

    @Test
    void listScoresReturnsEmptyWhenNoDb() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("schema yok") {
                });

        mockMvc.perform(get("/v1/workspaces/{ws}/scores", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void citationsMissingParamsReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/{ws}/citations", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id veya job_id parametresi gerekli"));
    }

    @Test
    void extractURLsFindsHttpLinks() {
        List<String> urls = MeasureController.extractURLs(
                "kaynak: https://ornek.com/sayfa ve https://ikinci.org/alt?q=1 ardından.");
        assertTrue(urls.contains("https://ornek.com/sayfa"));
        assertTrue(urls.contains("https://ikinci.org/alt?q=1"));
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("beklenen koşul sağlanmadı");
        }
    }
}
