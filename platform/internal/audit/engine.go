package audit

import (
	"time"
)

// ---- Domain Types ----

// AuditResult represents the complete result of a site audit.
type AuditResult struct {
	ID             string          `json:"id"`
	BrandID        string          `json:"brand_id"`
	BrandName      string          `json:"brand_name"`
	WebsiteURL     string          `json:"website_url"`
	WorkspaceID    string          `json:"workspace_id"`
	TenantID       string          `json:"tenant_id"`
	RobotsTxtCheck *RobotsTxtCheck `json:"robots_txt,omitempty"`
	BotAccessCheck *BotAccessCheck `json:"bot_access,omitempty"`
	SSRCheck       *SSRCheck       `json:"ssr,omitempty"`
	SSRFCheck      *SSRFCheck      `json:"ssrf,omitempty"`
	OverallScore   float64         `json:"overall_score"`
	Issues         []Issue         `json:"issues,omitempty"`
	CreatedAt      time.Time       `json:"created_at"`
}

// RobotsTxtCheck checks if robots.txt allows AI bots.
type RobotsTxtCheck struct {
	Exists        bool     `json:"exists"`
	AllowsAIBots  bool     `json:"allows_ai_bots"`
	BlockedPaths  []string `json:"blocked_paths,omitempty"`
	DisallowedAll bool     `json:"disallowed_all"`
}

// BotAccessCheck checks if the site is accessible to known AI user agents.
type BotAccessCheck struct {
	Accessible       bool     `json:"accessible"`
	StatusCode       int      `json:"status_code"`
	ResponseTimeMs   int64    `json:"response_time_ms"`
	AIBotNamesTested []string `json:"ai_bot_names_tested,omitempty"`
}

// SSRCheck checks server-side rendering signals.
type SSRCheck struct {
	HasMetaTags       bool `json:"has_meta_tags"`
	HasOGTags         bool `json:"has_og_tags"`
	HasStructuredData bool `json:"has_structured_data"`
	ContentLength     int  `json:"content_length"`
}

// SSRFCheck checks SSRF protection headers.
type SSRFCheck struct {
	HasCloudflare         bool `json:"has_cloudflare"`
	HasAWSSecurityHeaders bool `json:"has_aws_security_headers"`
	HasRateLimitHeaders   bool `json:"has_rate_limit_headers"`
	CSPPresent            bool `json:"csp_present"`
}

// Issue represents a single audit finding.
type Issue struct {
	Severity       string `json:"severity"` // critical, high, medium, low, info
	Category       string `json:"category"` // robots, bot_access, ssr, ssrf
	Title          string `json:"title"`
	Detail         string `json:"detail"`
	Recommendation string `json:"recommendation,omitempty"`
}

// ---- Service Interface ----

// Service defines the interface for site auditing.
type Service interface {
	// Audit performs a complete site audit for the given brand.
	Audit(brandID, brandName, websiteURL string) (*AuditResult, error)
}

// AICrawler represents a known AI crawler/bot information.
type AICrawler struct {
	UserAgent string `json:"user_agent"`
	Name      string `json:"name"`
	Source    string `json:"source"` // "chatgpt", "gemini", "perplexity"
}
