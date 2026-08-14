package dev.geolens.sso;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * SAML yardımcıları — Go {@code saml.go} portu (crewjam/saml davranışı sadeleştirilmiş).
 * <p>SAMLResponse base64 çözülüp XML DOM olarak ayrıştırılır; assertion'dan
 * email ve display name çıkarılır (Go {@code extractEmailFromAssertion}/
 * {@code extractNameFromAssertion} birebir). IdP sertifikası PEM'den doğrulanır.
 */
public final class SamlSupport {

    private SamlSupport() {
    }

    /**
     * IdP sertifikasını PEM'den ayrıştırır — Go {@code buildSPFromConfig} PEM decode karşılığı.
     *
     * @throws IllegalArgumentException PEM formatında değilse
     */
    public static X509Certificate parseIdpCert(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("IdP sertifikası PEM formatında değil");
        }
        try {
            String b64 = pem
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(b64);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("IdP sertifikası PEM formatında değil", e);
        }
    }

    /**
     * SAMLResponse'u ayrıştırır ve assertion XML DOM'unu döndürür.
     *
     * @throws IllegalArgumentException SAML yanıtı çözümlenemezse
     */
    public static Document parseResponse(String samlResponseB64) {
        try {
            byte[] xml = Base64.getDecoder().decode(samlResponseB64);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("SAML yanıtı ayrıştırma: " + e.getMessage(), e);
        }
    }

    /**
     * Go {@code extractEmailFromAssertion} karşılığı: yaygın attribute adlarından
     * email bulur, bulamazsa NameID'e düşer.
     */
    public static String extractEmail(Document doc) {
        if (doc == null) {
            return "";
        }

        NodeList attributes = doc.getElementsByTagNameNS("*", "Attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            String friendly = attr.getAttribute("FriendlyName");
            String name = attr.getAttribute("Name");
            if ("email".equals(friendly) || "mail".equals(friendly) || "emailAddress".equals(friendly)
                    || "email".equals(name) || "mail".equals(name) || "emailAddress".equals(name)
                    || "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress".equals(name)
                    || "urn:oid:0.9.2342.19200300.100.1.3".equals(name)) {
                String value = firstAttributeValue(attr);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        // Subject NameID fallback
        NodeList nameIds = doc.getElementsByTagNameNS("*", "NameID");
        if (nameIds.getLength() > 0) {
            String v = nameIds.item(0).getTextContent();
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }

        return "";
    }

    /**
     * Go {@code extractNameFromAssertion} karşılığı: display name attribute'larından
     * görünen adı çıkarır.
     */
    public static String extractName(Document doc) {
        if (doc == null) {
            return "";
        }

        NodeList attributes = doc.getElementsByTagNameNS("*", "Attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attr = (Element) attributes.item(i);
            String friendly = attr.getAttribute("FriendlyName");
            String name = attr.getAttribute("Name");
            if ("displayName".equals(friendly) || "name".equals(friendly) || "givenName".equals(friendly) || "cn".equals(friendly)
                    || "displayName".equals(name) || "name".equals(name) || "givenName".equals(name) || "cn".equals(name)
                    || "urn:oid:2.5.4.42".equals(name) || "urn:oid:2.5.4.3".equals(name)
                    || "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name".equals(name)) {
                String value = firstAttributeValue(attr);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        return "";
    }

    private static String firstAttributeValue(Element attr) {
        NodeList values = attr.getElementsByTagNameNS("*", "AttributeValue");
        if (values.getLength() > 0) {
            return values.item(0).getTextContent().trim();
        }
        return null;
    }
}
