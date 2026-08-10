// Package billing provides handlers and logic for billing functionality.
package billing

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/internal/pdf"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool    *db.Pool
	stripe  *StripeClient
	efatura EFaturaProvider
}

func NewHandler(pool *db.Pool, stripeKey, webhookSecret, efaturaMode string) *Handler {
	return &Handler{
		pool:    pool,
		stripe:  NewStripeClient(stripeKey, webhookSecret),
		efatura: NewEFaturaProvider(efaturaMode),
	}
}

// SetPriceIDs overrides the Stripe tier→price mapping from env (STRIPE_PRICE_IDS).
// PO review §4: hardcoded price ID'leri env üzerinden yapılandırılabilir (HT2 multi-currency).
func (h *Handler) SetPriceIDs(priceIDs map[string]string) {
	if h.stripe != nil && len(priceIDs) > 0 {
		h.stripe.SetPriceIDs(priceIDs)
	}
}

func (h *Handler) CreateCheckoutSession(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		Tier       string `json:"tier"`
		Currency   string `json:"currency"`
		SuccessURL string `json:"success_url"`
		CancelURL  string `json:"cancel_url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.Tier == "" {
		httputil.WriteError(w, http.StatusBadRequest, "tier zorunludur (pro, business, enterprise)")
		return
	}
	validTiers := map[string]bool{"pro": true, "business": true, "enterprise": true}
	if !validTiers[req.Tier] {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz tier (pro, business, enterprise)")
		return
	}
	if req.Currency == "" {
		req.Currency = "usd" // HT2 multi-currency: varsayılan USD
	}

	session, err := h.stripe.CreateCheckout(r.Context(), tenantID, req.Tier, req.Currency, req.SuccessURL, req.CancelURL)
	if err != nil {
		slog.Error("checkout session oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "ödeme oturumu oluşturulamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"session_id": session.ID,
		"url":        session.URL,
	})
}

func (h *Handler) HandleWebhook(w http.ResponseWriter, r *http.Request) {
	if h.stripe.WebhookSecret == "" {
		httputil.WriteError(w, http.StatusNotImplemented, "webhook yapılandırılmamış")
		return
	}

	event, err := h.stripe.ParseWebhook(r)
	if err != nil {
		slog.Error("webhook ayrıştırma hatası", "error", err)
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz webhook")
		return
	}

	if err := h.stripe.HandleEvent(r.Context(), h.pool, event); err != nil {
		slog.Error("webhook işleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "webhook işlenemedi")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *Handler) GetSubscription(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var tier string
	var updatedAt interface{}
	err := h.pool.QueryRow(r.Context(), `
		SELECT tier, updated_at FROM identity.tenants WHERE id = $1
	`, tenantID).Scan(&tier, &updatedAt)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "kiracı bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"tenant_id":  tenantID,
		"tier":       tier,
		"updated_at": updatedAt,
	})
}

// ListInvoices handles GET /v1/billing/invoices
// Stripe webhook'larından toplanan faturaları listeler (FR-A6 otomatik fatura görüntüleme).
func (h *Handler) ListInvoices(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, stripe_invoice_id, COALESCE(number, ''), status, amount_total, currency,
		       period_start, period_end, COALESCE(hosted_invoice_url, ''), COALESCE(invoice_pdf, ''), created_at,
		       subtotal, vat_rate, vat_amount, invoice_type,
		       COALESCE(customer_name, ''), COALESCE(customer_tax_no, ''), COALESCE(customer_identity, ''),
		       COALESCE(customer_address, ''), gib_status, COALESCE(document_id, ''), COALESCE(gib_response_id, '')
		FROM billing.invoices
		WHERE tenant_id = $1
		ORDER BY created_at DESC
	`, tenantID)
	if err != nil {
		slog.Error("fatura listesi alınamadı", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "fatura listesi alınamadı")
		return
	}
	defer rows.Close()

	invoices := make([]Invoice, 0, 16)
	for rows.Next() {
		var inv Invoice
		var periodStart, periodEnd *time.Time
		var created time.Time
		if err := rows.Scan(&inv.ID, &inv.StripeInvoiceID, &inv.Number, &inv.Status,
			&inv.AmountTotal, &inv.Currency, &periodStart, &periodEnd, &inv.HostedURL, &inv.PDFURL, &created,
			&inv.Subtotal, &inv.VATRate, &inv.VATAmount, &inv.InvoiceType,
			&inv.CustomerName, &inv.CustomerTaxNo, &inv.CustomerIdentity, &inv.CustomerAddress,
			&inv.GIBStatus, &inv.DocumentID, &inv.GIBResponseID); err != nil {
			slog.Warn("fatura satırı okunamadı", "error", err)
			continue
		}
		if periodStart != nil {
			ts := periodStart.Format(time.RFC3339)
			inv.PeriodStart = &ts
		}
		if periodEnd != nil {
			ts := periodEnd.Format(time.RFC3339)
			inv.PeriodEnd = &ts
		}
		inv.CreatedAt = created.Format(time.RFC3339)
		invoices = append(invoices, inv)
	}
	if rows.Err() != nil {
		slog.Error("fatura iterasyon hatası", "error", rows.Err())
		httputil.WriteError(w, http.StatusInternalServerError, "fatura listesi alınamadı")
		return
	}
	if invoices == nil {
		invoices = []Invoice{}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"invoices": invoices,
		"count":    len(invoices),
	})
}

// CreatePortalSession handles POST /v1/billing/portal
// Stripe Billing Portal oturumu açar: kredi kartı yönetimi, paket değişikliği, iptal (FR-A6).
func (h *Handler) CreatePortalSession(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		ReturnURL string `json:"return_url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		slog.Warn("portal session isteği çözümlenemedi", "error", err)
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.ReturnURL == "" {
		req.ReturnURL = "/"
	}

	url, err := h.stripe.CreatePortalSession(r.Context(), tenantID, req.ReturnURL)
	if err != nil {
		slog.Error("portal session oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "portal oturumu oluşturulamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"url": url,
	})
}

// loadInvoice, kiracıya ait bir faturayı (vergi/e-Fatura alanları dahil) getirir.
func (h *Handler) loadInvoice(r *http.Request, tenantID, invoiceID string) (Invoice, bool, error) {
	var inv Invoice
	var periodStart, periodEnd *time.Time
	var created time.Time
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, stripe_invoice_id, COALESCE(number, ''), status, amount_total, currency,
		       period_start, period_end, COALESCE(hosted_invoice_url, ''), COALESCE(invoice_pdf, ''), created_at,
		       subtotal, vat_rate, vat_amount, invoice_type,
		       COALESCE(customer_name, ''), COALESCE(customer_tax_no, ''), COALESCE(customer_identity, ''),
		       COALESCE(customer_address, ''), gib_status, COALESCE(document_id, ''), COALESCE(gib_response_id, '')
		FROM billing.invoices
		WHERE id = $1 AND tenant_id = $2
	`, invoiceID, tenantID).Scan(&inv.ID, &inv.StripeInvoiceID, &inv.Number, &inv.Status,
		&inv.AmountTotal, &inv.Currency, &periodStart, &periodEnd, &inv.HostedURL, &inv.PDFURL, &created,
		&inv.Subtotal, &inv.VATRate, &inv.VATAmount, &inv.InvoiceType,
		&inv.CustomerName, &inv.CustomerTaxNo, &inv.CustomerIdentity, &inv.CustomerAddress,
		&inv.GIBStatus, &inv.DocumentID, &inv.GIBResponseID)
	if err != nil {
		return inv, false, err
	}
	if periodStart != nil {
		ts := periodStart.Format(time.RFC3339)
		inv.PeriodStart = &ts
	}
	if periodEnd != nil {
		ts := periodEnd.Format(time.RFC3339)
		inv.PeriodEnd = &ts
	}
	inv.CreatedAt = created.Format(time.RFC3339)
	return inv, true, nil
}

// GetInvoice handles GET /v1/billing/invoices/{invoiceId}
// Tek bir faturanın detayını (vergi/e-Fatura alanları dahil) döndürür.
func (h *Handler) GetInvoice(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	invoiceID := chi.URLParam(r, "invoiceId")

	inv, found, err := h.loadInvoice(r, tenantID, invoiceID)
	if err != nil {
		slog.Error("fatura detayı alınamadı", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "fatura detayı alınamadı")
		return
	}
	if !found {
		httputil.WriteError(w, http.StatusNotFound, "fatura bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, inv)
}

// SubmitEFatura handles POST /v1/billing/invoices/{invoiceId}/efatura
// Faturayı KDV hesaplamasıyla e-Fatura/e-Arşiv olarak GİB'e gönderir (FR-A6 TR özel).
// Gerçek GİB entegrasyonu kimlik bilgisi gerektirdiğinden mock/sandbox modda simüle edilir.
func (h *Handler) SubmitEFatura(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	invoiceID := chi.URLParam(r, "invoiceId")

	var req submitEFaturaRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.InvoiceType != string(InvoiceTypeEFatura) && req.InvoiceType != string(InvoiceTypeEArsiv) {
		httputil.WriteError(w, http.StatusBadRequest, "invoice_type zorunludur (efatura, earsiv)")
		return
	}
	validVAT := false
	for _, rate := range AllowedVATRates {
		if req.VATRate == rate {
			validVAT = true
			break
		}
	}
	if !validVAT {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz KDV oranı (izinli: 0, 1, 10, 20)")
		return
	}
	if req.CustomerName == "" {
		httputil.WriteError(w, http.StatusBadRequest, "customer_name zorunludur")
		return
	}
	if req.InvoiceType == string(InvoiceTypeEFatura) && req.CustomerTaxNo == "" {
		httputil.WriteError(w, http.StatusBadRequest, "e-Fatura için customer_tax_no (VKN) zorunludur")
		return
	}
	if req.InvoiceType == string(InvoiceTypeEArsiv) && req.CustomerTaxNo == "" && req.CustomerIdentity == "" {
		httputil.WriteError(w, http.StatusBadRequest, "e-Arşiv için customer_tax_no veya customer_identity zorunludur")
		return
	}

	inv, found, err := h.loadInvoice(r, tenantID, invoiceID)
	if err != nil {
		slog.Error("fatura yüklenemedi", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "fatura yüklenemedi")
		return
	}
	if !found {
		httputil.WriteError(w, http.StatusNotFound, "fatura bulunamadı")
		return
	}
	if inv.InvoiceType != "standard" && inv.GIBStatus != string(GIBStatusNone) {
		httputil.WriteError(w, http.StatusConflict, "fatura zaten e-Fatura/e-Arşiv olarak gönderilmiş")
		return
	}

	// KDV hesapla: net tutar, faturanın toplam tutarıdır (KDV dahil değil)
	tax, err := CalculateVAT(inv.AmountTotal, req.VATRate)
	if err != nil {
		httputil.WriteError(w, http.StatusBadRequest, err.Error())
		return
	}

	doc := &InvoiceDocument{
		DocumentID:       id.New(),
		Number:           inv.Number,
		InvoiceType:      InvoiceType(req.InvoiceType),
		Currency:         inv.Currency,
		Subtotal:         tax.Subtotal,
		VATRate:          tax.VATRate,
		VATAmount:        tax.VATAmount,
		Total:            tax.Total,
		CustomerName:     req.CustomerName,
		CustomerTaxNo:    req.CustomerTaxNo,
		CustomerIdentity: req.CustomerIdentity,
		CustomerAddress:  req.CustomerAddress,
		IssueDate:        time.Now().UTC(),
	}

	resp, err := h.efatura.Send(r.Context(), doc)
	if err != nil {
		slog.Error("e-fatura gönderim hatası", "error", err)
		httputil.WriteError(w, http.StatusBadGateway, "e-Fatura gönderilemedi")
		return
	}

	_, err = h.pool.Exec(r.Context(), `
		UPDATE billing.invoices SET
			subtotal = $1, vat_rate = $2, vat_amount = $3,
			invoice_type = $4, customer_name = $5, customer_tax_no = $6,
			customer_identity = $7, customer_address = $8,
			gib_status = $9, document_id = $10, gib_response_id = $11, efatura_sent_at = now(),
			updated_at = now()
		WHERE id = $12 AND tenant_id = $13
	`, tax.Subtotal, tax.VATRate, tax.VATAmount, req.InvoiceType,
		req.CustomerName, req.CustomerTaxNo, req.CustomerIdentity, req.CustomerAddress,
		resp.Status, doc.DocumentID, resp.ResponseID, invoiceID, tenantID)
	if err != nil {
		slog.Error("e-fatura durumu kaydedilemedi", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "e-Fatura durumu kaydedilemedi")
		return
	}

	slog.Info("e-fatura gönderildi", "invoice_id", invoiceID, "type", req.InvoiceType, "status", resp.Status)

	inv.InvoiceType = req.InvoiceType
	inv.Subtotal = tax.Subtotal
	inv.VATRate = tax.VATRate
	inv.VATAmount = tax.VATAmount
	inv.CustomerName = req.CustomerName
	inv.CustomerTaxNo = req.CustomerTaxNo
	inv.CustomerIdentity = req.CustomerIdentity
	inv.CustomerAddress = req.CustomerAddress
	inv.GIBStatus = string(resp.Status)
	inv.DocumentID = doc.DocumentID
	inv.GIBResponseID = resp.ResponseID

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"invoice": inv,
		"gib":     resp,
	})
}

// DownloadUBL handles GET /v1/billing/invoices/{invoiceId}/efatura/xml
// e-Fatura/e-Arşiv gönderilen faturanın UBL-TR XML belgesini indirir.
func (h *Handler) DownloadUBL(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	invoiceID := chi.URLParam(r, "invoiceId")

	inv, found, err := h.loadInvoice(r, tenantID, invoiceID)
	if err != nil {
		slog.Error("fatura yüklenemedi", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "fatura yüklenemedi")
		return
	}
	if !found {
		httputil.WriteError(w, http.StatusNotFound, "fatura bulunamadı")
		return
	}
	if inv.InvoiceType == "standard" || inv.DocumentID == "" {
		httputil.WriteError(w, http.StatusNotFound, "faturanın e-Fatura/e-Arşiv belgesi yok")
		return
	}

	doc := &InvoiceDocument{
		DocumentID:       inv.DocumentID,
		Number:           inv.Number,
		InvoiceType:      InvoiceType(inv.InvoiceType),
		Currency:         inv.Currency,
		Subtotal:         inv.Subtotal,
		VATRate:          inv.VATRate,
		VATAmount:        inv.VATAmount,
		Total:            inv.AmountTotal,
		CustomerName:     inv.CustomerName,
		CustomerTaxNo:    inv.CustomerTaxNo,
		CustomerIdentity: inv.CustomerIdentity,
		CustomerAddress:  inv.CustomerAddress,
		IssueDate:        time.Now().UTC(),
	}

	raw, err := BuildUBLTREInvoice(doc)
	if err != nil {
		slog.Error("ubl xml üretimi hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "UBL XML üretilemedi")
		return
	}

	w.Header().Set("Content-Type", "application/xml; charset=utf-8")
	w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s.xml"`, inv.Number))
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(raw)
}

// DownloadInvoicePDF handles GET /v1/billing/invoices/{invoiceId}/pdf
// Türkçe fatura PDF şablonunu (KDV kırılımı + e-Fatura/GİB durumu) üretir.
func (h *Handler) DownloadInvoicePDF(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	invoiceID := chi.URLParam(r, "invoiceId")

	inv, found, err := h.loadInvoice(r, tenantID, invoiceID)
	if err != nil {
		slog.Error("fatura yüklenemedi", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "fatura yüklenemedi")
		return
	}
	if !found {
		httputil.WriteError(w, http.StatusNotFound, "fatura bulunamadı")
		return
	}

	created, _ := time.Parse(time.RFC3339, inv.CreatedAt)
	data := pdf.InvoiceData{
		Number:           inv.Number,
		Status:           inv.Status,
		InvoiceType:      inv.InvoiceType,
		GIBStatus:        inv.GIBStatus,
		DocumentID:       inv.DocumentID,
		Currency:         inv.Currency,
		Subtotal:         inv.Subtotal,
		VATRate:          inv.VATRate,
		VATAmount:        inv.VATAmount,
		Total:            inv.AmountTotal,
		CustomerName:     inv.CustomerName,
		CustomerTaxNo:    inv.CustomerTaxNo,
		CustomerIdentity: inv.CustomerIdentity,
		CustomerAddress:  inv.CustomerAddress,
		PeriodStart:      parsePeriodPtr(inv.PeriodStart),
		PeriodEnd:        parsePeriodPtr(inv.PeriodEnd),
		CreatedAt:        created,
	}

	raw, err := pdf.RenderInvoice(data)
	if err != nil {
		slog.Error("fatura pdf üretimi hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "fatura PDF üretilemedi")
		return
	}

	fileName := inv.Number
	if fileName == "" {
		fileName = inv.ID
	}
	w.Header().Set("Content-Type", "application/pdf")
	w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="invoice-%s.pdf"`, fileName))
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(raw)
}

// submitEFaturaRequest, e-Fatura/e-Arşiv gönderim isteğidir.
type submitEFaturaRequest struct {
	InvoiceType      string `json:"invoice_type"`
	VATRate          int    `json:"vat_rate"`
	CustomerName     string `json:"customer_name"`
	CustomerTaxNo    string `json:"customer_tax_no"`
	CustomerIdentity string `json:"customer_identity"`
	CustomerAddress  string `json:"customer_address"`
}

func parsePeriodPtr(s *string) *time.Time {
	if s == nil || *s == "" {
		return nil
	}
	t, err := time.Parse(time.RFC3339, *s)
	if err != nil {
		return nil
	}
	return &t
}

func RegisterRoutes(r chi.Router, h *Handler) {
	r.Post("/billing/checkout", h.CreateCheckoutSession)
	r.Post("/billing/webhook", h.HandleWebhook)
	r.Get("/billing/subscription", h.GetSubscription)
	r.Get("/billing/invoices", h.ListInvoices)
	r.Get("/billing/invoices/{invoiceId}", h.GetInvoice)
	r.Post("/billing/invoices/{invoiceId}/efatura", h.SubmitEFatura)
	r.Get("/billing/invoices/{invoiceId}/efatura/xml", h.DownloadUBL)
	r.Get("/billing/invoices/{invoiceId}/pdf", h.DownloadInvoicePDF)
	r.Post("/billing/portal", h.CreatePortalSession)
}
