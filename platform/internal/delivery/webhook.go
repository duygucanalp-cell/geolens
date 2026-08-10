package delivery

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"
)

// webhookClient is a minimal HTTP client for webhook delivery.
var webhookHTTPClient = &http.Client{Timeout: 10 * time.Second}

// SendWebhook delivers a notification to an external webhook endpoint.
// HT2 webhook çeşitlendirme: Slack, Microsoft Teams, Discord, PagerDuty ve custom
// webhook formatlarını destekler. Aynı Notification yapısı kullanılır; metod
// uç formatına göre uygun payload'u üretir.
func (s *service) SendWebhook(notif Notification) error {
	if notif.WebhookURL == "" {
		return fmt.Errorf("delivery: webhook URL gerekli")
	}

	payload, ct, err := buildWebhookPayload(notif)
	if err != nil {
		return fmt.Errorf("delivery: webhook payload oluşturma: %w", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, notif.WebhookURL, bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("delivery: webhook istek oluşturma: %w", err)
	}
	req.Header.Set("Content-Type", ct)

	resp, err := webhookHTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("delivery: webhook çağrısı: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode >= 300 {
		return fmt.Errorf("delivery: webhook hatası (HTTP %d)", resp.StatusCode)
	}

	slog.Debug("webhook gönderildi", "kind", notif.WebhookKind, "status", resp.StatusCode)
	return nil
}

// buildWebhookPayload produces the platform-specific JSON payload for a webhook kind.
// Kullanılmayan method parametreleri bilinçli olarak yok: farklı formatlar için
// payload şablonu ayrı ayrı üretilir.
func buildWebhookPayload(notif Notification) ([]byte, string, error) {
	switch notif.WebhookKind {
	case WebhookKindSlack:
		return buildSlackPayload(notif)
	case WebhookKindTeams:
		return buildTeamsPayload(notif)
	case WebhookKindDiscord:
		return buildDiscordPayload(notif)
	case WebhookKindPagerDuty:
		return buildPagerDutyPayload(notif)
	default:
		return buildGenericPayload(notif)
	}
}

func buildGenericPayload(notif Notification) ([]byte, string, error) {
	payload := map[string]interface{}{
		"event":     notif.Type,
		"title":     notif.Title,
		"body":      notif.Body,
		"workspace": notif.WorkspaceID,
		"tenant":    notif.TenantID,
		"sent_at":   time.Now().UTC().Format(time.RFC3339),
		"data":      notif.Data,
	}
	b, err := json.Marshal(payload)
	if err != nil {
		return nil, "", err
	}
	return b, "application/json", nil
}

func buildSlackPayload(notif Notification) ([]byte, string, error) {
	payload := map[string]interface{}{
		"text": fmt.Sprintf("*%s*\n%s", notif.Title, notif.Body),
	}
	b, err := json.Marshal(payload)
	if err != nil {
		return nil, "", err
	}
	return b, "application/json", nil
}

func buildTeamsPayload(notif Notification) ([]byte, string, error) {
	payload := map[string]interface{}{
		"@type":    "MessageCard",
		"@context": "http://schema.org/extensions",
		"summary":  notif.Title,
		"title":    notif.Title,
		"text":     notif.Body,
	}
	b, err := json.Marshal(payload)
	if err != nil {
		return nil, "", err
	}
	return b, "application/json", nil
}

func buildDiscordPayload(notif Notification) ([]byte, string, error) {
	payload := map[string]interface{}{
		"content": fmt.Sprintf("**%s**\n%s", notif.Title, notif.Body),
	}
	b, err := json.Marshal(payload)
	if err != nil {
		return nil, "", err
	}
	return b, "application/json", nil
}

func buildPagerDutyPayload(notif Notification) ([]byte, string, error) {
	severity := "info"
	if notif.Type == NotificationScoreDrop {
		severity = "warning"
	}
	// Governance olayları: payload severity (critical/warning/high) PagerDuty severity'ye yansır
	if s, ok := notif.Data["severity"].(string); ok && s != "" {
		switch s {
		case "critical":
			severity = "critical"
		case "warning", "high":
			severity = "warning"
		}
	}
	payload := map[string]interface{}{
		"routing_key":  "geolens-alert",
		"event_action": "trigger",
		"payload": map[string]interface{}{
			"summary":  notif.Title,
			"source":   "geolens-platform",
			"severity": severity,
			"custom_details": map[string]interface{}{
				"body":      notif.Body,
				"workspace": notif.WorkspaceID,
				"tenant":    notif.TenantID,
			},
		},
	}
	b, err := json.Marshal(payload)
	if err != nil {
		return nil, "", err
	}
	return b, "application/json", nil
}

// buildGovernanceNotification constructs a webhook Notification from a Faz 4 governance event.
// NotificationType olarak eventType taşınır (generic payload'da "event" alanına yansır).
func buildGovernanceNotification(tenantID, workspaceID, eventType string, payload map[string]interface{}, kind WebhookKind, url string) Notification {
	title, body := governanceEventMeta(eventType, payload)
	return Notification{
		TenantID:    tenantID,
		WorkspaceID: workspaceID,
		Type:        NotificationType(eventType),
		Channel:     ChannelWebhook,
		Title:       title,
		Body:        body,
		Data:        payload,
		WebhookURL:  url,
		WebhookKind: kind,
		Status:      DeliveryPending,
	}
}

// governanceEventMeta maps a governance event type to a human-readable title and body summary.
// Bilinmeyen event tipleri için genel bir başlık döner (gelecekteki olaylar da taşınabilir).
func governanceEventMeta(eventType string, payload map[string]interface{}) (string, string) {
	str := func(k string) string {
		if v, ok := payload[k].(string); ok {
			return v
		}
		return ""
	}
	num := func(k string) float64 {
		if v, ok := payload[k].(float64); ok {
			return v
		}
		return 0
	}

	switch eventType {
	case "guardrail.violation":
		body := "Kural: " + str("rule_name")
		if c := str("category"); c != "" {
			body += " | Kategori: " + c
		}
		if a := str("action_taken"); a != "" {
			body += " | Aksiyon: " + a
		}
		return "Guardrail İhlali Tespit Edildi", body
	case "gate.check.decision":
		return "Gate Kontrol Kararı", fmt.Sprintf("%s %s sürümü → %s (%s)",
			str("entity_type"), str("version"), str("decision"), str("target_env"))
	case "incident.opened":
		return "Yeni Olay Açıldı", fmt.Sprintf("[%s] %s (%s)", str("severity"), str("title"), str("category"))
	case "drift.alert.triggered":
		return "Drift Uyarısı", fmt.Sprintf("%s: skor %.2f (%s, delta %.2f)",
			str("metric"), num("drift_score"), str("severity"), num("delta"))
	case "redteam.run.completed":
		return "Red Team Çalışması Tamamlandı", fmt.Sprintf("Hedef: %s | Geçen: %.0f | Kalan: %.0f | Savunma Skoru: %.0f",
			str("target_name"), num("passed"), num("failed"), num("defense_score"))
	default:
		body := str("detail")
		if body == "" {
			body = "Olay detayları için panoyu kontrol edin."
		}
		return "Yönetişim Olayı: " + eventType, body
	}
}
