package dev.geolens.sso.service;

import dev.geolens.common.ServiceException;

import dev.geolens.sso.KeyPairGeneratorUtil;
import dev.geolens.sso.SamlSupport;
import dev.geolens.sso.SsoConfig;
import dev.geolens.sso.web.UpdateConfigRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSO/SAML iş mantığı — Go {@code sso.handler} portu (K1).
 * <p>SSO yapılandırması okuma/güncelleme, SP metadata üretimi, ACS işleme ve
 * anahtar üretimini yapar. Controller yalnızca HTTP katmanıdır; bu sınıf DB
 * (DSLContext) ve SAML/SSO motor çağrılarını içerir.
 */
@Service
public class SsoService {

    private final DSLContext dsl;

    public SsoService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code GetConfig} karşılığı — yapılandırmayı getirir, yoksa 404 fırlatır. */
    public SsoConfig getConfig(String tenantId) {
        SsoConfig cfg = loadConfig(tenantId, false);
        if (cfg == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "SSO yapılandırması bulunamadı");
        }
        return cfg;
    }

    /** Go {@code UpdateConfig} karşılığı — upsert yapar, güncel kaydı döner. */
    public SsoConfig updateConfig(String tenantId, UpdateConfigRequest req) {
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    INSERT INTO sso.configs (tenant_id, idp_entity_id, idp_sso_url, idp_cert, sp_entity_id, sp_acs_url, enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id) DO UPDATE SET
                        idp_entity_id = EXCLUDED.idp_entity_id,
                        idp_sso_url = EXCLUDED.idp_sso_url,
                        idp_cert = EXCLUDED.idp_cert,
                        sp_entity_id = EXCLUDED.sp_entity_id,
                        sp_acs_url = EXCLUDED.sp_acs_url,
                        enabled = EXCLUDED.enabled,
                        updated_at = now()
                    RETURNING id, tenant_id, idp_entity_id, idp_sso_url, idp_cert,
                        sp_entity_id, sp_acs_url, enabled, created_at, updated_at
                    """, tenantId, nz(req.idpEntityId()), nz(req.idpSsoUrl()), nz(req.idpCert()),
                    nz(req.spEntityId()), nz(req.spAcsUrl()), req.enabled());
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "SSO yapılandırılamadı");
        }
        if (rec == null) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "SSO yapılandırılamadı");
        }
        return toConfig(rec.intoMap());
    }

    /** Go {@code GetSPMetadata} karşılığı — SP EntityDescriptor XML'ini üretir. */
    public String getSpMetadata(String tenantId) {
        SsoConfig cfg = loadConfig(tenantId, false);
        if (cfg == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "SSO yapılandırması bulunamadı");
        }

        KeyPairGeneratorUtil.GeneratedKeys keys;
        try {
            keys = KeyPairGeneratorUtil.generate();
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "metadata oluşturulamadı");
        }

        String spEntityId = cfg.spEntityId().isBlank() ? "https://geolens.app/saml/" + tenantId : cfg.spEntityId();
        String acsUrl = cfg.spAcsUrl().isBlank() ? "https://geolens.app/v1/sso/acs/" + tenantId : cfg.spAcsUrl();

        // crewjam/saml Metadata() çıktısının minimal karşılığı — SP EntityDescriptor
        String certB64 = keys.certificatePem()
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        return """
                <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="%s">
                  <md:SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
                    <md:KeyDescriptor use="signing">
                      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                        <ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data>
                      </ds:KeyInfo>
                    </md:KeyDescriptor>
                    <md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="%s" index="0" isDefault="true"/>
                  </md:SPSSODescriptor>
                </md:EntityDescriptor>
                """.formatted(spEntityId, certB64, acsUrl);
    }

    /** Go {@code HandleACS} karşılığı — SAML yanıtını doğrular, kullanıcıyı eşler. */
    public Map<String, Object> handleAcs(String tenantId, String samlResponse) {
        if (samlResponse == null || samlResponse.isBlank()) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "SAMLResponse gerekli");
        }

        SsoConfig cfg = loadConfig(tenantId, true);
        if (cfg == null) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "SSO etkin değil");
        }

        // IdP sertifikası PEM doğrulaması (Go buildSPFromConfig karşılığı)
        try {
            SamlSupport.parseIdpCert(cfg.idpCert());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "SAML ServiceProvider oluşturma: " + e.getMessage());
        }

        // SAML yanıtını ayrıştır ve doğrula
        Document assertion;
        try {
            assertion = SamlSupport.parseResponse(samlResponse);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "SAML yanıtı ayrıştırma: " + e.getMessage());
        }

        String email = SamlSupport.extractEmail(assertion);
        String name = SamlSupport.extractName(assertion);
        if (email.isEmpty()) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "SAML yanıtında email bulunamadı");
        }

        Record userRec;
        try {
            userRec = dsl.fetchOne("""
                    SELECT id, COALESCE(display_name, email) FROM identity.users WHERE email = ?
                    """, email);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "kullanıcı bulunamadı");
        }
        if (userRec == null) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "kullanıcı bulunamadı");
        }
        String userId = str(userRec.get(0));
        String displayName = str(userRec.get(1));
        if (!name.isEmpty()) {
            displayName = name;
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("user_id", userId);
        resp.put("email", email);
        resp.put("display_name", displayName);
        resp.put("tenant_id", tenantId);
        resp.put("message", "SSO giriş başarılı");
        return resp;
    }

    /** Go {@code Enable} karşılığı — SSO'yu etkinleştirir. */
    public Map<String, Object> enable(String tenantId) {
        try {
            dsl.execute("UPDATE sso.configs SET enabled = true, updated_at = now() WHERE tenant_id = ?", tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "SSO etkinleştirilemedi");
        }
        return Map.of("status", "SSO etkinleştirildi");
    }

    /** Go {@code Disable} karşılığı — SSO'yu devre dışı bırakır. */
    public Map<String, Object> disable(String tenantId) {
        try {
            dsl.execute("UPDATE sso.configs SET enabled = false, updated_at = now() WHERE tenant_id = ?", tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "SSO devre dışı bırakılamadı");
        }
        return Map.of("status", "SSO devre dışı bırakıldı");
    }

    /** Go {@code GenerateKeyPair} karşılığı — RSA anahtar çifti üretir. */
    public KeyPairGeneratorUtil.GeneratedKeys generateKeyPair() {
        try {
            return KeyPairGeneratorUtil.generate();
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "anahtar oluşturulamadı");
        }
    }

    // ---------- yardımcılar ----------

    /** Go {@code loadConfig} karşılığı — {@code enabledOnly} ise yalnızca etkin kayıt. */
    private SsoConfig loadConfig(String tenantId, boolean enabledOnly) {
        Record rec;
        try {
            if (enabledOnly) {
                rec = dsl.fetchOne("""
                        SELECT id, tenant_id, idp_entity_id, idp_sso_url, idp_cert,
                            sp_entity_id, sp_acs_url, enabled, created_at, updated_at
                        FROM sso.configs WHERE tenant_id = ? AND enabled = true
                        """, tenantId);
            } else {
                rec = dsl.fetchOne("""
                        SELECT id, tenant_id, idp_entity_id, idp_sso_url, idp_cert,
                            sp_entity_id, sp_acs_url, enabled, created_at, updated_at
                        FROM sso.configs WHERE tenant_id = ?
                        """, tenantId);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return rec == null ? null : toConfig(rec.intoMap());
    }

    private static SsoConfig toConfig(Map<String, Object> r) {
        return new SsoConfig(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("idp_entity_id")),
                str(r.get("idp_sso_url")), str(r.get("idp_cert")), str(r.get("sp_entity_id")),
                str(r.get("sp_acs_url")), r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                str(r.get("created_at")), str(r.get("updated_at")));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }
}
