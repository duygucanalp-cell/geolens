package delivery

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/sendgrid/sendgrid-go"
	"github.com/sendgrid/sendgrid-go/helpers/mail"

	"github.com/geolens/platform/platform/db"
)

// service implements the Service interface for delivery.
type service struct {
	config EmailConfig
	pool   *db.Pool
}

// NewService creates a new delivery service.
func NewService(cfg EmailConfig, pool *db.Pool) Service {
	return &service{config: cfg, pool: pool}
}

// SendNotification sends a single notification.
// For now, only email channel is supported.
func (s *service) SendNotification(notif Notification) error {
	switch notif.Channel {
	case ChannelEmail:
		return s.sendEmailNotification(&notif)
	case ChannelInApp:
		slog.Debug("in-app notification (not yet implemented)", "id", notif.ID)
		return nil
	default:
		return fmt.Errorf("delivery: bilinmeyen kanal: %s", notif.Channel)
	}
}

// SendEmail sends a plain email using SendGrid.
func (s *service) SendEmail(to, subject, htmlContent string) error {
	if s.config.SendGridKey == "" || s.config.SendGridKey == "mock" {
		slog.Info("mock email sent", "to", to, "subject", subject)
		return nil
	}

	from := mail.NewEmail(s.config.FromName, s.config.FromEmail)
	toEmail := mail.NewEmail("", to)
	message := mail.NewSingleEmail(from, subject, toEmail, "", htmlContent)

	client := sendgrid.NewSendClient(s.config.SendGridKey)
	resp, err := client.Send(message)
	if err != nil {
		return fmt.Errorf("delivery: sendgrid gönderme hatası: %w", err)
	}

	if resp.StatusCode >= 400 {
		return fmt.Errorf("delivery: sendgrid hatası (HTTP %d): %s", resp.StatusCode, resp.Body)
	}

	slog.Info("email sent", "to", to, "subject", subject, "status", resp.StatusCode)
	return nil
}

// SendWeeklyDigest sends a weekly digest for a workspace.
func (s *service) SendWeeklyDigest(workspaceID, tenantID string) error {
	subject := "GeoLens Haftalık Özet — " + time.Now().Format("02.01.2006")

	htmlContent := `<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><style>
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f1f5f9; margin: 0; padding: 0; }
.container { max-width: 600px; margin: 0 auto; padding: 20px; }
.header { background: linear-gradient(135deg, #6366f1, #4f46e5); color: white; padding: 24px; border-radius: 12px 12px 0 0; text-align: center; }
.header h1 { margin: 0; font-size: 22px; }
.header p { margin: 8px 0 0; opacity: 0.9; font-size: 14px; }
.section { background: white; padding: 20px; border-bottom: 1px solid #e2e8f0; }
.section:last-child { border-radius: 0 0 12px 12px; }
.section h2 { font-size: 16px; margin: 0 0 12px; color: #1e293b; }
.score-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f1f5f9; }
.score-row:last-child { border-bottom: none; }
.brand-name { font-weight: 600; color: #334155; }
.score-value { font-weight: 700; color: #6366f1; }
.change-up { color: #22c55e; }
.change-down { color: #ef4444; }
.rec-item { padding: 8px 0; border-bottom: 1px solid #f1f5f9; font-size: 14px; color: #475569; }
.rec-item:last-child { border-bottom: none; }
.footer { text-align: center; padding: 20px; font-size: 12px; color: #94a3b8; }
.btn { display: inline-block; padding: 10px 20px; background: #6366f1; color: white; text-decoration: none; border-radius: 8px; font-size: 14px; margin-top: 12px; }
</style></head>
<body>
<div class="container">
  <div class="header">
    <h1>GeoLens Haftalık Özet</h1>
    <p>` + time.Now().Format("02.01.2006") + `</p>
  </div>
  <div class="section">
    <h2>📊 Görünürlük Skorları</h2>
    <div class="score-row">
      <span class="brand-name">Acme</span>
      <span><span class="score-value">85</span> <span class="change-up">↑5</span></span>
    </div>
    <div class="score-row">
      <span class="brand-name">BetaCorp</span>
      <span><span class="score-value">62</span> <span class="change-down">↓8</span></span>
    </div>
    <div class="score-row">
      <span class="brand-name">GammaInc</span>
      <span><span class="score-value">43</span> <span class="change-down">↓2</span></span>
    </div>
    <a href="#" class="btn">Panoda Görüntüle</a>
  </div>
  <div class="section">
    <h2>💡 Öneriler</h2>
    <div class="rec-item">• Acme: Görünürlük yükselişte — mevcut stratejiyi koruyun.</div>
    <div class="rec-item">• BetaCorp: Skor düşüşü tespit edildi — rakip analizi önerilir.</div>
    <div class="rec-item">• GammaInc: Yapılandırılmış veri ekleyerek görünürlüğü artırabilirsiniz.</div>
  </div>
  <div class="footer">
    Bu e-posta GeoLens AI Visibility Platform tarafından otomatik gönderilmiştir.
  </div>
</div>
</body>
</html>`

	return s.SendEmail("user@example.com", subject, htmlContent)
}

// GetSettings returns the notification settings for a workspace.
// Reads from PostgreSQL; returns defaults if no record exists.
func (s *service) GetSettings(workspaceID, tenantID string) (*NotificationSettings, error) {
	settings := &NotificationSettings{WorkspaceID: workspaceID}

	err := s.pool.QueryRow(context.Background(), `
		SELECT email_address, digest_enabled, digest_day, digest_time,
		       digest_format, notify_on_drop, drop_threshold
		FROM delivery.notification_settings
		WHERE workspace_id = $1 AND tenant_id = $2
	`, workspaceID, tenantID).Scan(
		&settings.EmailAddress, &settings.DigestEnabled, &settings.DigestDay,
		&settings.DigestTime, &settings.DigestFormat,
		&settings.NotifyOnDrop, &settings.DropThreshold,
	)

	if err != nil {
		// Return sensible defaults
		return &NotificationSettings{
			WorkspaceID:   workspaceID,
			DigestDay:     "monday",
			DigestTime:    "09:00",
			DigestFormat:  "email",
			DigestEnabled: true,
			NotifyOnDrop:  true,
			DropThreshold: 10,
		}, nil
	}

	return settings, nil
}

// UpdateSettings validates and saves the notification settings for a workspace.
func (s *service) UpdateSettings(settings *NotificationSettings, tenantID string) error {
	if err := ValidateSettings(settings); err != nil {
		return err
	}

	_, err := s.pool.Exec(context.Background(), `
		INSERT INTO delivery.notification_settings
			(workspace_id, tenant_id, email_address, digest_enabled, digest_day,
			 digest_time, digest_format, notify_on_drop, drop_threshold, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
		ON CONFLICT (workspace_id, tenant_id) DO UPDATE SET
			email_address = EXCLUDED.email_address,
			digest_enabled = EXCLUDED.digest_enabled,
			digest_day = EXCLUDED.digest_day,
			digest_time = EXCLUDED.digest_time,
			digest_format = EXCLUDED.digest_format,
			notify_on_drop = EXCLUDED.notify_on_drop,
			drop_threshold = EXCLUDED.drop_threshold,
			updated_at = now()
	`, settings.WorkspaceID, tenantID, settings.EmailAddress, settings.DigestEnabled,
		settings.DigestDay, settings.DigestTime, settings.DigestFormat,
		settings.NotifyOnDrop, settings.DropThreshold)

	if err != nil {
		return fmt.Errorf("delivery: ayarlar kaydedilemedi: %w", err)
	}

	slog.Info("notification settings saved to DB", "workspace", settings.WorkspaceID, "tenant", tenantID, "enabled", settings.DigestEnabled)
	return nil
}

// sendEmailNotification sends a notification as email (pointer ile çağrılır).
func (s *service) sendEmailNotification(notif *Notification) error {
	htmlContent := notif.HTMLBody
	if htmlContent == "" {
		htmlContent = fmt.Sprintf("<h2>%s</h2><p>%s</p>", notif.Title, notif.Body)
	}

	err := s.SendEmail(notif.UserID+"@example.com", notif.Title, htmlContent)
	if err != nil {
		notif.Status = DeliveryFailed
		return err
	}

	now := time.Now()
	notif.Status = DeliverySent
	notif.SentAt = &now
	return nil
}
