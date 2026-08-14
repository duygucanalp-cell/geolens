package dev.geolens.sso.web;

import dev.geolens.sso.KeyPairGeneratorUtil;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.LinkedHashMap;
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

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- GetConfig ----------

    @Test
    void getConfigNotFound() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get("/v1/sso/config")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SSO yapılandırması bulunamadı"));
    }

    @Test
    void getConfigSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(configRow()));

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
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(configRow()));

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
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get("/v1/sso/metadata")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SSO yapılandırması bulunamadı"));
    }

    @Test
    void getSpMetadataSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(configRow()));

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
        mockMvc.perform(post("/v1/sso/acs/" + TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SAMLResponse gerekli"));
    }

    @Test
    void handleAcsConfigNotEnabledReturns401() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "dummy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SSO etkin değil"));
    }

    @Test
    void handleAcsInvalidIdpCertReturns401() throws Exception {
        // config bulunur ama idp_cert geçersiz
        Map<String, Object> row = configRow();
        row.put("idp_cert", "not-a-pem");
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "dummy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("IdP sertifikası")));
    }

    @Test
    void handleAcsInvalidSamlResponseReturns401() throws Exception {
        Map<String, Object> row = configRow();
        row.put("idp_cert", KeyPairGeneratorUtil.generate().certificatePem());
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", "not-valid-base64!!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("SAML yanıtı ayrıştırma")));
    }

    @Test
    void handleAcsSuccess() throws Exception {
        Map<String, Object> row = configRow();
        row.put("idp_cert", KeyPairGeneratorUtil.generate().certificatePem());
        // config sorgusu → row; kullanıcı sorgusu → user row (2 farklı fetchOne)
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenAnswer(inv -> {
            // user sorgusu 1 arg (email), config sorgusu 1 arg (tenantId) — SQL içeriğiyle ayırt et
            String sql = inv.getArgument(0);
            if (sql.contains("identity.users")) {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("id", "user-1");
                u.put("display_name", "Jane Doe");
                return JooqTestData.record(u);
            }
            return JooqTestData.record(row);
        });

        String saml = samlResponseWithEmail("jane@corp.com", "Jane Doe");
        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", saml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("user-1"))
                .andExpect(jsonPath("$.email").value("jane@corp.com"))
                .andExpect(jsonPath("$.display_name").value("Jane Doe"))
                .andExpect(jsonPath("$.tenant_id").value(TENANT))
                .andExpect(jsonPath("$.message").value("SSO giriş başarılı"));
    }

    @Test
    void handleAcsUserNotFoundReturns401() throws Exception {
        Map<String, Object> row = configRow();
        row.put("idp_cert", KeyPairGeneratorUtil.generate().certificatePem());
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("identity.users")) {
                return null;
            }
            return JooqTestData.record(row);
        });

        String saml = samlResponseWithEmail("nobody@corp.com", "");
        mockMvc.perform(post("/v1/sso/acs/" + TENANT)
                        .param("SAMLResponse", saml))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("kullanıcı bulunamadı"));
    }

    // ---------- Enable / Disable ----------

    @Test
    void enableSuccess() throws Exception {
        mockMvc.perform(post("/v1/sso/enable")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO etkinleştirildi"));
    }

    @Test
    void disableSuccess() throws Exception {
        mockMvc.perform(post("/v1/sso/disable")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO devre dışı bırakıldı"));
    }

    // ---------- GenerateKeyPair ----------

    @Test
    void generateKeyPairSuccess() throws Exception {
        mockMvc.perform(post("/v1/sso/generate-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificate").value(org.hamcrest.Matchers.containsString("BEGIN CERTIFICATE")))
                .andExpect(jsonPath("$.private_key").value(org.hamcrest.Matchers.containsString("BEGIN RSA PRIVATE KEY")));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> configRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "cfg-1");
        m.put("tenant_id", TENANT);
        m.put("idp_entity_id", "https://idp.example.com");
        m.put("idp_sso_url", "https://idp.example.com/sso");
        m.put("idp_cert", "");
        m.put("sp_entity_id", "https://geolens.app/saml/" + TENANT);
        m.put("sp_acs_url", "https://geolens.app/v1/sso/acs/" + TENANT);
        m.put("enabled", true);
        m.put("created_at", "2026-08-15T10:00:00Z");
        m.put("updated_at", "2026-08-15T10:00:00Z");
        return m;
    }

    private static String samlResponseWithEmail(String email, String name) {
        String attrs = name == null || name.isEmpty() ? "" :
                "<saml:Attribute FriendlyName=\"displayName\" Name=\"displayName\"><saml:AttributeValue>"
                        + name + "</saml:AttributeValue></saml:Attribute>";
        String xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Assertion>
                    <saml:AttributeStatement>
                      <saml:Attribute FriendlyName="email" Name="email">
                        <saml:AttributeValue>%s</saml:AttributeValue>
                      </saml:Attribute>
                      %s
                    </saml:AttributeStatement>
                  </saml:Assertion>
                </samlp:Response>
                """.formatted(email, attrs);
        return Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
