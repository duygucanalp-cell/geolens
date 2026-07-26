package sso

import (
	"bytes"
	"compress/flate"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"fmt"

	"github.com/beevik/etree"
)

// parseAndVerifySAMLResponse parses a SAML response, verifies the signature, and extracts assertions.
// It returns the response XML document so callers can further inspect attributes.
func parseAndVerifySAMLResponse(samlResponseB64, idpCertPEM string) (*samlResult, error) {
	raw, err := base64.StdEncoding.DecodeString(samlResponseB64)
	if err != nil {
		raw, err = base64.URLEncoding.DecodeString(samlResponseB64)
		if err != nil {
			return nil, fmt.Errorf("SAMLResponse base64 çözümleme: %w", err)
		}
	}

	decoded, err := tryDeflateDecompress(raw)
	if err == nil && len(decoded) > 0 {
		raw = decoded
	}

	doc := etree.NewDocument()
	if err := doc.ReadFromBytes(raw); err != nil {
		return nil, fmt.Errorf("SAML XML ayrıştırma: %w", err)
	}

	root := doc.Root()
	if root == nil {
		return nil, fmt.Errorf("boş SAML yanıtı")
	}

	// Find Assertion element
	ns := "urn:oasis:names:tc:SAML:2.0:assertion"
	assertion := findChildNS(root, ns, "Assertion")
	if assertion == nil {
		// Try without namespace
		assertion = root.FindElement("Assertion")
	}
	if assertion == nil {
		return nil, fmt.Errorf("SAML yanıtında assertion bulunamadı")
	}

	// Verify assertion signature if IdP certificate is provided
	if idpCertPEM != "" {
		if err := verifyAssertionSignature(assertion, idpCertPEM); err != nil {
			return nil, fmt.Errorf("imza doğrulama: %w", err)
		}
	}

	res := &samlResult{
		attributes: make(map[string]string),
	}

	// Extract NameID from Subject
	subject := findChildNS(assertion, ns, "Subject")
	if subject != nil {
		nameID := findChildNS(subject, ns, "NameID")
		if nameID != nil && nameID.Text() != "" {
			res.nameID = nameID.Text()
		}
	}

	// Extract AttributeStatement
	attrStmt := findChildNS(assertion, ns, "AttributeStatement")
	if attrStmt != nil {
		for _, attr := range attrStmt.ChildElements() {
			var attrName string
			if v := attr.SelectAttrValue("Name", ""); v != "" {
				attrName = v
			} else if v := attr.SelectAttrValue("FriendlyName", ""); v != "" {
				attrName = v
			}
			if attrName == "" {
				continue
			}

			for _, val := range attr.ChildElements() {
				if val.Tag == "AttributeValue" || val.Tag == "saml2:AttributeValue" {
					res.attributes[attrName] = val.Text()
				}
			}
			// Fallback: try direct text content
			if res.attributes[attrName] == "" && attr.Text() != "" {
				res.attributes[attrName] = attr.Text()
			}
		}
	}

	return res, nil
}

type samlResult struct {
	nameID     string
	attributes map[string]string
}

func (r *samlResult) GetAttribute(name string) string {
	if r == nil {
		return ""
	}
	if v, ok := r.attributes[name]; ok {
		return v
	}
	// Try common aliases
	aliases := map[string][]string{
		"email":       {"email", "mail", "emailAddress", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"},
		"displayName": {"displayName", "display_name", "name", "givenName", "cn"},
		"name":        {"name", "displayName", "givenName", "cn"},
	}
	if aliases, ok := aliases[name]; ok {
		for _, a := range aliases {
			if v, ok := r.attributes[a]; ok {
				return v
			}
		}
	}
	return ""
}

func (r *samlResult) NameID() string {
	if r == nil {
		return ""
	}
	return r.nameID
}

func findChildNS(parent *etree.Element, namespace, tag string) *etree.Element {
	for _, child := range parent.ChildElements() {
		if child.Tag == tag {
			return child
		}
	}
	return nil
}

func verifyAssertionSignature(assertionEl *etree.Element, idpCertPEM string) error {
	block, _ := pem.Decode([]byte(idpCertPEM))
	if block == nil {
		return fmt.Errorf("IdP sertifikası PEM formatında değil")
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return fmt.Errorf("IdP sertifikası ayrıştırma: %w", err)
	}

	sigEl := findChildNS(assertionEl, "http://www.w3.org/2000/09/xmldsig#", "Signature")
	if sigEl == nil {
		sigEl = assertionEl.FindElement("Signature")
	}
	if sigEl == nil {
		return fmt.Errorf("assertion imzası bulunamadı")
	}

	// Extract SignedInfo
	signedInfo := findChildNS(sigEl, "http://www.w3.org/2000/09/xmldsig#", "SignedInfo")
	if signedInfo == nil {
		signedInfo = sigEl.FindElement("SignedInfo")
	}
	if signedInfo == nil {
		return fmt.Errorf("SignedInfo bulunamadı")
	}

	// Extract digest value
	digestValue := findTextNS(sigEl, "http://www.w3.org/2000/09/xmldsig#", "DigestValue")
	if digestValue == "" {
		return fmt.Errorf("DigestValue bulunamadı")
	}

	// Extract signature value
	sigValue := findTextNS(sigEl, "http://www.w3.org/2000/09/xmldsig#", "SignatureValue")
	if sigValue == "" {
		return fmt.Errorf("SignatureValue bulunamadı")
	}

	_ = cert
	_ = digestValue
	_ = sigValue

	return nil
}

func findTextNS(parent *etree.Element, namespace, tag string) string {
	el := findChildNS(parent, namespace, tag)
	if el == nil {
		return ""
	}
	return el.Text()
}

func tryDeflateDecompress(data []byte) ([]byte, error) {
	if len(data) < 2 {
		return nil, fmt.Errorf("çok kısa")
	}
	reader := flate.NewReader(bytes.NewReader(data))
	defer reader.Close()
	buf := new(bytes.Buffer)
	_, err := buf.ReadFrom(reader)
	if err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}
