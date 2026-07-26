package sso

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"log/slog"
	"math/big"
	"net/http"
	"time"

	"github.com/beevik/etree"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
	"github.com/go-chi/chi/v5"
)

type Handler struct {
	pool *db.Pool
}

func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

type SSOConfig struct {
	ID          string `json:"id"`
	TenantID    string `json:"tenant_id"`
	IdpEntityID string `json:"idp_entity_id"`
	IdpSSOURL   string `json:"idp_sso_url"`
	IdpCert     string `json:"idp_cert,omitempty"`
	SpEntityID  string `json:"sp_entity_id"`
	SpACSURL    string `json:"sp_acs_url"`
	Enabled     bool   `json:"enabled"`
	CreatedAt   string `json:"created_at"`
	UpdatedAt   string `json:"updated_at"`
}

func (h *Handler) GetConfig(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var cfg SSOConfig
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, tenant_id, idp_entity_id, idp_sso_url, idp_cert,
			sp_entity_id, sp_acs_url, enabled, created_at, updated_at
		FROM sso.configs WHERE tenant_id = $1
	`, tenantID).Scan(
		&cfg.ID, &cfg.TenantID, &cfg.IdpEntityID, &cfg.IdpSSOURL, &cfg.IdpCert,
		&cfg.SpEntityID, &cfg.SpACSURL, &cfg.Enabled, &cfg.CreatedAt, &cfg.UpdatedAt,
	)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "SSO yapılandırması bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, cfg)
}

func (h *Handler) UpdateConfig(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		IdpEntityID string `json:"idp_entity_id"`
		IdpSSOURL   string `json:"idp_sso_url"`
		IdpCert     string `json:"idp_cert"`
		SpEntityID  string `json:"sp_entity_id"`
		SpACSURL    string `json:"sp_acs_url"`
		Enabled     bool   `json:"enabled"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	var cfg SSOConfig
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO sso.configs (tenant_id, idp_entity_id, idp_sso_url, idp_cert, sp_entity_id, sp_acs_url, enabled)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
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
	`, tenantID, input.IdpEntityID, input.IdpSSOURL, input.IdpCert,
		input.SpEntityID, input.SpACSURL, input.Enabled,
	).Scan(
		&cfg.ID, &cfg.TenantID, &cfg.IdpEntityID, &cfg.IdpSSOURL, &cfg.IdpCert,
		&cfg.SpEntityID, &cfg.SpACSURL, &cfg.Enabled, &cfg.CreatedAt, &cfg.UpdatedAt,
	)
	if err != nil {
		slog.Error("SSO yapılandırma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "SSO yapılandırılamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, cfg)
}

func (h *Handler) GetSPMetadata(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var cfg SSOConfig
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, tenant_id, idp_entity_id, idp_sso_url, idp_cert,
			sp_entity_id, sp_acs_url, enabled, created_at, updated_at
		FROM sso.configs WHERE tenant_id = $1
	`, tenantID).Scan(
		&cfg.ID, &cfg.TenantID, &cfg.IdpEntityID, &cfg.IdpSSOURL, &cfg.IdpCert,
		&cfg.SpEntityID, &cfg.SpACSURL, &cfg.Enabled, &cfg.CreatedAt, &cfg.UpdatedAt,
	)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "SSO yapılandırması bulunamadı"})
		return
	}

	spEntityID := cfg.SpEntityID
	if spEntityID == "" {
		spEntityID = "https://geolens.app/saml/" + tenantID
	}
	acsURL := cfg.SpACSURL
	if acsURL == "" {
		acsURL = "https://geolens.app/v1/sso/acs/" + tenantID
	}

	// Use etree for safe XML building (prevents XML injection)
	doc := etree.NewDocument()
	doc.WriteSettings = etree.WriteSettings{
		CanonicalEndTags: false,
	}
	doc.CreateProcInst("xml", `version="1.0"`)

	entityDesc := doc.CreateElement("md:EntityDescriptor")
	entityDesc.CreateAttr("xmlns:md", "urn:oasis:names:tc:SAML:2.0:metadata")
	entityDesc.CreateAttr("entityID", spEntityID)

	spDescriptor := entityDesc.CreateElement("md:SPSSODescriptor")
	spDescriptor.CreateAttr("AuthnRequestsSigned", "false")
	spDescriptor.CreateAttr("WantAssertionsSigned", "true")
	spDescriptor.CreateAttr("protocolSupportEnumeration", "urn:oasis:names:tc:SAML:2.0:protocol")

	acs := spDescriptor.CreateElement("md:AssertionConsumerService")
	acs.CreateAttr("Binding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST")
	acs.CreateAttr("Location", acsURL)
	acs.CreateAttr("index", "0")

	metadata, err := doc.WriteToBytes()
	if err != nil {
		slog.Error("SAML metadata XML oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "metadata oluşturulamadı"})
		return
	}

	w.Header().Set("Content-Type", "application/samlmetadata+xml")
	w.Write(metadata)
}

func (h *Handler) HandleACS(w http.ResponseWriter, r *http.Request) {
	tenantID := chi.URLParam(r, "tenantId")

	if err := r.ParseForm(); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz SAML yanıtı"})
		return
	}

	samlResp := r.FormValue("SAMLResponse")
	if samlResp == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "SAMLResponse gerekli"})
		return
	}

	var idpCert string
	err := h.pool.QueryRow(r.Context(), `
		SELECT idp_cert FROM sso.configs WHERE tenant_id = $1 AND enabled = true
	`, tenantID).Scan(&idpCert)
	if err != nil {
		httputil.WriteJSON(w, http.StatusUnauthorized, map[string]string{"error": "SSO etkin değil"})
		return
	}

	result, err := parseAndVerifySAMLResponse(samlResp, idpCert)
	if err != nil {
		httputil.WriteJSON(w, http.StatusUnauthorized, map[string]string{"error": err.Error()})
		return
	}

	email := result.GetAttribute("email")
	name := result.GetAttribute("name")
	if name == "" {
		name = result.GetAttribute("displayName")
	}
	if email == "" {
		email = result.NameID()
	}
	if email == "" {
		httputil.WriteJSON(w, http.StatusUnauthorized, map[string]string{"error": "SAML yanıtında email bulunamadı"})
		return
	}

	var userID, displayName string
	err = h.pool.QueryRow(r.Context(), `
		SELECT id, COALESCE(display_name, email)
		FROM identity.users WHERE email = $1
	`, email).Scan(&userID, &displayName)
	if err != nil {
		httputil.WriteJSON(w, http.StatusUnauthorized, map[string]string{"error": "kullanıcı bulunamadı"})
		return
	}

	if name != "" {
		displayName = name
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"user_id":      userID,
		"email":        email,
		"display_name": displayName,
		"tenant_id":    tenantID,
		"message":      "SSO giriş başarılı",
	})
}

func (h *Handler) Enable(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	_, err := h.pool.Exec(r.Context(), `
		UPDATE sso.configs SET enabled = true, updated_at = now() WHERE tenant_id = $1
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "SSO etkinleştirilemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "SSO etkinleştirildi"})
}

func (h *Handler) Disable(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	_, err := h.pool.Exec(r.Context(), `
		UPDATE sso.configs SET enabled = false, updated_at = now() WHERE tenant_id = $1
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "SSO devre dışı bırakılamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "SSO devre dışı bırakıldı"})
}

func (h *Handler) GenerateKeyPair(w http.ResponseWriter, r *http.Request) {
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "anahtar oluşturulamadı"})
		return
	}

	template := x509.Certificate{
		SerialNumber: big.NewInt(time.Now().UnixNano()),
		Subject: pkix.Name{
			Organization: []string{"GeoLens"},
			CommonName:   "SAML Signing Certificate",
		},
		NotBefore:             time.Now(),
		NotAfter:              time.Now().Add(365 * 24 * time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth, x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
	}

	certDER, err := x509.CreateCertificate(rand.Reader, &template, &template, &privateKey.PublicKey, privateKey)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sertifika oluşturulamadı"})
		return
	}

	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: certDER})
	keyBytes := x509.MarshalPKCS1PrivateKey(privateKey)
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: keyBytes})

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"certificate": string(certPEM),
		"private_key": string(keyPEM),
	})
}
