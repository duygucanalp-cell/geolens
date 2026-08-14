package dev.geolens.auth.web;

import dev.geolens.auth.JWTService;
import dev.geolens.auth.TokenBlacklist;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go auth handler_test davranış parity testleri (e-posta gönderici mevcut + baseURL set). */
@WebMvcTest(value = AuthController.class, properties = "app.base-url=https://app.geolens.ai")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JWTService jwt;

    @MockBean
    private DSLContext dsl;

    @MockBean
    private TransactionTemplate tx;

    @MockBean
    private TokenBlacklist blacklist;

    @MockBean
    private TransactionalMailer mail;

    private static final String TENANT = "T01";

    @Test
    void registerInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void registerMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"password\":\"12345678\",\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("e-posta, şifre ve isim zorunludur"));

        mockMvc.perform(post("/v1/auth/register")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("e-posta, şifre ve isim zorunludur"));
    }

    @Test
    void registerShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"test@test.com\",\"password\":\"123\",\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("şifre en az 8 karakter olmalıdır"));
    }

    @Test
    void loginInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void loginMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"password\":\"12345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("e-posta ve şifre zorunludur"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("e-posta ve şifre zorunludur"));
    }

    @Test
    void logoutReturnsLoggedOut() throws Exception {
        mockMvc.perform(post("/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged_out"));
    }

    @Test
    void refreshRequiresTokenReturns401() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("kimlik doğrulama gerekli"));
    }

    @Test
    void refreshInvalidTokenReturns401() throws Exception {
        when(jwt.validateToken("not-a-jwt")).thenThrow(new dev.geolens.auth.AuthException("jwt doğrulama"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("oturum süresi dolmuş veya geçersiz token"));
    }

    @Test
    void inviteMemberSendsEmail() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post("/v1/tenant/invitations")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"email\":\"yonetici@example.com\",\"workspace_id\":\"WS1\",\"role\":\"editor\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email_sent").value(true))
                .andExpect(jsonPath("$.status").value("invited"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void inviteMemberMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/tenant/invitations")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("e-posta, çalışma alanı ve rol zorunludur"));
    }

    @Test
    void inviteMemberInvalidRoleReturns400() throws Exception {
        mockMvc.perform(post("/v1/tenant/invitations")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"email\":\"a@b.com\",\"workspace_id\":\"WS1\",\"role\":\"superadmin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz rol (admin, editor, viewer)"));
    }

    @Test
    void updateMemberRoleInvalidRoleReturns400() throws Exception {
        mockMvc.perform(patch("/v1/tenant/members/{userId}/role", "U1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"role\":\"hacker\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz rol (admin, editor, viewer)"));
    }

    @Test
    void getTenantWithoutTenantReturns401() throws Exception {
        mockMvc.perform(get("/v1/tenant"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("kimlik doğrulama gerekli"));
    }

    @Test
    void getTenantNotFoundReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessException("yok") {
                });

        mockMvc.perform(get("/v1/tenant")
                        .header("X-Tenant-ID", "BOGUS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("kiracı bulunamadı"));
    }
}
