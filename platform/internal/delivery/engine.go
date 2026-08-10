package delivery

import (
	"context"
	"fmt"
	"time"
)

// ---- Domain Types ----

// Channel represents a delivery channel (email, in-app, webhook).
type Channel string

const (
	ChannelEmail   Channel = "email"
	ChannelInApp   Channel = "in_app"
	ChannelWebhook Channel = "webhook"
)

// WebhookKind describes the webhook destination format.
// HT2 ticari olgunlaştırma: Slack/e-posta dışında Teams, Discord, PagerDuty, custom webhook.
type WebhookKind string

const (
	WebhookKindGeneric   WebhookKind = "generic"
	WebhookKindSlack     WebhookKind = "slack"
	WebhookKindTeams     WebhookKind = "teams"
	WebhookKindDiscord   WebhookKind = "discord"
	WebhookKindPagerDuty WebhookKind = "pagerduty"
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

	// Webhook alanları (HT2 webhook çeşitlendirme — Teams/Discord/PagerDuty/custom).
	// Channel = webhook iken URL zorunludur.
	WebhookURL  string      `json:"webhook_url,omitempty"`
	WebhookKind WebhookKind `json:"webhook_kind,omitempty"`
}

// EmailConfig holds the configuration for email delivery.
type EmailConfig struct {
	FromName    string `json:"from_name"`
	FromEmail   string `json:"from_email"`
	SendGridKey string `json:"-"` // SendGrid API key, not serialized
}

// NotificationSettings represents email digest preferences for a workspace.
type NotificationSettings struct {
	WorkspaceID   string `json:"workspace_id"`
	EmailAddress  string `json:"email_address"`
	DigestEnabled bool   `json:"digest_enabled"`
	DigestDay     string `json:"digest_day"`     // monday, tuesday, ..., sunday
	DigestTime    string `json:"digest_time"`    // HH:mm format
	DigestFormat  string `json:"digest_format"`  // email, pdf, both
	NotifyOnDrop  bool   `json:"notify_on_drop"` // score drop notification
	DropThreshold int    `json:"drop_threshold"` // % drop to trigger notification

	// Webhook kanalı ayarları (HT2 webhook çeşitlendirme).
	// webhook_webhook_url set edildiğinde bildirimler workspace'e webhook üzerinden gider.
	WebhookURL    string `json:"webhook_url,omitempty"`
	WebhookKind   string `json:"webhook_kind,omitempty"` // generic, slack, teams, discord, pagerduty
	WebhookActive bool   `json:"webhook_active,omitempty"`
}

// ValidDays lists all acceptable digest_day values.
var ValidDays = []string{"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"}

// validDigestFormats lists all acceptable digest_format values.
var ValidDigestFormats = []string{"email", "pdf", "both"}

// validationError is a marker type returned by ValidateSettings.
type validationError struct {
	msg string
}

func (e *validationError) Error() string { return e.msg }

// ValidateSettings checks that a NotificationSettings struct has valid values.
// Returns a user-facing error message if invalid, or nil if valid.
func ValidateSettings(s *NotificationSettings) error {
	if s.EmailAddress == "" {
		return &validationError{msg: "e-posta adresi gerekli"}
	}

	// Validate digest_day (required)
	if s.DigestDay == "" {
		return &validationError{msg: "gün gerekli (monday-sunday)"}
	}
	valid := false
	for _, d := range ValidDays {
		if s.DigestDay == d {
			valid = true
			break
		}
	}
	if !valid {
		return &validationError{msg: fmt.Sprintf("geçersiz gün: %s (pazartesi-pazar arası olmalı, İngilizce)", s.DigestDay)}
	}

	// Validate digest_time (HH:mm 24h format, required)
	if s.DigestTime == "" {
		return &validationError{msg: "saat gerekli (HH:mm)"}
	}
	if len(s.DigestTime) != 5 || s.DigestTime[2] != ':' {
		return &validationError{msg: fmt.Sprintf("geçersiz saat: %s (HH:mm formatında olmalı, örn: 09:00)", s.DigestTime)}
	}
	h := int(s.DigestTime[0]-'0')*10 + int(s.DigestTime[1]-'0')
	m := int(s.DigestTime[3]-'0')*10 + int(s.DigestTime[4]-'0')
	if h < 0 || h > 23 || m < 0 || m > 59 {
		return &validationError{msg: fmt.Sprintf("geçersiz saat: %s (saat 00-23, dakika 00-59 arası olmalı)", s.DigestTime)}
	}

	// Validate digest_format (required)
	if s.DigestFormat == "" {
		return &validationError{msg: "format gerekli (email, pdf veya both)"}
	}
	valid = false
	for _, f := range ValidDigestFormats {
		if s.DigestFormat == f {
			valid = true
			break
		}
	}
	if !valid {
		return &validationError{msg: fmt.Sprintf("geçersiz format: %s (email, pdf veya both olmalı)", s.DigestFormat)}
	}

	// Validate drop_threshold
	if s.DropThreshold < 1 || s.DropThreshold > 100 {
		return &validationError{msg: "düşüş eşiği 1-100 arası olmalı"}
	}

	// Validate webhook settings
	if s.WebhookActive {
		if s.WebhookURL == "" {
			return &validationError{msg: "webhook aktifken webhook URL'si gerekli"}
		}
		if s.WebhookKind == "" {
			return &validationError{msg: "webhook aktifken webhook türü gerekli (generic, slack, teams, discord, pagerduty)"}
		}
		valid = false
		for _, k := range []string{"generic", "slack", "teams", "discord", "pagerduty"} {
			if s.WebhookKind == k {
				valid = true
				break
			}
		}
		if !valid {
			return &validationError{msg: fmt.Sprintf("geçersiz webhook türü: %s (generic, slack, teams, discord veya pagerduty olmalı)", s.WebhookKind)}
		}
	}

	return nil
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

	// GetSettings returns the notification settings for a workspace.
	GetSettings(ctx context.Context, workspaceID, tenantID string) (*NotificationSettings, error)

	// UpdateSettings saves notification settings for a workspace using the given context.
	UpdateSettings(ctx context.Context, settings *NotificationSettings, tenantID string) error

	// SendGovernanceEvent forwards a Faz 4 governance event (guardrail, gate, incident,
	// drift, redteam) to all webhook-active workspaces of the tenant (O-6 bildirim tüketicisi).
	SendGovernanceEvent(ctx context.Context, tenantID, eventType string, payload map[string]interface{}) error
}
