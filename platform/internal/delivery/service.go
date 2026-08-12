package delivery

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/sendgrid/sendgrid-go"
	"github.com/sendgrid/sendgrid-go/helpers/mail"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
)

// service implements the Service interface for delivery.
type service struct {
	config EmailConfig
	pool   *db.Pool
	// testPool — dbiface.DB uyumlu test/mock havuzu; nil ise pool kullanılır.
	// db.Pool.Begin imzası dbiface.Tx ile uyuşmadığından pool dbiface.DB olarak
	// atanamaz; in-app metotları her iki havuzla da çalışır (mock test desteği).
	testPool dbiface.DB
}

// NewService creates a new delivery service.
func NewService(cfg EmailConfig, pool *db.Pool) Service {
	return &service{config: cfg, pool: pool}
}

// SendNotification sends a single notification.
// Desteklenen kanallar: email, webhook (Slack/Teams/Discord/PagerDuty/custom). In-app henüz pasif.
func (s *service) SendNotification(notif Notification) error {
	switch notif.Channel {
	case ChannelEmail:
		return s.sendEmailNotification(&notif)
	case ChannelWebhook:
		if err := s.SendWebhook(notif); err != nil {
			notif.Status = DeliveryFailed
			return err
		}
		now := time.Now()
		notif.Status = DeliverySent
		notif.SentAt = &now
		return nil
	case ChannelInApp:
		return s.saveInAppNotification(&notif)
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

// digestBrandScore holds score data for a single brand in the digest.
type digestBrandScore struct {
	BrandID       string
	BrandName     string
	Score         float64
	PreviousScore float64
	Change        float64
}

// digestRecommendation holds recommendation data for the digest.
type digestRecommendation struct {
	BrandName string
	Title     string
	Detail    string
}

// SendWeeklyDigest sends a weekly digest for a workspace using real DB data.
// Skorları, trend verilerini ve önerileri veritabanından çeker.
// Derin bağlantılı URL'ler ile panoya yönlendirme yapar.
func (s *service) SendWeeklyDigest(workspaceID, tenantID string) error {
	ctx := context.Background()
	subject := "GeoLens Haftalık Özet — " + time.Now().Format("02.01.2006")

	// 1. Marka skorlarını ve trend verilerini çek
	brands, err := s.loadDigestScores(ctx, workspaceID, tenantID)
	if err != nil {
		slog.Error("digest: skor yükleme hatası", "error", err)
		// Skor yoksa bile boş özet gönder
		brands = []digestBrandScore{}
	}

	// 2. Önerileri çek
	recs, err := s.loadDigestRecommendations(ctx, workspaceID, tenantID)
	if err != nil {
		slog.Error("digest: öneri yükleme hatası", "error", err)
		recs = []digestRecommendation{}
	}

	// 3. HTML oluştur
	htmlContent := s.buildDigestHTML(subject, brands, recs, workspaceID, tenantID)

	// 4. Alıcı e-posta adresini ayarlardan al
	settings, err := s.GetSettings(ctx, workspaceID, tenantID)
	if err != nil {
		slog.Warn("digest: ayarlar okunamadı, varsayılan e-posta kullanılacak", "error", err)
	}

	toEmail := settings.EmailAddress
	if toEmail == "" {
		toEmail = "user@example.com"
	}

	return s.SendEmail(toEmail, subject, htmlContent)
}

// loadDigestScores loads brand scores with previous values for the digest.
func (s *service) loadDigestScores(ctx context.Context, workspaceID, tenantID string) ([]digestBrandScore, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT DISTINCT ON (b.id)
			b.id AS brand_id,
			b.name AS brand_name,
			s.value AS score,
			LAG(s.value) OVER (PARTITION BY b.id ORDER BY s.freshness_at) AS prev_score
		FROM config.brands b
		JOIN measure.scores s ON s.brand_id = b.id
		WHERE b.workspace_id = $1 AND b.tenant_id = $2 AND b.is_active = true
		ORDER BY b.id, s.freshness_at DESC
	`, workspaceID, tenantID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var brands []digestBrandScore
	for rows.Next() {
		var b digestBrandScore
		var prevScore *float64
		if err := rows.Scan(&b.BrandID, &b.BrandName, &b.Score, &prevScore); err != nil {
			slog.Warn("digest: skor satır okuma hatası", "error", err)
			continue
		}
		if prevScore != nil {
			b.PreviousScore = *prevScore
			b.Change = b.Score - b.PreviousScore
		}
		brands = append(brands, b)
	}
	return brands, rows.Err()
}

// loadDigestRecommendations loads the latest recommendations for the digest.
func (s *service) loadDigestRecommendations(ctx context.Context, workspaceID, tenantID string) ([]digestRecommendation, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT b.name, r.title, r.detail
		FROM recommendation.results r
		JOIN config.brands b ON b.id = r.brand_id
		WHERE r.workspace_id = $1 AND r.tenant_id = $2
		ORDER BY r.created_at DESC
		LIMIT 5
	`, workspaceID, tenantID)
	if err != nil {
		// Tablo henüz yoksa boş dön
		slog.Debug("digest: recommendation.results tablosu henüz oluşturulmamış olabilir", "error", err)
		return nil, nil
	}
	defer rows.Close()

	var recs []digestRecommendation
	for rows.Next() {
		var r digestRecommendation
		if err := rows.Scan(&r.BrandName, &r.Title, &r.Detail); err != nil {
			slog.Warn("digest: öneri satır okuma hatası", "error", err)
			continue
		}
		recs = append(recs, r)
	}
	return recs, rows.Err()
}

// buildDigestHTML constructs the HTML email content for the weekly digest.
func (s *service) buildDigestHTML(subject string, brands []digestBrandScore, recs []digestRecommendation, workspaceID, tenantID string) string {
	// Dashboard derin bağlantısı
	dashboardURL := fmt.Sprintf("https://app.geolens.ai/v1/workspaces/%s/dashboard", workspaceID)

	html := `<!DOCTYPE html>
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
.change-neutral { color: #94a3b8; }
.rec-item { padding: 8px 0; border-bottom: 1px solid #f1f5f9; font-size: 14px; color: #475569; }
.rec-item:last-child { border-bottom: none; }
.rec-brand { font-weight: 600; color: #6366f1; }
.footer { text-align: center; padding: 20px; font-size: 12px; color: #94a3b8; }
.btn { display: inline-block; padding: 10px 20px; background: #6366f1; color: white; text-decoration: none; border-radius: 8px; font-size: 14px; margin-top: 12px; }
.empty-state { text-align: center; padding: 20px; color: #94a3b8; font-size: 14px; }
</style></head>
<body>
<div class="container">
  <div class="header">
    <h1>GeoLens Haftalık Özet</h1>
    <p>` + time.Now().Format("02.01.2006") + `</p>
  </div>
  <div class="section">
    <h2>📊 Görünürlük Skorları</h2>`

	if len(brands) == 0 {
		html += `<div class="empty-state">Henüz ölçüm yapılmamış. İlk ölçümünüzü başlatmak için panoya gidin.</div>`
	} else {
		for _, b := range brands {
			changeHTML := ""
			//nolint:gocritic
			//nolint:gocritic
			if b.Change > 0 {
				changeHTML = fmt.Sprintf(`<span class="change-up">↑%.0f</span>`, b.Change)
			} else if b.Change < 0 {
				changeHTML = fmt.Sprintf(`<span class="change-down">↓%.0f</span>`, -b.Change)
			} else {
				changeHTML = `<span class="change-neutral">—</span>`
			}

			brandURL := fmt.Sprintf("%s?brand=%s", dashboardURL, b.BrandID)
			html += fmt.Sprintf(`
    <div class="score-row">
      <a href="%s" style="text-decoration:none;color:inherit;"><span class="brand-name">%s</span></a>
      <span><span class="score-value">%.0f</span> %s</span>
    </div>`, brandURL, escapeHTML(b.BrandName), b.Score, changeHTML)
		}
	}

	html += fmt.Sprintf(`
    <a href="%s" class="btn">Panoda Görüntüle</a>
  </div>
  <div class="section">
    <h2>💡 Öneriler</h2>`, dashboardURL)

	if len(recs) == 0 {
		html += `<div class="empty-state">Henüz öneri bulunmuyor.</div>`
	} else {
		for _, r := range recs {
			html += fmt.Sprintf(`
    <div class="rec-item"><span class="rec-brand">%s:</span> %s</div>`, escapeHTML(r.BrandName), escapeHTML(r.Detail))
		}
	}

	html += `
  </div>
  <div class="footer">
    Bu e-posta GeoLens AI Visibility Platform tarafından otomatik gönderilmiştir.<br>
    <a href="` + dashboardURL + `" style="color:#6366f1;">Panoya Git</a>
  </div>
</div>
</body>
</html>`

	return html
}

// escapeHTML escapes special HTML characters in a string.
// HTML entity'leri Go string literal'ine gömülü olarak değil,
// parça parça birleştirilerek oluşturulur (oto-formatlamanın bozmaması için).
func escapeHTML(s string) string {
	var result []byte
	for i := 0; i < len(s); i++ {
		switch s[i] {
		case '&':
			result = append(result, '&')
			result = append(result, 'a')
			result = append(result, 'm')
			result = append(result, 'p')
			result = append(result, ';')
		case '<':
			result = append(result, '&')
			result = append(result, 'l')
			result = append(result, 't')
			result = append(result, ';')
		case '>':
			result = append(result, '&')
			result = append(result, 'g')
			result = append(result, 't')
			result = append(result, ';')
		case '"':
			result = append(result, '&')
			result = append(result, 'q')
			result = append(result, 'u')
			result = append(result, 'o')
			result = append(result, 't')
			result = append(result, ';')
		case '\'':
			result = append(result, '&')
			result = append(result, '#')
			result = append(result, '3')
			result = append(result, '9')
			result = append(result, ';')
		default:
			result = append(result, s[i])
		}
	}
	return string(result)
}

// GetSettings returns the notification settings for a workspace.
// Reads from PostgreSQL; returns defaults if no record exists.
func (s *service) GetSettings(ctx context.Context, workspaceID, tenantID string) (*NotificationSettings, error) {
	settings := &NotificationSettings{WorkspaceID: workspaceID}

	err := s.pool.QueryRow(ctx, `
		SELECT email_address, digest_enabled, digest_day, digest_time,
		       digest_format, notify_on_drop, drop_threshold,
		       webhook_url, webhook_kind, webhook_active
		FROM delivery.notification_settings
		WHERE workspace_id = $1 AND tenant_id = $2
	`, workspaceID, tenantID).Scan(
		&settings.EmailAddress, &settings.DigestEnabled, &settings.DigestDay,
		&settings.DigestTime, &settings.DigestFormat,
		&settings.NotifyOnDrop, &settings.DropThreshold,
		&settings.WebhookURL, &settings.WebhookKind, &settings.WebhookActive,
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
func (s *service) UpdateSettings(ctx context.Context, settings *NotificationSettings, tenantID string) error {
	if err := ValidateSettings(settings); err != nil {
		return err
	}

	_, err := s.pool.Exec(ctx, `
		INSERT INTO delivery.notification_settings
			(workspace_id, tenant_id, email_address, digest_enabled, digest_day,
			 digest_time, digest_format, notify_on_drop, drop_threshold,
			 webhook_url, webhook_kind, webhook_active, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, now())
		ON CONFLICT (workspace_id, tenant_id) DO UPDATE SET
			email_address = EXCLUDED.email_address,
			digest_enabled = EXCLUDED.digest_enabled,
			digest_day = EXCLUDED.digest_day,
			digest_time = EXCLUDED.digest_time,
			digest_format = EXCLUDED.digest_format,
			notify_on_drop = EXCLUDED.notify_on_drop,
			drop_threshold = EXCLUDED.drop_threshold,
			webhook_url = EXCLUDED.webhook_url,
			webhook_kind = EXCLUDED.webhook_kind,
			webhook_active = EXCLUDED.webhook_active,
			updated_at = now()
	`, settings.WorkspaceID, tenantID, settings.EmailAddress, settings.DigestEnabled,
		settings.DigestDay, settings.DigestTime, settings.DigestFormat,
		settings.NotifyOnDrop, settings.DropThreshold,
		settings.WebhookURL, settings.WebhookKind, settings.WebhookActive)

	if err != nil {
		return fmt.Errorf("delivery: ayarlar kaydedilemedi: %w", err)
	}

	slog.Info("notification settings saved to DB", "workspace", settings.WorkspaceID, "tenant", tenantID, "enabled", settings.DigestEnabled)
	return nil
}

// inAppExec executes on the test pool (mock) or production pool.
func (s *service) inAppExec(ctx context.Context, sql string, args ...any) error {
	if s.testPool != nil {
		_, err := s.testPool.Exec(ctx, sql, args...)
		return err
	}
	_, err := s.pool.Exec(ctx, sql, args...)
	return err
}

// inAppQuery runs a query on the test pool (mock) or production pool.
func (s *service) inAppQuery(ctx context.Context, sql string, args ...any) (dbiface.RowsIter, error) {
	if s.testPool != nil {
		return s.testPool.Query(ctx, sql, args...)
	}
	return s.pool.Query(ctx, sql, args...)
}

// saveInAppNotification stores an in-app notification in delivery.notifications
// (FR-D10 in-app kanalı — önceden pasifti, yalnızca debug log yazıyordu).
// Bildirim, web UI'nin API üzerinden okuyabildiği kalıcı tabloya yazılır.
func (s *service) saveInAppNotification(notif *Notification) error {
	dataJSON := "{}"
	if notif.Data != nil {
		if b, err := json.Marshal(notif.Data); err == nil {
			dataJSON = string(b)
		}
	}

	ctx := context.Background()
	err := s.inAppExec(ctx, `
		INSERT INTO delivery.notifications (id, tenant_id, workspace_id, user_id, type, title, body, data, is_read, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, false, now())
	`, notif.ID, notif.TenantID, notif.WorkspaceID, notif.UserID,
		string(notif.Type), notif.Title, notif.Body, dataJSON)
	if err != nil {
		slog.Warn("in-app bildirim kaydedilemedi", "id", notif.ID, "error", err)
		return fmt.Errorf("delivery: in-app bildirim kaydedilemedi: %w", err)
	}

	now := time.Now()
	notif.Status = DeliverySent
	notif.SentAt = &now
	slog.Info("in-app bildirim kaydedildi", "id", notif.ID, "type", notif.Type)
	return nil
}

// ListInAppNotifications returns unread (or all) in-app notifications for a workspace.
func (s *service) ListInAppNotifications(ctx context.Context, tenantID, workspaceID string, unreadOnly bool, limit int) ([]Notification, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	query := `
		SELECT id, COALESCE(user_id, ''), type, title, body, data, is_read, created_at
		FROM delivery.notifications
		WHERE tenant_id = $1 AND workspace_id = $2`
	args := []interface{}{tenantID, workspaceID}
	if unreadOnly {
		query += ` AND is_read = false`
	}
	query += ` ORDER BY created_at DESC LIMIT $` + fmt.Sprint(len(args)+1)
	args = append(args, limit)

	rows, err := s.inAppQuery(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("delivery: in-app bildirim listesi: %w", err)
	}
	defer rows.Close()

	out := []Notification{}
	for rows.Next() {
		var n Notification
		var id, userID, typ, title, body string
		var dataJSON []byte
		var isRead bool
		var createdAt time.Time
		if err := rows.Scan(&id, &userID, &typ, &title, &body, &dataJSON, &isRead, &createdAt); err != nil {
			slog.Warn("in-app bildirim satırı okunamadı", "error", err)
			continue
		}
		n.ID = id
		n.UserID = userID
		n.Type = NotificationType(typ)
		n.Title = title
		n.Body = body
		n.IsRead = isRead
		n.CreatedAt = createdAt
		if len(dataJSON) > 0 && string(dataJSON) != "{}" {
			_ = json.Unmarshal(dataJSON, &n.Data)
		}
		out = append(out, n)
	}
	return out, rows.Err()
}

// MarkInAppNotificationRead marks a single in-app notification as read.
func (s *service) MarkInAppNotificationRead(ctx context.Context, tenantID, notificationID string) error {
	err := s.inAppExec(ctx, `
		UPDATE delivery.notifications SET is_read = true
		WHERE id = $1 AND tenant_id = $2
	`, notificationID, tenantID)
	if err != nil {
		return fmt.Errorf("delivery: in-app bildirim okundu işaretlenemedi: %w", err)
	}
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
