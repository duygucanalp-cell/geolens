package dev.geolens.seo.web;

import dev.geolens.seo.service.SeoService;
import dev.geolens.common.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEO entegrasyon REST testleri — Go {@code seo.handler} davranış parity (Go'da test yok). */
@WebMvcTest(SeoController.class)
class SeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeoService seoService;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    // ---------- ListConnections ----------

    @Test
    void listConnectionsSuccess() throws Exception {
        when(seoService.listConnections(anyString(), anyString()))
                .thenReturn(List.of(connectionRow()));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/connections")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("conn-1"))
                .andExpect(jsonPath("$[0].platform").value("search_console"))
                .andExpect(jsonPath("$[0].is_active").value(true));
    }

    @Test
    void listConnectionsQueryErrorReturnsEmpty() throws Exception {
        when(seoService.listConnections(anyString(), anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/connections")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- GetAuthURL ----------

    @Test
    void getAuthUrlInvalidPlatformReturns400() throws Exception {
        when(seoService.getAuthUrl(anyString(), anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "platform search_console veya ga4 olmalıdır"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/auth-url")
                        .header("X-Tenant-ID", TENANT)
                        .param("platform", "twitter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("platform search_console veya ga4 olmalıdır"));
    }

    @Test
    void getAuthUrlNotConfiguredReturns400() throws Exception {
        when(seoService.getAuthUrl(anyString(), anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "Google OAuth yapılandırılmamış"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/auth-url")
                        .header("X-Tenant-ID", TENANT)
                        .param("platform", "search_console"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Google OAuth yapılandırılmamış"));
    }

    @Test
    void getAuthUrlSuccess() throws Exception {
        when(seoService.getAuthUrl(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "auth_url", "https://accounts.google.com/o/oauth2/v2/auth?client_id=c&access_type=offline&response_type=code&scope=s",
                        "state_token", "abc123"));

        MvcResult res = mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/auth-url")
                        .header("X-Tenant-ID", TENANT)
                        .param("platform", "search_console"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state_token").isNotEmpty())
                .andReturn();

        String authUrl = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(res.getResponse().getContentAsString()).path("auth_url").asText();
        assertTrue(authUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        assertTrue(authUrl.contains("access_type=offline"));
        assertTrue(authUrl.contains("response_type=code"));
    }

    // ---------- HandleCallback ----------

    @Test
    void callbackMissingParamsReturns400() throws Exception {
        when(seoService.handleCallback(any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "code ve state parametreleri gerekli"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("code ve state parametreleri gerekli"));
    }

    @Test
    void callbackInvalidStateReturns400() throws Exception {
        when(seoService.handleCallback(any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz state token"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/callback")
                        .param("code", "auth-code")
                        .param("state", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz state token"));
    }

    @Test
    void callbackWorkspaceMismatchReturns400() throws Exception {
        when(seoService.handleCallback(any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.BAD_REQUEST, "workspace eşleşmez"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/callback")
                        .param("code", "auth-code")
                        .param("state", "valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("workspace eşleşmez"));
    }

    @Test
    void callbackSuccessRedirects() throws Exception {
        when(seoService.handleCallback(any(), any(), any()))
                .thenReturn("http://localhost:8080/?seo=connected&platform=search_console");

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/callback")
                        .param("code", "auth-code")
                        .param("state", "valid"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("http://localhost:8080/?seo=connected&platform=search_console"));
    }

    @Test
    void callbackExchangeErrorReturns500() throws Exception {
        when(seoService.handleCallback(any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "token alınamadı"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/callback")
                        .param("code", "bad-code")
                        .param("state", "valid"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("token alınamadı"));
    }

    // ---------- Disconnect ----------

    @Test
    void disconnectSuccess() throws Exception {
        when(seoService.disconnect(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "status", "disconnected",
                        "platform", "search_console"));

        mockMvc.perform(delete("/v1/workspaces/" + WS + "/seo/connections/search_console")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disconnected"))
                .andExpect(jsonPath("$.platform").value("search_console"));
    }

    // ---------- GetSearchConsoleData / GetGA4Data ----------

    @Test
    void getSearchConsoleDataSuccess() throws Exception {
        when(seoService.getSearchConsoleData(anyString(), anyString()))
                .thenReturn(List.of(scRow()));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/search-console")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "brand-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].query").value("ai visibility"))
                .andExpect(jsonPath("$[0].clicks").value(120))
                .andExpect(jsonPath("$[0].measured_at").value("2026-08-01"));
    }

    @Test
    void getGa4DataSuccess() throws Exception {
        when(seoService.getGa4Data(anyString(), anyString()))
                .thenReturn(List.of(ga4Row()));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/seo/ga4")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "brand-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].page_views").value(1500))
                .andExpect(jsonPath("$[0].sessions").value(320))
                .andExpect(jsonPath("$[0].measured_at").value("2026-08-01"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> connectionRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "conn-1");
        m.put("platform", "search_console");
        m.put("email", "owner@example.com");
        m.put("is_active", true);
        m.put("last_synced_at", null);
        m.put("created_at", "2026-08-01T00:00:00Z");
        return m;
    }

    private static Map<String, Object> scRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("query", "ai visibility");
        m.put("clicks", 120L);
        m.put("impressions", 1500L);
        m.put("ctr", 0.08);
        m.put("avg_position", 4.2);
        m.put("measured_at", java.sql.Date.valueOf("2026-08-01"));
        return m;
    }

    private static Map<String, Object> ga4Row() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page_views", 1500L);
        m.put("sessions", 320L);
        m.put("bounce_rate", 0.45);
        m.put("avg_session_duration", 95.0);
        m.put("measured_at", java.sql.Date.valueOf("2026-08-01"));
        return m;
    }
}
