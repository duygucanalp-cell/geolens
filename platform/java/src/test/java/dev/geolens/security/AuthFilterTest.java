package dev.geolens.security;

import dev.geolens.auth.AuthException;
import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import dev.geolens.auth.TokenValidator;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Go httpmw middleware_test.go parity — JWT auth, tenant override (X-Tenant-ID spoofing
 * engeli), rol hiyerarşisi (admin/editor/viewer) ve tier (pro) yetkilendirme.
 */
class AuthFilterTest {

    private static final String SECRET = "test-secret";

    private JWTService jwt;
    private MockMvc mvc;

    @RestController
    static class StubController {
        @GetMapping("/v1/auth/register")
        public ResponseEntity<?> register() {
            return ResponseEntity.ok(Map.of("status", "public"));
        }

        @GetMapping("/v1/workspaces/{ws}/brands")
        public ResponseEntity<?> brands(@RequestHeader("X-Tenant-ID") String tenant) {
            return ResponseEntity.ok(Map.of("tenant", tenant));
        }

        @PostMapping("/v1/workspaces/{ws}/measurements")
        public ResponseEntity<?> measurements() {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }

        @PostMapping("/v1/api-keys")
        public ResponseEntity<?> apiKeys() {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }

        @PostMapping("/v1/tenant/invitations")
        public ResponseEntity<?> invitations() {
            return ResponseEntity.ok(Map.of("status", "invited"));
        }
    }

    private String token(String role, String tenantId) {
        return jwt.generateToken("user-1", tenantId, role).token();
    }

    @BeforeEach
    void setUp() {
        jwt = new JWTService(SECRET);
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new AuthFilter(jwt.tokenValidator(null), null))
                .build();
    }

    // ---------- Authenticate ----------

    @Test
    void publicPath_skipsAuth() throws Exception {
        mvc.perform(get("/v1/auth/register"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("public"));
    }

    @Test
    void missingToken_returns401AuthorizationRequired() throws Exception {
        mvc.perform(get("/v1/workspaces/WS01/brands"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authorization_required"));
    }

    @Test
    void invalidToken_returns401InvalidToken() throws Exception {
        mvc.perform(get("/v1/workspaces/WS01/brands")
                        .header("Authorization", "Bearer bogus.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void validViewerToken_readAllowed() throws Exception {
        mvc.perform(get("/v1/workspaces/WS01/brands")
                        .header("Authorization", "Bearer " + token("viewer", "T01")))
                .andExpect(status().isOk());
    }

    // ---------- Tenant override (X-Tenant-ID spoofing engeli) ----------

    @Test
    void tenantHeader_overriddenWithJwtClaim() throws Exception {
        // İstemci X-Tenant-ID: OTHER gönderir; JWT'deki kiracı (T42) kazanmalı
        mvc.perform(get("/v1/workspaces/WS01/brands")
                        .header("Authorization", "Bearer " + token("viewer", "T42"))
                        .header("X-Tenant-ID", "OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").value("T42"));
    }

    // ---------- RequireRole ----------

    @Test
    void viewerWrite_denied403() throws Exception {
        mvc.perform(post("/v1/workspaces/WS01/measurements")
                        .header("Authorization", "Bearer " + token("viewer", "T01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("insufficient_permissions"));
    }

    @Test
    void editorWrite_allowed() throws Exception {
        mvc.perform(post("/v1/workspaces/WS01/measurements")
                        .header("Authorization", "Bearer " + token("editor", "T01")))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoute_viewerDenied() throws Exception {
        mvc.perform(post("/v1/api-keys")
                        .header("Authorization", "Bearer " + token("viewer", "T01")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoute_editorDenied() throws Exception {
        mvc.perform(post("/v1/api-keys")
                        .header("Authorization", "Bearer " + token("editor", "T01")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoute_adminAllowed() throws Exception {
        mvc.perform(post("/v1/api-keys")
                        .header("Authorization", "Bearer " + token("admin", "T01")))
                .andExpect(status().isOk());
    }

    @Test
    void unknownRole_normalizesToViewer() throws Exception {
        // 'member' (identity.users sözlüğü) → viewer: okuma açık, yazma kapalı
        mvc.perform(get("/v1/workspaces/WS01/brands")
                        .header("Authorization", "Bearer " + token("member", "T01")))
                .andExpect(status().isOk());

        mvc.perform(post("/v1/workspaces/WS01/measurements")
                        .header("Authorization", "Bearer " + token("member", "T01")))
                .andExpect(status().isForbidden());
    }

    // ---------- RequireTier ----------

    @Test
    void freeTenant_proRoute_returns402() throws Exception {
        MockMvc proMvc = mvcWithTier("free");
        proMvc.perform(post("/v1/tenant/invitations")
                        .header("Authorization", "Bearer " + token("admin", "T01")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("plan_upgrade_required"));
    }

    @Test
    void proTenant_proRoute_allowed() throws Exception {
        MockMvc proMvc = mvcWithTier("pro");
        proMvc.perform(post("/v1/tenant/invitations")
                        .header("Authorization", "Bearer " + token("admin", "T01")))
                .andExpect(status().isOk());
    }

    @Test
    void enterpriseTenant_proRoute_allowed() throws Exception {
        MockMvc proMvc = mvcWithTier("enterprise");
        proMvc.perform(post("/v1/tenant/invitations")
                        .header("Authorization", "Bearer " + token("admin", "T01")))
                .andExpect(status().isOk());
    }

    // ---------- Blacklist ----------

    @Test
    void blacklistedToken_rejected401() throws Exception {
        String token = token("viewer", "T01");
        String jti = jtiOf(token);

        TokenBlacklist blacklist = mock(TokenBlacklist.class);
        when(blacklist.exists("token:blacklist:" + jti)).thenReturn(true);

        MockMvc blacklistedMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new AuthFilter(jwt.tokenValidator(blacklist), null))
                .build();
        blacklistedMvc.perform(get("/v1/workspaces/WS01/brands")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    private static String jtiOf(String token) {
        String payload = token.split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = json.indexOf("\"jti\":\"") + 7;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    // ---------- yardımcılar ----------

    private MockMvc mvcWithTier(String tier) {
        DSLContext dsl = mock(DSLContext.class);
        Record rec = mock(Record.class);
        when(rec.get(0)).thenReturn(tier);
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(rec);
        return MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new AuthFilter(jwt.tokenValidator(null), dsl))
                .build();
    }
}
