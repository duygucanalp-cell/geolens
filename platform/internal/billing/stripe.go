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

func (s *StripeClient) CreateCheckout(tenantID, tier, successURL, cancelURL string) (*CheckoutSession, error) {
	priceMap := map[string]string{
		"pro":        "price_pro_monthly",
		"business":   "price_business_monthly",
		"enterprise": "price_enterprise_monthly",
	}
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
		},
	}
	body, err := json.Marshal(params)
	if err != nil {
		return nil, fmt.Errorf("param marshal: %w", err)
	}

	req, err := http.NewRequest("POST", "https://api.stripe.com/v1/checkout/sessions", bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("stripe istek: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+s.APIKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("stripe api çağrısı: %w", err)
	}
	defer resp.Body.Close()

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
