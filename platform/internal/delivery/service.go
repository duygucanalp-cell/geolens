package delivery

import (
	"fmt"
	"log/slog"
	"time"

	"github.com/sendgrid/sendgrid-go"
	"github.com/sendgrid/sendgrid-go/helpers/mail"
)

// service implements the Service interface for delivery.
type service struct {
	config EmailConfig
}

// NewService creates a new delivery service.
func NewService(cfg EmailConfig) Service {
	return &service{config: cfg}
}

// SendNotification sends a single notification.
// For now, only email channel is supported.
func (s *service) SendNotification(notif Notification) error {
	switch notif.Channel {
	case ChannelEmail:
		return s.sendEmailNotification(notif)
	case ChannelInApp:
		// TODO(H10): In-app notification storage + polling
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
	// TODO(H10): Query scores for the workspace, generate digest HTML
	slog.Info("weekly digest (not yet implemented)", "workspace", workspaceID, "tenant", tenantID)
	return nil
}

// sendEmailNotification sends a notification as email.
func (s *service) sendEmailNotification(notif Notification) error {
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
