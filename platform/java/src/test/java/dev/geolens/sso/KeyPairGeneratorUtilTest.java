package dev.geolens.sso;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RSA 2048 + X.509 self-signed sertifika üretim testleri. */
class KeyPairGeneratorUtilTest {

    @Test
    void generatesPemCertificateAndKey() {
        KeyPairGeneratorUtil.GeneratedKeys keys = KeyPairGeneratorUtil.generate();
        assertNotNull(keys.certificatePem());
        assertNotNull(keys.privateKeyPem());
        assertTrue(keys.certificatePem().startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(keys.certificatePem().contains("-----END CERTIFICATE-----"));
        assertTrue(keys.privateKeyPem().startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        assertTrue(keys.privateKeyPem().contains("-----END RSA PRIVATE KEY-----"));
    }

    @Test
    void certificateParsesAsX509() {
        KeyPairGeneratorUtil.GeneratedKeys keys = KeyPairGeneratorUtil.generate();
        var cert = SamlSupport.parseIdpCert(keys.certificatePem());
        assertNotNull(cert);
        assertFalse(cert.getNotAfter().before(new java.util.Date()));
    }
}
