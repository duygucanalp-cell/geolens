package billing

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
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
	body, _ := json.Marshal(params)

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

	raw, _ := io.ReadAll(resp.Body)
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

	var event StripeEvent
	if err := json.Unmarshal(body, &event); err != nil {
		return nil, fmt.Errorf("webhook ayrıştırma: %w", err)
	}

	return &event, nil
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
		data, _ := json.Marshal(event.Data.Object)
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

		_, err := pool.Exec(ctx, `
			UPDATE identity.tenants SET tier = $1, updated_at = now() WHERE id = $2
		`, tier, tenantID)
		if err != nil {
			return fmt.Errorf("tier güncelleme: %w", err)
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
