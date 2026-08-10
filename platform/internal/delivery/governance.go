package delivery

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/geolens/platform/platform/metrics"
)

// SendGovernanceEvent forwards a Faz 4 governance event (guardrail, gate, incident,
// drift, redteam) to every workspace of the tenant that has an active webhook configured.
// Best-effort: tek bir hedef başarısız olursa diğerleri gönderilmeye devam eder ve hata loglanır;
// hedef yoksa sessizce döner. Worker, gönderim sonrası ACK ettiğinden kaza durumunda yeniden
// teslimat (at-least-once) yinelenen webhook üretebilir — olaylar zaten kaynak tabloda kalıcı
// olduğundan bu kabul edilebilir bir ödünleşimdir.
func (s *service) SendGovernanceEvent(ctx context.Context, tenantID, eventType string, payload map[string]interface{}) error {
	if s.pool == nil {
		return nil
	}

	rows, err := s.pool.Query(ctx, `
		SELECT workspace_id, webhook_url, webhook_kind
		FROM delivery.notification_settings
		WHERE tenant_id = $1 AND webhook_active = true AND webhook_url <> ''
	`, tenantID)
	if err != nil {
		return fmt.Errorf("delivery: governance webhook hedef sorgu: %w", err)
	}
	defer rows.Close()

	type webhookTarget struct {
		workspaceID string
		url         string
		kind        string
	}
	var targets []webhookTarget
	for rows.Next() {
		var t webhookTarget
		if err := rows.Scan(&t.workspaceID, &t.url, &t.kind); err != nil {
			slog.Warn("delivery: governance webhook hedef okuma hatası", "error", err)
			continue
		}
		targets = append(targets, t)
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("delivery: governance webhook hedef iterasyon: %w", err)
	}

	if len(targets) == 0 {
		metrics.WebhookDeliveriesTotal.WithLabelValues(eventType, "no_target").Inc()
		return nil
	}

	for _, t := range targets {
		notif := buildGovernanceNotification(tenantID, t.workspaceID, eventType, payload, WebhookKind(t.kind), t.url)
		if err := s.SendWebhook(notif); err != nil {
			slog.Warn("delivery: governance webhook gönderilemedi",
				"tenant", tenantID, "workspace", t.workspaceID, "event", eventType, "error", err)
			metrics.WebhookDeliveriesTotal.WithLabelValues(eventType, "failed").Inc()
			continue
		}
		metrics.WebhookDeliveriesTotal.WithLabelValues(eventType, "sent").Inc()
		slog.Debug("governance webhook gönderildi", "tenant", tenantID, "workspace", t.workspaceID, "event", eventType)
	}

	return nil
}
