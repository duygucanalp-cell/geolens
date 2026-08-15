package dev.geolens.measure.web;

import dev.geolens.measure.service.MeasureService;
import dev.geolens.common.ServiceException;
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
    private MeasureService measureService;

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
        when(measureService.triggerMeasurement(anyString(), anyString(), any()))
                .thenReturn(Map.of(
                        "status", "queued",
                        "run_id", "R01",
                        "brand", "Acme",
                        "engines", List.of("chatgpt", "gemini")));

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
        when(measureService.triggerMeasurement(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı"));

        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B99\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void triggerNoEnginesReturns500() throws Exception {
        when(measureService.triggerMeasurement(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "kayıtlı motor bulunamadı"));

        mockMvc.perform(post("/v1/workspaces/{ws}/measurements", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("kayıtlı motor bulunamadı"));
    }

    @Test
    void listScoresReturnsEmptyWhenNoDb() throws Exception {
        when(measureService.listScores(anyString(), anyString()))
                .thenReturn(List.of());

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
    void benchmarkContextReturnsStats() throws Exception {
        when(measureService.listBenchmarkContext(anyString(), anyString(), any()))
                .thenReturn(Map.of(
                        "my_score", 72.5,
                        "tenant_count", 6,
                        "sufficient_data", true,
                        "sector_avg", 65.0,
                        "sector_average", 65.0,
                        "sector_median", 64.0,
                        "trend", "up"));

        mockMvc.perform(get("/v1/workspaces/{ws}/benchmark/context", WS)
                        .header("X-Tenant-ID", TENANT)
                        .param("sector", "retail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.my_score").value(72.5))
                .andExpect(jsonPath("$.tenant_count").value(6))
                .andExpect(jsonPath("$.sufficient_data").value(true))
                .andExpect(jsonPath("$.sector_average").value(65.0))
                .andExpect(jsonPath("$.sector_median").value(64.0));
    }

    @Test
    void benchmarkContextInsufficientDataReturnsMessage() throws Exception {
        when(measureService.listBenchmarkContext(anyString(), anyString(), any()))
                .thenReturn(Map.of(
                        "my_score", 50.0,
                        "tenant_count", 2,
                        "sufficient_data", false,
                        "message", "yetersiz veri — anonim kıyas için en az 5 kiracı gerekli"));

        mockMvc.perform(get("/v1/workspaces/{ws}/benchmark/context", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sufficient_data").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void extractURLsFindsHttpLinks() {
        List<String> urls = MeasureService.extractURLs(
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
