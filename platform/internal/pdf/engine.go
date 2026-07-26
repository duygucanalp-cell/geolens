// Package internal/pdf provides internal/pdf functionality.
package pdf

import (
	"context"
	"time"
)

// ---- Domain Types ----

// ReportType represents the type of PDF report.
type ReportType string

const (
	ReportScoreCard    ReportType = "score_card"
	ReportWeeklyDigest ReportType = "weekly_digest"
	ReportAudit        ReportType = "audit"
)

// ReportRequest represents a request to generate a PDF report.
type ReportRequest struct {
	Type        ReportType `json:"type"`
	WorkspaceID string     `json:"workspace_id"`
	TenantID    string     `json:"tenant_id"`
	BrandID     string     `json:"brand_id,omitempty"`
	BrandName   string     `json:"brand_name,omitempty"`
	DateFrom    time.Time  `json:"date_from,omitempty"`
	DateTo      time.Time  `json:"date_to,omitempty"`
}

// ReportResult contains the generated PDF data.
type ReportResult struct {
	ID          string     `json:"id"`
	Type        ReportType `json:"type"`
	Data        []byte     `json:"-"` // Raw PDF bytes
	FileName    string     `json:"file_name"`
	PageCount   int        `json:"page_count"`
	GeneratedAt time.Time  `json:"generated_at"`
	S3Ref       string     `json:"s3_ref,omitempty"`
}

// ScoreRow represents a single score entry for PDF tables.
type ScoreRow struct {
	BrandName     string  `json:"brand_name"`
	Score         float64 `json:"score"`
	PreviousScore float64 `json:"previous_score,omitempty"`
	Change        float64 `json:"change"`
	FidelityLabel string  `json:"fidelity_label"`
}

// AuditRow represents audit data for PDF reports.
type AuditRow struct {
	Category       string  `json:"category"`
	Status         string  `json:"status"`
	Score          float64 `json:"score"`
	Recommendation string  `json:"recommendation,omitempty"`
}

// ---- Service Interface ----

// Service defines the interface for PDF report generation.
type Service interface {
	// Generate creates a PDF report based on the request.
	Generate(req ReportRequest) (*ReportResult, error)

	// GenerateWeeklyDigest creates a weekly digest PDF with scores, trends, and recommendations.
	GenerateWeeklyDigest(workspaceID, tenantID string) (*ReportResult, error)

	// GetReportData retrieves generated report data by ID.
	GetReportData(ctx context.Context, reportID string) ([]byte, error)
}
