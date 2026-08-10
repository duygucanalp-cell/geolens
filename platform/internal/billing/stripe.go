package billing

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/geolens/platform/platform/db"
)

type StripeClient struct {
	APIKey        string
	WebhookSecret string
	httpClient    *http.Client

	// priceIDs, tier+currency → Stripe Price ID eşlemesidir.
	// HT2 globalleşme (multi-currency): farklı para birimleri için ayrı price ID'leri atanır.
	// Boş bırakılırsa varsayılan (usd) map kullanılır. Env'den doldurulabilir.
	priceIDs map[string]string
}

// currencyPriceIDs returns the tier→priceId lookup for a given currency.
// Desteklenen para birimleri: usd, eur, try (TR), gbp (HT2 multi-currency).
// Bilinmeyen currency için usd'e düşer.
func (s *StripeClient) currencyPriceIDs(currency string) map[string]string {
	if s.priceIDs != nil {
		return s.priceIDs
	}
	switch currency {
	case "eur":
		return map[string]string{
			"pro":        "price_pro_monthly_eur",
			"business":   "price_business_monthly_eur",
			"enterprise": "price_enterprise_monthly_eur",
		}
	case "try":
		return map[string]string{
			"pro":        "price_pro_monthly_try",
			"business":   "price_business_monthly_try",
			"enterprise": "price_enterprise_monthly_try",
		}
	case "gbp":
		return map[string]string{
			"pro":        "price_pro_monthly_gbp",
			"business":   "price_business_monthly_gbp",
			"enterprise": "price_enterprise_monthly_gbp",
		}
	default:
		return map[string]string{
			"pro":        "price_pro_monthly",
			"business":   "price_business_monthly",
			"enterprise": "price_enterprise_monthly",
		}
	}
}

// SetPriceIDs overrides the tier/currency price mapping (env-driven, HT2 multi-currency).
func (s *StripeClient) SetPriceIDs(priceIDs map[string]string) {
	s.priceIDs = priceIDs
}

type CheckoutSession struct {
	ID  string
	URL string
}

func NewStripeClient(apiKey, webhookSecret string) *StripeClient {
	return &StripeClient{
		APIKey:        apiKey,
		WebhookSecret: webhookSecret,
		httpClient:    &http.Client{Timeout: 30 * time.Second},
	}
}

func (s *StripeClient) CreateCheckout(ctx context.Context, tenantID, tier, currency, successURL, cancelURL string) (*CheckoutSession, error) {
	priceMap := s.currencyPriceIDs(currency)
	priceID, ok := priceMap[tier]
	if !ok {
		return nil, fmt.Errorf("bilinmeyen tier: %s", tier)
	}

	if s.APIKey == "" || s.APIKey == "mock" {
		slog.Warn("stripe mock modda çalışıyor — gerçek ödeme alınmaz")
		return &CheckoutSession{
			ID:  "cs_mock_" + tenantID,
			URL: successURL,
		}, nil
	}

	params := map[string]interface{}{
		"mode": "subscription",
		"line_items": []map[string]interface{}{
			{"price": priceID, "quantity": 1},
		},
		"client_reference_id": tenantID,
		"success_url":         successURL,
		"cancel_url":          cancelURL,
		"metadata": map[string]string{
			"tenant_id": tenantID,
			"tier":      tier,
			"currency":  currency,
		},
	}
	body, err := json.Marshal(params)
	if err != nil {
		return nil, fmt.Errorf("param marshal: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", "https://api.stripe.com/v1/checkout/sessions", bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("stripe istek: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+s.APIKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("stripe api çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("stripe yanıt okuma: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("stripe api hatası (HTTP %d): %s", resp.StatusCode, string(raw))
	}

	var result struct {
		ID  string `json:"id"`
		URL string `json:"url"`
	}
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, fmt.Errorf("stripe yanıt ayrıştırma: %w", err)
	}

	return &CheckoutSession{ID: result.ID, URL: result.URL}, nil
}

// Invoice represents a normalized Stripe invoice persisted for the tenant.
type Invoice struct {
	ID              string  `json:"id"`
	StripeInvoiceID string  `json:"stripe_invoice_id"`
	Number          string  `json:"number"`
	Status          string  `json:"status"`
	AmountTotal     int64   `json:"amount_total"`
	Currency        string  `json:"currency"`
	PeriodStart     *string `json:"period_start,omitempty"`
	PeriodEnd       *string `json:"period_end,omitempty"`
	HostedURL       string  `json:"hosted_invoice_url"`
	PDFURL          string  `json:"invoice_pdf"`
	CreatedAt       string  `json:"created_at"`

	// TR özel vergi alanları (FR-A6)
	Subtotal         int64  `json:"subtotal"`
	VATRate          int    `json:"vat_rate"`
	VATAmount        int64  `json:"vat_amount"`
	InvoiceType      string `json:"invoice_type"`
	CustomerName     string `json:"customer_name"`
	CustomerTaxNo    string `json:"customer_tax_no"`
	CustomerIdentity string `json:"customer_identity"`
	CustomerAddress  string `json:"customer_address"`
	GIBStatus        string `json:"gib_status"`
	DocumentID       string `json:"document_id"`
	GIBResponseID    string `json:"gib_response_id"`
}

// CreatePortalSession creates a Stripe billing portal session for the tenant's subscription.
// Fatura görüntüleme, kredi kartı yönetimi, paket yükseltme/düşürme ve iptal işlemleri
// Stripe'in kendi yönetim arayüzü üzerinden yapılır (FR-A6 self-serve UI).
func (s *StripeClient) CreatePortalSession(ctx context.Context, tenantID, returnURL string) (string, error) {
	if s.APIKey == "" || s.APIKey == "mock" {
		slog.Warn("stripe mock modda çalışıyor — portal session oluşturulmaz")
		return returnURL, nil
	}

	// Kiracının Stripe customer ID'sini bul
	var customerID string
	// Metadata üzerinden eşleme: checkout sırasında client_reference_id=tenantID kullanılır.
	// Gerçek customer ID, ilk ödeme sonrası webhook'ta yakalanır; burada customer listesi aranır.
	body := "limit=100"
	req, err := http.NewRequestWithContext(ctx, "GET",
		"https://api.stripe.com/v1/customers?"+body, nil)
	if err != nil {
		return "", fmt.Errorf("stripe customer istek: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+s.APIKey)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("stripe customer çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("stripe customer yanıt okuma: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("stripe customer api hatası (HTTP %d): %s", resp.StatusCode, string(raw))
	}

	var customers struct {
		Data []struct {
			ID       string `json:"id"`
			Metadata struct {
				TenantID string `json:"tenant_id"`
			} `json:"metadata"`
		} `json:"data"`
	}
	if err := json.Unmarshal(raw, &customers); err != nil {
		return "", fmt.Errorf("stripe customer ayrıştırma: %w", err)
	}

	for _, c := range customers.Data {
		if c.Metadata.TenantID == tenantID {
			customerID = c.ID
			break
		}
	}
	if customerID == "" {
		return "", fmt.Errorf("tenant için stripe customer bulunamadı: %s", tenantID)
	}

	portalParams, err := json.Marshal(map[string]interface{}{
		"customer":   customerID,
		"return_url": returnURL,
	})
	if err != nil {
		return "", fmt.Errorf("portal param marshal: %w", err)
	}

	req, err = http.NewRequestWithContext(ctx, "POST",
		"https://api.stripe.com/v1/billing_portal/sessions", bytes.NewReader(portalParams))
	if err != nil {
		return "", fmt.Errorf("stripe portal istek: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+s.APIKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err = s.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("stripe portal çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	raw, err = io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("stripe portal yanıt okuma: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("stripe portal api hatası (HTTP %d): %s", resp.StatusCode, string(raw))
	}

	var portal struct {
		URL string `json:"url"`
	}
	if err := json.Unmarshal(raw, &portal); err != nil {
		return "", fmt.Errorf("stripe portal ayrıştırma: %w", err)
	}
	if portal.URL == "" {
		return "", fmt.Errorf("stripe portal url boş")
	}

	return portal.URL, nil
}

// UpsertInvoice persists an invoice received from a Stripe webhook.
// invoice.created / invoice.paid / invoice.finalized olaylarında çağrılır.
func (s *StripeClient) UpsertInvoice(ctx context.Context, pool *db.Pool, event *StripeEvent) error {
	var inv struct {
		ID               string  `json:"id"`
		Number           string  `json:"number"`
		Status           string  `json:"status"`
		AmountTotal      int64   `json:"amount_total"`
		Currency         string  `json:"currency"`
		Subscription     *string `json:"subscription"`
		Customer         string  `json:"customer"`
		HostedInvoiceURL string  `json:"hosted_invoice_url"`
		InvoicePDF       string  `json:"invoice_pdf"`
		Created          int64   `json:"created"`
		PeriodStart      *int64  `json:"period_start"`
		PeriodEnd        *int64  `json:"period_end"`
		Lines            struct {
			Data []struct {
				Period struct {
					Start int64 `json:"start"`
					End   int64 `json:"end"`
				} `json:"period"`
			} `json:"data"`
		} `json:"lines"`
	}

	data, err := json.Marshal(event.Data.Object)
	if err != nil {
		return fmt.Errorf("invoice data marshal: %w", err)
	}
	if err := json.Unmarshal(data, &inv); err != nil {
		return fmt.Errorf("invoice ayrıştırma: %w", err)
	}

	tenantID := event.MetadataTenantID()
	if tenantID == "" && inv.Customer != "" {
		// Customer → tenant eşlemesini çek
		tenantID = s.lookupTenantByCustomer(ctx, pool, inv.Customer)
	}
	if tenantID == "" {
		return fmt.Errorf("invoice: tenant_id bulunamadı (invoice %s)", inv.ID)
	}

	periodStart := inv.PeriodStart
	periodEnd := inv.PeriodEnd
	if len(inv.Lines.Data) > 0 {
		ps := inv.Lines.Data[0].Period.Start
		pe := inv.Lines.Data[0].Period.End
		if ps != 0 {
			periodStart = &ps
		}
		if pe != 0 {
			periodEnd = &pe
		}
	}

	_, err = pool.Exec(ctx, `
		INSERT INTO billing.invoices (
			stripe_invoice_id, tenant_id, stripe_subscription, number, status,
			amount_total, currency, period_start, period_end, hosted_invoice_url, invoice_pdf, created_at, updated_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, to_timestamp($12), now())
		ON CONFLICT (stripe_invoice_id) DO UPDATE SET
			status = EXCLUDED.status,
			amount_total = EXCLUDED.amount_total,
			currency = EXCLUDED.currency,
			number = EXCLUDED.number,
			hosted_invoice_url = EXCLUDED.hosted_invoice_url,
			invoice_pdf = EXCLUDED.invoice_pdf,
			updated_at = now()
	`, inv.ID, tenantID, inv.Subscription, inv.Number, inv.Status,
		inv.AmountTotal, inv.Currency, periodStart, periodEnd, inv.HostedInvoiceURL, inv.InvoicePDF, inv.Created)
	if err != nil {
		return fmt.Errorf("invoice persist: %w", err)
	}

	// Stripe faturaları KDV kırılımı içermediği için varsayılan olarak
	// subtotal = amount_total, KDV = 0 olarak işlenir. Kullanıcı e-Fatura/e-Arşiv
	// gönderirken KDV oranı belirtir; o zaman subtotal/KDV yeniden hesaplanır.
	if _, err := pool.Exec(ctx, `
		UPDATE billing.invoices
		SET subtotal = amount_total
		WHERE stripe_invoice_id = $1 AND invoice_type = 'standard'
	`, inv.ID); err != nil {
		slog.Warn("invoice subtotal varsayılanı ayarlanamadı", "invoice_id", inv.ID, "error", err)
	}

	slog.Info("invoice kaydedildi", "invoice_id", inv.ID, "tenant_id", tenantID, "status", inv.Status)
	return nil
}

// lookupTenantByCustomer resolves a Stripe customer ID to a tenant ID.
// Checkout tamamlandığında billing.stripe_customers tablosuna eşleme yazılır.
func (s *StripeClient) lookupTenantByCustomer(ctx context.Context, pool *db.Pool, customerID string) string {
	var tenantID string
	err := pool.QueryRow(ctx, `
		SELECT tenant_id FROM billing.stripe_customers WHERE customer_id = $1
	`, customerID).Scan(&tenantID)
	if err != nil {
		return ""
	}
	return tenantID
}

// SaveCustomerMapping persists the tenant ↔ Stripe customer mapping after a checkout completes.
func (s *StripeClient) SaveCustomerMapping(ctx context.Context, pool *db.Pool, tenantID, customerID string) error {
	if customerID == "" || tenantID == "" {
		return nil
	}
	_, err := pool.Exec(ctx, `
		INSERT INTO billing.stripe_customers (tenant_id, customer_id, created_at)
		VALUES ($1, $2, now())
		ON CONFLICT (tenant_id) DO UPDATE SET customer_id = EXCLUDED.customer_id
	`, tenantID, customerID)
	if err != nil {
		return fmt.Errorf("stripe customer eşleme kaydı: %w", err)
	}
	return nil
}

// MetadataTenantID extracts tenant_id from the event metadata if present.
func (e *StripeEvent) MetadataTenantID() string {
	var obj struct {
		Metadata struct {
			TenantID string `json:"tenant_id"`
		} `json:"metadata"`
	}
	if err := json.Unmarshal(e.Data.Object, &obj); err != nil {
		return ""
	}
	return obj.Metadata.TenantID
}

func (s *StripeClient) ParseWebhook(r *http.Request) (*StripeEvent, error) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return nil, fmt.Errorf("webhook body okuma: %w", err)
	}

	if s.WebhookSecret != "" {
		sigHeader := r.Header.Get("Stripe-Signature")
		if sigHeader == "" {
			return nil, fmt.Errorf("Stripe-Signature header eksik")
		}
		if err := verifyStripeSignature(sigHeader, string(body), s.WebhookSecret); err != nil {
			return nil, fmt.Errorf("stripe imza doğrulama: %w", err)
		}
	} else {
		slog.Warn("stripe webhook secret boş — imza doğrulaması yapılmıyor")
	}

	var event StripeEvent
	if err := json.Unmarshal(body, &event); err != nil {
		return nil, fmt.Errorf("webhook ayrıştırma: %w", err)
	}

	return &event, nil
}

func verifyStripeSignature(sigHeader, payload, secret string) error {
	parts := strings.Split(sigHeader, ",")
	var sigTime string
	var sigValue string
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if strings.HasPrefix(part, "t=") {
			sigTime = strings.TrimPrefix(part, "t=")
		}
		if strings.HasPrefix(part, "v1=") {
			sigValue = strings.TrimPrefix(part, "v1=")
		}
	}
	if sigTime == "" || sigValue == "" {
		return fmt.Errorf("geçersiz Stripe-Signature formatı")
	}

	signedPayload := sigTime + "." + payload
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(signedPayload))
	expected := hex.EncodeToString(mac.Sum(nil))

	if !hmac.Equal([]byte(expected), []byte(sigValue)) {
		return fmt.Errorf("stripe webhook imzası eşleşmiyor")
	}
	return nil
}

func (s *StripeClient) HandleEvent(ctx context.Context, pool *db.Pool, event *StripeEvent) error {
	switch event.Type {
	case "checkout.session.completed":
		var session struct {
			ClientReferenceID string `json:"client_reference_id"`
			Metadata          struct {
				TenantID string `json:"tenant_id"`
				Tier     string `json:"tier"`
			} `json:"metadata"`
			Subscription string `json:"subscription"`
			Customer     string `json:"customer"`
		}
		data, err := json.Marshal(event.Data.Object)
		if err != nil {
			return fmt.Errorf("event data marshal: %w", err)
		}
		if err := json.Unmarshal(data, &session); err != nil {
			return fmt.Errorf("session ayrıştırma: %w", err)
		}

		tenantID := session.ClientReferenceID
		if tenantID == "" {
			tenantID = session.Metadata.TenantID
		}
		tier := session.Metadata.Tier
		if tier == "" {
			tier = "pro"
		}

		if tenantID == "" {
			return fmt.Errorf("checkout.session.completed: tenant_id bulunamadı")
		}

		if err := s.SaveCustomerMapping(ctx, pool, tenantID, session.Customer); err != nil {
			slog.Warn("stripe customer eşleme kaydedilemedi", "tenant_id", tenantID, "error", err)
		}

		result, err := pool.Exec(ctx, `
			UPDATE identity.tenants SET tier = $1, updated_at = now() WHERE id = $2
		`, tier, tenantID)
		if err != nil {
			return fmt.Errorf("tier güncelleme: %w", err)
		}
		if result.RowsAffected() == 0 {
			slog.Warn("tier güncelleme: tenant bulunamadı", "tenant_id", tenantID)
		}
		slog.Info("tier yükseltme tamam", "tenant_id", tenantID, "tier", tier)

	case "invoice.payment_failed":
		slog.Warn("stripe ödeme başarısız", "event_id", event.ID)

	case "invoice.created", "invoice.paid", "invoice.finalized", "invoice.updated":
		if err := s.UpsertInvoice(ctx, pool, event); err != nil {
			slog.Warn("invoice işlenemedi", "event_id", event.ID, "error", err)
			return err
		}
	}

	return nil
}

type StripeEvent struct {
	ID              string          `json:"id"`
	Type            string          `json:"type"`
	APIVersion      string          `json:"api_version"`
	Data            StripeEventData `json:"data"`
	Created         int64           `json:"created"`
	Livemode        bool            `json:"livemode"`
	PendingWebhooks int             `json:"pending_webhooks"`
	Request         json.RawMessage `json:"request,omitempty"`
}

type StripeEventData struct {
	Object json.RawMessage `json:"object"`
}
