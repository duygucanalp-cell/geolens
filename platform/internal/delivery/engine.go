package delivery

import "time"

// ---- Domain Types ----

// Channel represents a delivery channel (email, in-app, webhook).
type Channel string

const (
	ChannelEmail Channel = "email"
	ChannelInApp Channel = "in_app"
)

// NotificationType represents the type of notification.
type NotificationType string

const (
	NotificationScoreDrop     NotificationType = "score_drop"
	NotificationWeeklyDigest  NotificationType = "weekly_digest"
	NotificationNewSuggestion NotificationType = "new_suggestion"
	NotificationAuditComplete NotificationType = "audit_complete"
)

// DeliveryStatus represents the status of a delivery attempt.
type DeliveryStatus string

const (
	DeliveryPending   DeliveryStatus = "pending"
	DeliverySent      DeliveryStatus = "sent"
	DeliveryFailed    DeliveryStatus = "failed"
	DeliveryDelivered DeliveryStatus = "delivered"
)

// Notification represents a single notification to be delivered.
type Notification struct {
	ID          string                 `json:"id"`
	TenantID    string                 `json:"tenant_id"`
	UserID      string                 `json:"user_id"`
	WorkspaceID string                 `json:"workspace_id"`
	Type        NotificationType       `json:"type"`
	Channel     Channel                `json:"channel"`
	Title       string                 `json:"title"`
	Body        string                 `json:"body"`
	HTMLBody    string                 `json:"html_body,omitempty"`
	Data        map[string]interface{} `json:"data,omitempty"`
	Status      DeliveryStatus         `json:"status"`
	SentAt      *time.Time             `json:"sent_at,omitempty"`
	CreatedAt   time.Time              `json:"created_at"`
}

// EmailConfig holds the configuration for email delivery.
type EmailConfig struct {
	FromName    string `json:"from_name"`
	FromEmail   string `json:"from_email"`
	SendGridKey string `json:"-"` // SendGrid API key, not serialized
}

// ---- Service Interface ----

// Service defines the interface for the delivery system.
type Service interface {
	// SendNotification sends a single notification.
	SendNotification(notif Notification) error

	// SendEmail sends an email using SendGrid.
	SendEmail(to, subject, htmlContent string) error

	// SendWeeklyDigest sends a weekly digest email for a workspace.
	SendWeeklyDigest(workspaceID, tenantID string) error
}
