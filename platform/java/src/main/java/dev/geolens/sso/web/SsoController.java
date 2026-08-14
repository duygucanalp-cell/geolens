package dev.geolens.sso.web;

import dev.geolens.sso.KeyPairGeneratorUtil;
import dev.geolens.sso.SamlSupport;
import dev.geolens.sso.SsoConfig;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSO/SAML REST controller'ı — Go {@code sso.handler} portu (K1).
 * <p>Route'lar (go cmd/api): GET/PUT /v1/sso/config, GET /v1/sso/metadata,
 * POST /v1/sso/enable, POST /v1/sso/disable, POST /v1/sso/generate-keys,
 * POST /sso/acs/{tenantId} (JWT dışı — path tenant).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir (ACS dışında); SAML yanıtı
 * base64 → XML DOM olarak ayrıştırılıp assertion'dan email/name çıkarılır.
 */
@RestController
@RequestMapping("/v1/sso")
public class SsoController {

    private final DSLContext dsl;

    public SsoController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- GetConfig ----------

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(@RequestHeader("X-Tenant-ID") String tenantId) {
        SsoConfig cfg = loadConfig(tenantId, false);
        if (cfg == null) {
            return error(HttpStatus.NOT_FOUND, "SSO yapılandırması bulunamadı");
        }
        return ResponseEntity.ok(cfg);
    }

    // ---------- UpdateConfig ----------

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody UpdateConfigRequest req) {
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
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SSO yapılandırılamadı");
        }
        if (rec == null) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SSO yapılandırılamadı");
        }
        return ResponseEntity.ok(toConfig(rec.intoMap()));
    }

    // ---------- GetSPMetadata ----------

    @GetMapping("/metadata")
    public ResponseEntity<?> getSpMetadata(@RequestHeader("X-Tenant-ID") String tenantId) {
        SsoConfig cfg = loadConfig(tenantId, false);
        if (cfg == null) {
            return error(HttpStatus.NOT_FOUND, "SSO yapılandırması bulunamadı");
        }

        KeyPairGeneratorUtil.GeneratedKeys keys;
        try {
            keys = KeyPairGeneratorUtil.generate();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "metadata oluşturulamadı");
        }

        String spEntityId = cfg.spEntityId().isBlank() ? "https://geolens.app/saml/" + tenantId : cfg.spEntityId();
        String acsUrl = cfg.spAcsUrl().isBlank() ? "https://geolens.app/v1/sso/acs/" + tenantId : cfg.spAcsUrl();

        // crewjam/saml Metadata() çıktısının minimal karşılığı — SP EntityDescriptor
        String certB64 = keys.certificatePem()
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        String xml = """
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

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(new MediaType("application", "samlmetadata+xml"))
                .body(xml);
    }

    // ---------- HandleACS ----------

    @PostMapping("/acs/{tenantId}")
    public ResponseEntity<?> handleAcs(@PathVariable String tenantId,
                                       @RequestParam(value = "SAMLResponse", required = false) String samlResponse) {
        if (samlResponse == null || samlResponse.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "SAMLResponse gerekli");
        }

        SsoConfig cfg = loadConfig(tenantId, true);
        if (cfg == null) {
            return error(HttpStatus.UNAUTHORIZED, "SSO etkin değil");
        }

        // IdP sertifikası PEM doğrulaması (Go buildSPFromConfig karşılığı)
        try {
            SamlSupport.parseIdpCert(cfg.idpCert());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.UNAUTHORIZED, "SAML ServiceProvider oluşturma: " + e.getMessage());
        }

        // SAML yanıtını ayrıştır ve doğrula
        Document assertion;
        try {
            assertion = SamlSupport.parseResponse(samlResponse);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.UNAUTHORIZED, "SAML yanıtı ayrıştırma: " + e.getMessage());
        }

        String email = SamlSupport.extractEmail(assertion);
        String name = SamlSupport.extractName(assertion);
        if (email.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "SAML yanıtında email bulunamadı");
        }

        Record userRec;
        try {
            userRec = dsl.fetchOne("""
                    SELECT id, COALESCE(display_name, email) FROM identity.users WHERE email = ?
                    """, email);
        } catch (RuntimeException e) {
            return error(HttpStatus.UNAUTHORIZED, "kullanıcı bulunamadı");
        }
        if (userRec == null) {
            return error(HttpStatus.UNAUTHORIZED, "kullanıcı bulunamadı");
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
        return ResponseEntity.ok(resp);
    }

    // ---------- Enable ----------

    @PostMapping("/enable")
    public ResponseEntity<?> enable(@RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            dsl.execute("UPDATE sso.configs SET enabled = true, updated_at = now() WHERE tenant_id = ?", tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SSO etkinleştirilemedi");
        }
        return ResponseEntity.ok(Map.of("status", "SSO etkinleştirildi"));
    }

    // ---------- Disable ----------

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            dsl.execute("UPDATE sso.configs SET enabled = false, updated_at = now() WHERE tenant_id = ?", tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SSO devre dışı bırakılamadı");
        }
        return ResponseEntity.ok(Map.of("status", "SSO devre dışı bırakıldı"));
    }

    // ---------- GenerateKeyPair ----------

    @PostMapping("/generate-keys")
    public ResponseEntity<?> generateKeyPair() {
        KeyPairGeneratorUtil.GeneratedKeys keys;
        try {
            keys = KeyPairGeneratorUtil.generate();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "anahtar oluşturulamadı");
        }
        return ResponseEntity.ok(Map.of("certificate", keys.certificatePem(), "private_key", keys.privateKeyPem()));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
