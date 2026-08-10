package billing

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/geolens/platform/platform/httpmw"
)

// buildSignature, Stripe webhook HMAC imzasını test için üretir.
func buildSignature(timestamp, payload, secret string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(timestamp + "." + payload))
	return "t=" + timestamp + ",v1=" + hex.EncodeToString(mac.Sum(nil))
}

// newTestHandler, DB erişimi gerektirmeyen doğrulama hata yollarını test etmek
// için pool'suz bir handler üretir. Geçerli isteklerde DB çağrısına ulaşılmadan
// önce doğrulama hatası dönülür, bu yüzden nil pool güvenlidir.
func newTestHandler() *Handler {
	return &Handler{
		efatura: NewEFaturaProvider("mock"),
	}
}

func ctxWithTenant(tenantID string) context.Context {
	return context.WithValue(context.Background(), httpmw.CtxKeyTenantID, tenantID)
}

func TestSubmitEFatura_ValidationErrors(t *testing.T) {
	cases := []struct {
		name string
		body string
	}{
		{"geçersiz json", "not-json"},
		{"boş invoice_type", `{"vat_rate":20,"customer_name":"Acme","customer_tax_no":"123"}`},
		{"geçersiz invoice_type", `{"invoice_type":"pdf","vat_rate":20,"customer_name":"Acme"}`},
		{"geçersiz KDV oranı", `{"invoice_type":"efatura","vat_rate":15,"customer_name":"Acme","customer_tax_no":"123"}`},
		{"boş müşteri adı", `{"invoice_type":"efatura","vat_rate":20,"customer_tax_no":"123"}`},
		{"e-faturada VKN yok", `{"invoice_type":"efatura","vat_rate":20,"customer_name":"Acme"}`},
		{"e-arşivde VKN ve kimlik yok", `{"invoice_type":"earsiv","vat_rate":20,"customer_name":"Acme"}`},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			h := newTestHandler()
			req := httptest.NewRequest(http.MethodPost, "/v1/billing/invoices/INV1/efatura",
				bytes.NewReader([]byte(c.body)))
			req.Header.Set("Content-Type", "application/json")
			req = req.WithContext(ctxWithTenant("T01"))
			w := httptest.NewRecorder()

			h.SubmitEFatura(w, req)

			resp := w.Result()
			if resp.StatusCode != http.StatusBadRequest {
				t.Fatalf("beklenen 400, gelen %d", resp.StatusCode)
			}
		})
	}
}

func TestStripeWebhookSignature_Invalid(t *testing.T) {
	s := NewStripeClient("", "secret")
	req := httptest.NewRequest(http.MethodPost, "/v1/billing/webhook", strings.NewReader(`{"id":"evt_1"}`))
	req.Header.Set("Stripe-Signature", "t=1620000000,v1=bad")

	if _, err := s.ParseWebhook(req); err == nil {
		t.Fatal("geçersiz imza için hata dönülmedi")
	}
}

func TestStripeWebhookSignature_MissingSecretSkipsVerify(t *testing.T) {
	s := NewStripeClient("", "")
	req := httptest.NewRequest(http.MethodPost, "/v1/billing/webhook",
		strings.NewReader(`{"id":"evt_1","type":"invoice.paid","data":{"object":{}}}`))
	req.Header.Set("Content-Type", "application/json")

	ev, err := s.ParseWebhook(req)
	if err != nil {
		t.Fatalf("secret'sız ayrıştırma hatası: %v", err)
	}
	if ev.Type != "invoice.paid" {
		t.Fatalf("beklenen invoice.paid, gelen %q", ev.Type)
	}
}

func TestStripeWebhookSignature_Valid(t *testing.T) {
	// HMAC imzasını elle üretip doğrulamayı test et
	s := NewStripeClient("", "whsec_test")
	payload := `{"id":"evt_1","type":"invoice.paid","data":{"object":{}}}`
	body := []byte(payload)

	req := httptest.NewRequest(http.MethodPost, "/v1/billing/webhook", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Stripe-Signature", buildSignature("1620000000", payload, "whsec_test"))

	if _, err := s.ParseWebhook(req); err != nil {
		t.Fatalf("geçerli imza doğrulanamadı: %v", err)
	}
}

func TestStripeCheckout_MockMode(t *testing.T) {
	s := NewStripeClient("mock", "")
	sess, err := s.CreateCheckout("T01", "pro", "https://app/ok", "https://app/cancel")
	if err != nil {
		t.Fatalf("mock checkout hatası: %v", err)
	}
	if sess.ID != "cs_mock_T01" {
		t.Fatalf("beklenen cs_mock_T01, gelen %q", sess.ID)
	}
	if sess.URL != "https://app/ok" {
		t.Fatalf("beklenen success url, gelen %q", sess.URL)
	}
}

func TestStripeCheckout_InvalidTier(t *testing.T) {
	s := NewStripeClient("mock", "")
	if _, err := s.CreateCheckout("T01", "gold", "u", "c"); err == nil {
		t.Fatal("geçersiz tier için hata dönülmedi")
	}
}

func TestStripePortal_MockMode(t *testing.T) {
	s := NewStripeClient("mock", "")
	url, err := s.CreatePortalSession("T01", "/billing")
	if err != nil {
		t.Fatalf("mock portal hatası: %v", err)
	}
	if url != "/billing" {
		t.Fatalf("beklenen return url, gelen %q", url)
	}
}

func TestInvoiceJSON_TaxFields(t *testing.T) {
	inv := Invoice{
		ID:              "inv1",
		Number:          "INV-1",
		Status:          "paid",
		AmountTotal:     12000,
		Subtotal:        10000,
		VATRate:         20,
		VATAmount:       2000,
		InvoiceType:     "efatura",
		CustomerName:    "Acme",
		CustomerTaxNo:   "123",
		GIBStatus:       "accepted",
		DocumentID:      "doc1",
		GIBResponseID:   "gib1",
		Currency:        "try",
		StripeInvoiceID: "in_1",
	}

	raw, err := json.Marshal(inv)
	if err != nil {
		t.Fatalf("marshal hatası: %v", err)
	}
	var decoded map[string]interface{}
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("unmarshal hatası: %v", err)
	}
	for key, want := range map[string]interface{}{
		"vat_rate":      float64(20),
		"vat_amount":    float64(2000),
		"subtotal":      float64(10000),
		"invoice_type":  "efatura",
		"gib_status":    "accepted",
		"document_id":   "doc1",
		"customer_name": "Acme",
	} {
		if got := decoded[key]; got != want {
			t.Fatalf("%s: beklenen %v, gelen %v", key, want, got)
		}
	}
}
