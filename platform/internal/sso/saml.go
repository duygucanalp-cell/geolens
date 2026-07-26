package sso

import (
	"bytes"
	"compress/flate"
	"crypto"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/sha256"
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

	// Verify Response signature first (whole response)
	if idpCertPEM != "" {
		if err := verifyResponseSignature(root, idpCertPEM); err != nil {
			return nil, fmt.Errorf("SAML yanıt imzası doğrulama: %w", err)
		}
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

// verifyResponseSignature verifies the XML Signature on the SAML Response element.
// RSA-SHA256 ile imza doğrulaması yapar.
func verifyResponseSignature(rootEl *etree.Element, idpCertPEM string) error {
	block, _ := pem.Decode([]byte(idpCertPEM))
	if block == nil {
		return fmt.Errorf("IdP sertifikası PEM formatında değil")
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return fmt.Errorf("IdP sertifikası ayrıştırma: %w", err)
	}

	dsigNS := "http://www.w3.org/2000/09/xmldsig#"
	sigEl := findChildNS(rootEl, dsigNS, "Signature")
	if sigEl == nil {
		sigEl = rootEl.FindElement("Signature")
	}
	if sigEl == nil {
		return fmt.Errorf("SAML yanıtında Signature bulunamadı")
	}

	// Extract SignedInfo canonical XML
	signedInfo := findChildNS(sigEl, dsigNS, "SignedInfo")
	if signedInfo == nil {
		signedInfo = sigEl.FindElement("SignedInfo")
	}
	if signedInfo == nil {
		return fmt.Errorf("SignedInfo bulunamadı")
	}

	// Serialize SignedInfo as canonical XML
	siDoc := etree.NewDocument()
	siDoc.SetRoot(signedInfo.Copy())
	siXML, err := siDoc.WriteToBytes()
	if err != nil {
		return fmt.Errorf("SignedInfo serileştirme: %w", err)
	}

	// Extract signature value (base64)
	sigValueB64 := findTextNS(sigEl, dsigNS, "SignatureValue")
	if sigValueB64 == "" {
		return fmt.Errorf("SignatureValue bulunamadı")
	}

	sigBytes, err := base64.StdEncoding.DecodeString(sigValueB64)
	if err != nil {
		return fmt.Errorf("SignatureValue base64 çözümleme: %w", err)
	}

	// Determine signature method
	sigMethod := findChildNS(signedInfo, dsigNS, "SignatureMethod")
	var hashFunc crypto.Hash
	if sigMethod != nil {
		algo := sigMethod.SelectAttrValue("Algorithm", "")
		switch algo {
		case "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
			"http://www.w3.org/2001/04/xmldsig-more#rsa-sha384",
			"http://www.w3.org/2001/04/xmldsig-more#rsa-sha512":
			hashFunc = crypto.SHA256
			siHash := sha256.Sum256(siXML)
			sigBytes = siHash[:] // PKCS1v15Verify hashes internally, but we need to hash first for RSA verification
			if err := rsa.VerifyPKCS1v15(cert.PublicKey.(*rsa.PublicKey), crypto.SHA256, siHash[:], sigBytes); err != nil {
				return fmt.Errorf("RSA-SHA256 imza doğrulama başarısız: %w", err)
			}
			return nil
		case "http://www.w3.org/2000/09/xmldsig#rsa-sha1":
			hashFunc = crypto.SHA1
			siHash := sha1.Sum(siXML)
			if err := rsa.VerifyPKCS1v15(cert.PublicKey.(*rsa.PublicKey), crypto.SHA1, siHash[:], sigBytes); err != nil {
				return fmt.Errorf("RSA-SHA1 imza doğrulama başarısız: %w", err)
			}
			return nil
		default:
			// Varsayılan: SHA-256 dene, sonra SHA-1
			hashFunc = crypto.SHA256
		}
	} else {
		hashFunc = crypto.SHA256
	}

	// Try SHA-256 first, then SHA-1
	siHash256 := sha256.Sum256(siXML)
	if err := rsa.VerifyPKCS1v15(cert.PublicKey.(*rsa.PublicKey), crypto.SHA256, siHash256[:], sigBytes); err != nil {
		// Fallback to SHA-1
		siHash1 := sha1.Sum(siXML)
		if err2 := rsa.VerifyPKCS1v15(cert.PublicKey.(*rsa.PublicKey), crypto.SHA1, siHash1[:], sigBytes); err2 != nil {
			return fmt.Errorf("imza doğrulama başarısız (SHA-256: %v, SHA-1: %v)", err, err2)
		}
		hashFunc = crypto.SHA1
	}

	_ = hashFunc
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
