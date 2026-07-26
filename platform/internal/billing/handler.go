// Package billing provides handlers and logic for billing functionality.
package billing

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool   *db.Pool
	stripe *StripeClient
}

func NewHandler(pool *db.Pool, stripeKey, webhookSecret string) *Handler {
	return &Handler{
		pool:   pool,
		stripe: NewStripeClient(stripeKey, webhookSecret),
	}
}

func (h *Handler) CreateCheckoutSession(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		Tier       string `json:"tier"`
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

	session, err := h.stripe.CreateCheckout(tenantID, req.Tier, req.SuccessURL, req.CancelURL)
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

func RegisterRoutes(r chi.Router, h *Handler) {
	r.Post("/billing/checkout", h.CreateCheckoutSession)
	r.Post("/billing/webhook", h.HandleWebhook)
	r.Get("/billing/subscription", h.GetSubscription)
}
