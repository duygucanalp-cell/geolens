package dev.geolens.sso;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SAML yanıtı ayrıştırma ve assertion'dan email/name çıkarma testleri. */
class SamlSupportTest {

    @Test
    void parseResponseDecodesBase64Xml() {
        String xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Assertion>
                    <saml:Subject><saml:NameID>user@example.com</saml:NameID></saml:Subject>
                  </saml:Assertion>
                </samlp:Response>
                """;
        String b64 = Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Document doc = SamlSupport.parseResponse(b64);
        assertTrue(doc != null);
    }

    @Test
    void parseResponseInvalidBase64Throws() {
        assertThrows(IllegalArgumentException.class, () -> SamlSupport.parseResponse("not-base64!!!"));
    }

    @Test
    void extractEmailFromFriendlyName() {
        Document doc = SamlSupport.parseResponse(b64(samlWithAttribute("email", "jane@corp.com", "jane@corp.com")));
        assertEquals("jane@corp.com", SamlSupport.extractEmail(doc));
    }

    @Test
    void extractEmailFromNameUri() {
        Document doc = SamlSupport.parseResponse(b64(samlWithAttribute(
                "givenName", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", "jane@corp.com")));
        assertEquals("jane@corp.com", SamlSupport.extractEmail(doc));
    }

    @Test
    void extractEmailFallsBackToNameId() {
        Document doc = SamlSupport.parseResponse(b64(samlWithNameId("fallback@corp.com")));
        assertEquals("fallback@corp.com", SamlSupport.extractEmail(doc));
    }

    @Test
    void extractEmailEmptyWhenNoAttributes() {
        Document doc = SamlSupport.parseResponse(b64("""
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Assertion><saml:Subject><saml:NameID/></saml:Subject></saml:Assertion>
                </samlp:Response>
                """));
        assertEquals("", SamlSupport.extractEmail(doc));
    }

    @Test
    void extractNameFromDisplayName() {
        Document doc = SamlSupport.parseResponse(b64(samlWithAttribute("displayName", "displayName", "Jane Doe")));
        assertEquals("Jane Doe", SamlSupport.extractName(doc));
    }

    @Test
    void extractNameEmptyWhenAbsent() {
        Document doc = SamlSupport.parseResponse(b64(samlWithAttribute("email", "jane@corp.com", "jane@corp.com")));
        assertEquals("", SamlSupport.extractName(doc));
    }

    @Test
    void parseIdpCertAcceptsPem() {
        // Kendi ürettiğimiz sertifikayı PEM olarak doğrula (round-trip)
        KeyPairGeneratorUtil.GeneratedKeys keys = KeyPairGeneratorUtil.generate();
        assertTrue(SamlSupport.parseIdpCert(keys.certificatePem()) != null);
    }

    @Test
    void parseIdpCertRejectsNonPem() {
        assertThrows(IllegalArgumentException.class, () -> SamlSupport.parseIdpCert("not a pem"));
    }

    private static String b64(String xml) {
        return Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String samlWithAttribute(String friendlyName, String name, String value) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Assertion>
                    <saml:AttributeStatement>
                      <saml:Attribute FriendlyName="%s" Name="%s">
                        <saml:AttributeValue>%s</saml:AttributeValue>
                      </saml:Attribute>
                    </saml:AttributeStatement>
                  </saml:Assertion>
                </samlp:Response>
                """.formatted(friendlyName, name, value);
    }

    private static String samlWithNameId(String nameId) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Assertion>
                    <saml:Subject><saml:NameID>%s</saml:NameID></saml:Subject>
                  </saml:Assertion>
                </samlp:Response>
                """.formatted(nameId);
    }
}
