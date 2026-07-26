package sso

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"fmt"
	"net/http"
	"net/url"

	"github.com/crewjam/saml"
)

// parseAndVerifySAMLResponse parses a SAML Response using crewjam/saml's ServiceProvider
// and returns the extracted assertion.
func parseAndVerifySAMLResponse(r *http.Request, cfg *SSOConfig, spKey *rsa.PrivateKey, spCert *x509.Certificate) (*saml.Assertion, error) {
	sp, err := buildSPFromConfig(cfg, spKey, spCert)
	if err != nil {
		return nil, fmt.Errorf("SAML ServiceProvider oluşturma: %w", err)
	}

	assertion, err := sp.ParseResponse(r, nil)
	if err != nil {
		return nil, fmt.Errorf("SAML yanıtı ayrıştırma: %w", err)
	}

	return assertion, nil
}

// buildSPFromConfig creates a crewjam/saml ServiceProvider from SSO configuration.
func buildSPFromConfig(cfg *SSOConfig, spKey *rsa.PrivateKey, spCert *x509.Certificate) (*saml.ServiceProvider, error) {
	// Parse IdP certificate from PEM
	block, _ := pem.Decode([]byte(cfg.IdpCert))
	if block == nil {
		return nil, fmt.Errorf("IdP sertifikası PEM formatında değil")
	}

	// Build minimal IdP metadata EntityDescriptor
	idpMetadata := &saml.EntityDescriptor{
		EntityID: cfg.IdpEntityID,
		IDPSSODescriptors: []saml.IDPSSODescriptor{
			{
				SSODescriptor: saml.SSODescriptor{
					RoleDescriptor: saml.RoleDescriptor{
						KeyDescriptors: []saml.KeyDescriptor{
							{
								Use: "signing",
								KeyInfo: saml.KeyInfo{
									X509Data: saml.X509Data{
										X509Certificates: []saml.X509Certificate{
											{Data: base64.StdEncoding.EncodeToString(block.Bytes)},
										},
									},
								},
							},
						},
					},
				},
				SingleSignOnServices: []saml.Endpoint{
					{
						Binding:  saml.HTTPPostBinding,
						Location: cfg.IdpSSOURL,
					},
				},
			},
		},
	}

	spEntityID := cfg.SpEntityID
	if spEntityID == "" {
		spEntityID = "https://geolens.app/saml/" + cfg.TenantID
	}
	acsURLStr := cfg.SpACSURL
	if acsURLStr == "" {
		acsURLStr = "https://geolens.app/v1/sso/acs/" + cfg.TenantID
	}
	parsedACS, err := url.Parse(acsURLStr)
	if err != nil {
		return nil, fmt.Errorf("ACS URL ayrıştırma: %w", err)
	}

	sp := &saml.ServiceProvider{
		EntityID:          spEntityID,
		AcsURL:            *parsedACS,
		IDPMetadata:       idpMetadata,
		AllowIDPInitiated: true,
		Key:               spKey,
		Certificate:       spCert,
	}

	return sp, nil
}

// extractEmailFromAssertion extracts the email attribute from a SAML assertion.
// It checks common attribute names and falls back to NameID.
func extractEmailFromAssertion(assertion *saml.Assertion) string {
	if assertion == nil {
		return ""
	}

	for _, as := range assertion.AttributeStatements {
		for _, attr := range as.Attributes {
			switch attr.FriendlyName {
			case "email", "mail", "emailAddress":
				if len(attr.Values) > 0 {
					return attr.Values[0].Value
				}
			}
			switch attr.Name {
			case "email", "mail", "emailAddress",
				"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
				"urn:oid:0.9.2342.19200300.100.1.3":
				if len(attr.Values) > 0 {
					return attr.Values[0].Value
				}
			}
		}
	}

	if assertion.Subject != nil && assertion.Subject.NameID != nil {
		return assertion.Subject.NameID.Value
	}

	return ""
}

// extractNameFromAssertion extracts the display name from a SAML assertion.
func extractNameFromAssertion(assertion *saml.Assertion) string {
	if assertion == nil {
		return ""
	}

	for _, as := range assertion.AttributeStatements {
		for _, attr := range as.Attributes {
			switch attr.FriendlyName {
			case "displayName", "name", "givenName", "cn":
				if len(attr.Values) > 0 {
					return attr.Values[0].Value
				}
			}
			switch attr.Name {
			case "displayName", "name", "givenName", "cn",
				"urn:oid:2.5.4.42",
				"urn:oid:2.5.4.3",
				"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name":
				if len(attr.Values) > 0 {
					return attr.Values[0].Value
				}
			}
		}
	}

	return ""
}
