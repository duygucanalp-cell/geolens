package dev.geolens.audit.web;

import dev.geolens.audit.AuditService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MockMvc;

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
    private AuditService service;

    @MockBean
    private DSLContext dsl;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

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
    void findingsMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/{ws}/audit/findings", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void findingsReturnEmptyCatalogWhenNoDb() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("yok") {
                });

        mockMvc.perform(get("/v1/workspaces/{ws}/audit/findings", WS)
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("B01"))
                .andExpect(jsonPath("$.summary.total").value(0));
    }
}
