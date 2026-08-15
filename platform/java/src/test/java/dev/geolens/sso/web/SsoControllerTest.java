package dev.geolens.sso.web;

import dev.geolens.sso.KeyPairGeneratorUtil;
import dev.geolens.sso.SsoConfig;
import dev.geolens.sso.service.SsoService;
import dev.geolens.sso.service.SsoServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go sso.handler davranış parity testleri — SSO/SAML REST. */
@WebMvcTest(SsoController.class)
class SsoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SsoService ssoService;

    private static final String TENANT = "T01";

    // ---------- GetConfig ----------

    @Test
    void getConfigNotFound() throws Exception {
        when(ssoService.getConfig(anyString()))
                .thenThrow(new SsoServiceException(HttpStatus.NOT_FOUND, "SSO yapılandırması bulunamadı"));

        mockMvc.perform(get("/v1/sso/config")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SSO yapılandırması bulunamadı"));
    }

    @Test
    void getConfigSuccess() throws Exception {
        when(ssoService.getConfig(anyString())).thenReturn(config());

        mockMvc.perform(get("/v1/sso/config")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idp_entity_id").value("https://idp.example.com"))
                .andExpect(jsonPath("$.idp_sso_url").value("https://idp.example.com/sso"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    // ---------- UpdateConfig ----------

    @Test
    void updateConfigInvalidJsonReturns400() throws Exception {
        mockMvc.perform(put("/v1/sso/config")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateConfigSuccess() throws Exception {
        when(ssoService.updateConfig(anyString(), any())).thenReturn(config());

        mockMvc.perform(put("/v1/sso/config")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"idp_entity_id\": \"https://idp.example.com\", \"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idp_entity_id").value("https://idp.example.com"))
                .andExpect(jsonPath("$.tenant_id").value(TENANT));
    }

    // ---------- GetSPMetadata ----------

    @Test
    void getSpMetadataNotFound() throws Exception {
        when(ssoService.getSpMetadata(anyString()))
                .thenThrow(new SsoServiceException(HttpStatus.NOT_FOUND, "SSO yapılandırması bulunamadı"));

        mockMvc.perform(get("/v1/sso/metadata")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SSO yapılandırması bulunamadı"));
    }

    @Test
    void getSpMetadataSuccess() throws Exception {
        when(ssoService.getSpMetadata(anyString())).thenReturn(metadataXml());

        mockMvc.perform(get("/v1/sso/metadata")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/samlmetadata+xml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EntityDescriptor")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("X509Certificate")));
    }

    // ---------- HandleACS ----------

    @Test
    void handleAcsMissingSamlResponseReturns400() throws Exception {
        when(ssoService.handleAcs(anyString(), any()))
                .thenThrow(new SsoServiceException(HttpStatus.BAD_REQUEST, "SAMLResponse gerekli"));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SAMLResponse gerekli"));
    }

    @Test
    void handleAcsConfigNotEnabledReturns401() throws Exception {
        when(ssoService.handleAcs(anyString(), any()))
                .thenThrow(new SsoServiceException(HttpStatus.UNAUTHORIZED, "SSO etkin değil"));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "dummy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SSO etkin değil"));
    }

    @Test
    void handleAcsInvalidIdpCertReturns401() throws Exception {
        when(ssoService.handleAcs(anyString(), any()))
                .thenThrow(new SsoServiceException(HttpStatus.UNAUTHORIZED,
                        "SAML ServiceProvider oluşturma: IdP sertifikası PEM formatında değil"));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "dummy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("IdP sertifikası")));
    }

    @Test
    void handleAcsInvalidSamlResponseReturns401() throws Exception {
        when(ssoService.handleAcs(anyString(), any()))
                .thenThrow(new SsoServiceException(HttpStatus.UNAUTHORIZED,
                        "SAML yanıtı ayrıştırma: hata"));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "not-valid-base64!!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("SAML yanıtı ayrıştırma")));
    }

    @Test
    void handleAcsSuccess() throws Exception {
        when(ssoService.handleAcs(anyString(), any())).thenReturn(Map.of(
                "user_id", "user-1",
                "email", "jane@corp.com",
                "display_name", "Jane Doe",
                "tenant_id", TENANT,
                "message", "SSO giriş başarılı"));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "dummy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("user-1"))
                .andExpect(jsonPath("$.email").value("jane@corp.com"))
                .andExpect(jsonPath("$.display_name").value("Jane Doe"))
                .andExpect(jsonPath("$.tenant_id").value(TENANT))
                .andExpect(jsonPath("$.message").value("SSO giriş başarılı"));
    }

    @Test
    void handleAcsUserNotFoundReturns401() throws Exception {
        when(ssoService.handleAcs(anyString(), any()))
                .thenThrow(new SsoServiceException(HttpStatus.UNAUTHORIZED, "kullanıcı bulunamadı"));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "dummy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("kullanıcı bulunamadı"));
    }

    // ---------- Enable / Disable ----------

    @Test
    void enableSuccess() throws Exception {
        when(ssoService.enable(anyString())).thenReturn(Map.of("status", "SSO etkinleştirildi"));

        mockMvc.perform(post("/v1/sso/enable")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO etkinleştirildi"));
    }

    @Test
    void disableSuccess() throws Exception {
        when(ssoService.disable(anyString())).thenReturn(Map.of("status", "SSO devre dışı bırakıldı"));

        mockMvc.perform(post("/v1/sso/disable")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO devre dışı bırakıldı"));
    }

    // ---------- GenerateKeyPair ----------

    @Test
    void generateKeyPairSuccess() throws Exception {
        when(ssoService.generateKeyPair()).thenReturn(KeyPairGeneratorUtil.generate());

        mockMvc.perform(post("/v1/sso/generate-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificate").value(org.hamcrest.Matchers.containsString("BEGIN CERTIFICATE")))
                .andExpect(jsonPath("$.private_key").value(org.hamcrest.Matchers.containsString("BEGIN RSA PRIVATE KEY")));
    }

    // ---------- yardımcılar ----------

    private static SsoConfig config() {
        return new SsoConfig(
                "cfg-1", TENANT, "https://idp.example.com", "https://idp.example.com/sso",
                "", "https://geolens.app/saml/" + TENANT, "https://geolens.app/v1/sso/acs/" + TENANT,
                true, "2026-08-15T10:00:00Z", "2026-08-15T10:00:00Z");
    }

    private static String metadataXml() {
        return "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\"https://geolens.app/saml/T01\">"
                + "<md:SPSSODescriptor>"
                + "<md:KeyDescriptor use=\"signing\"><ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">"
                + "<ds:X509Data><ds:X509Certificate>abc</ds:X509Certificate></ds:X509Data></ds:KeyInfo></md:KeyDescriptor>"
                + "</md:SPSSODescriptor></md:EntityDescriptor>";
    }
}
