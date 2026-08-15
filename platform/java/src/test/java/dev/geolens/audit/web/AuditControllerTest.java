package dev.geolens.audit.web;

import dev.geolens.common.ServiceException;
import dev.geolens.audit.service.AuditWebService;
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

/** Go audit handler davranışı ile route/response parity testleri. */
@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditWebService auditWebService;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    // ---------- RunAudit ----------

    @Test
    void runAuditInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/audit", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void runAuditMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/audit", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"website_url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id ve website_url zorunludur"));
    }

    @Test
    void runAuditMissingWebsiteUrlReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/audit", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"b1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id ve website_url zorunludur"));
    }

    @Test
    void runAuditEmptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/audit", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id ve website_url zorunludur"));
    }

    @Test
    void runAuditBrandNotFoundReturns404() throws Exception {
        when(auditWebService.runAudit(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı"));

        mockMvc.perform(post("/v1/workspaces/{ws}/audit", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"nonexistent\",\"website_url\":\"https://example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void runAuditEngineFailureReturns500() throws Exception {
        when(auditWebService.runAudit(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim başarısız"));

        mockMvc.perform(post("/v1/workspaces/{ws}/audit", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"b1\",\"brand_name\":\"Marka\",\"website_url\":\"https://example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("denetim başarısız"));
    }

    // ---------- Findings ----------

    @Test
    void findingsMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/{ws}/audit/findings", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void findingsReturnEmptyCatalogWhenNoDb() throws Exception {
        when(auditWebService.getFindingsCatalog(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "brand_id", "B01",
                        "catalog", List.of(),
                        "summary", Map.of("total", 0, "critical", 0, "high", 0, "medium", 0, "low", 0)));

        mockMvc.perform(get("/v1/workspaces/{ws}/audit/findings", WS)
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("B01"))
                .andExpect(jsonPath("$.summary.total").value(0));
    }

    @Test
    void findingsServiceErrorReturns500() throws Exception {
        when(auditWebService.getFindingsCatalog(anyString(), anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası"));

        mockMvc.perform(get("/v1/workspaces/{ws}/audit/findings", WS)
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "B01"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("sorgu hatası"));
    }
}
