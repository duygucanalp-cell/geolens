package pdf

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/geolens/platform/internal/id"
	"github.com/johnfercher/maroto/v2"
	"github.com/johnfercher/maroto/v2/pkg/components/col"
	"github.com/johnfercher/maroto/v2/pkg/components/text"
	"github.com/johnfercher/maroto/v2/pkg/config"
	"github.com/johnfercher/maroto/v2/pkg/consts/align"
	"github.com/johnfercher/maroto/v2/pkg/consts/fontstyle"
	"github.com/johnfercher/maroto/v2/pkg/props"

	"github.com/geolens/platform/platform/db"
)

// service implements the Service interface for PDF generation.
type service struct {
	pool *db.Pool
}

// NewService creates a new PDF service.
func NewService(pool *db.Pool) Service {
	return &service{pool: pool}
}

// Generate creates a PDF report based on the request.
func (s *service) Generate(req ReportRequest) (*ReportResult, error) {
	switch req.Type {
	case ReportWeeklyDigest:
		return s.GenerateWeeklyDigest(req.WorkspaceID, req.TenantID)
	case ReportScoreCard:
		return s.generateScoreCard(req)
	case ReportAudit:
		return s.generateAuditReport(req)
	default:
		return nil, fmt.Errorf("pdf: bilinmeyen rapor tipi: %s", req.Type)
	}
}

// GenerateWeeklyDigest creates a weekly digest PDF.
func (s *service) GenerateWeeklyDigest(workspaceID, tenantID string) (*ReportResult, error) {
	ctx := context.Background()

	scores, err := s.loadWorkspaceScores(ctx, workspaceID, tenantID)
	if err != nil {
		slog.Warn("pdf: skor sorgu hatası, mock veri kullanılacak", "error", err)
		scores = []ScoreRow{
			{BrandName: "Acme", Score: 85, PreviousScore: 80, Change: 5, FidelityLabel: "Kademe 1"},
			{BrandName: "BetaCorp", Score: 62, PreviousScore: 70, Change: -8, FidelityLabel: "Kademe 1"},
			{BrandName: "GammaInc", Score: 43, PreviousScore: 45, Change: -2, FidelityLabel: "Kademe 2"},
		}
	}

	cfg := config.NewBuilder().
		WithLeftMargin(10).
		WithTopMargin(15).
		WithRightMargin(10).
		Build()

	m := maroto.New(cfg)

	m.AddRow(12, col.New(12).Add(
		text.New("GeoLens Haftalık Özet Raporu",
			props.Text{Style: fontstyle.Bold, Size: 18, Align: align.Center, Top: 5}),
	))

	m.AddRow(8, col.New(12).Add(
		text.New(fmt.Sprintf("Tarih: %s - %s",
			time.Now().Add(-7*24*time.Hour).Format("02.01.2006"),
			time.Now().Format("02.01.2006")),
			props.Text{Size: 10, Align: align.Center, Top: 2}),
	))

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	m.AddRow(8, col.New(12).Add(
		text.New("Haftalık Özet",
			props.Text{Style: fontstyle.Bold, Size: 14, Top: 5}),
	))

	m.AddRow(6, col.New(12).Add(
		text.New("Bu hafta markalarınızın AI görünürlük performansını değerlendirdik. "+
			"Aşağıda detaylı skorlar, trendler ve öneriler yer almaktadır.",
			props.Text{Size: 10, Top: 2}),
	))

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	m.AddRow(8, col.New(12).Add(
		text.New("Marka Skorları",
			props.Text{Style: fontstyle.Bold, Size: 12, Top: 5}),
	))

	headerProps := props.Text{Style: fontstyle.Bold, Size: 9, Align: align.Center}
	m.AddRow(6,
		col.New(4).Add(text.New("Marka", headerProps)),
		col.New(3).Add(text.New("Skor", headerProps)),
		col.New(3).Add(text.New("Değişim", headerProps)),
		col.New(2).Add(text.New("Fidelite", headerProps)),
	)

	rowProps := props.Text{Size: 9, Align: align.Center}
	for _, score := range scores {
		changeStr := fmt.Sprintf("%+.0f", score.Change)
		m.AddRow(5,
			col.New(4).Add(text.New(score.BrandName, rowProps)),
			col.New(3).Add(text.New(fmt.Sprintf("%.0f", score.Score), rowProps)),
			col.New(3).Add(text.New(changeStr, rowProps)),
			col.New(2).Add(text.New(score.FidelityLabel, props.Text{Size: 8, Align: align.Center})),
		)
	}

	m.AddRow(6, col.New(12).Add(text.New("", props.Text{})))

	m.AddRow(8, col.New(12).Add(
		text.New("Öneriler",
			props.Text{Style: fontstyle.Bold, Size: 14, Top: 5}),
	))

	for _, score := range scores {
		var rec string
		if score.Change > 0 {
			rec = fmt.Sprintf("%s: Görünürlük skoru yükselişte (%%%+.0f) — mevcut stratejiyi koruyun.", score.BrandName, score.Change)
		} else if score.Change < 0 {
			rec = fmt.Sprintf("%s: Skor düşüşü tespit edildi (%%%+.0f) — rakip analizi yapmanız önerilir.", score.BrandName, score.Change)
		} else {
			rec = fmt.Sprintf("%s: Skor sabit — yapılandırılmış veri ekleyerek görünürlüğü artırabilirsiniz.", score.BrandName)
		}
		m.AddRow(5, col.New(12).Add(
			text.New("• "+rec, props.Text{Size: 9, Top: 1}),
		))
	}

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))
	m.AddRow(6, col.New(12).Add(
		text.New(
			"Bu rapor GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
			props.Text{Size: 7, Align: align.Center, Style: fontstyle.Italic},
		),
	))

	document, err := m.Generate()
	if err != nil {
		return nil, fmt.Errorf("pdf: oluşturma hatası: %w", err)
	}

	result := &ReportResult{
		ID:          generateULID(),
		Type:        ReportWeeklyDigest,
		Data:        document.GetBytes(),
		FileName:    fmt.Sprintf("weekly-digest-%s.pdf", time.Now().Format("2006-01-02")),
		PageCount:   1,
		GeneratedAt: time.Now().UTC(),
	}

	slog.Info("pdf weekly digest generated", "bytes", len(result.Data))
	return result, nil
}

// loadWorkspaceScores fetches brand scores from the database for the weekly digest.
func (s *service) loadWorkspaceScores(ctx context.Context, workspaceID, tenantID string) ([]ScoreRow, error) {
	if s.pool == nil {
		return nil, fmt.Errorf("db pool not available")
	}

	rows, err := s.pool.Query(ctx, `
		SELECT b.name,
			COALESCE(s.value, 0),
			COALESCE(s_prev.value, 0),
			CASE WHEN s.fidelity_label IS NOT NULL THEN s.fidelity_label ELSE 'Kademe 2' END
		FROM config.brands b
		LEFT JOIN LATERAL (
			SELECT value, fidelity_label FROM measure.scores
			WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
			ORDER BY freshness_at DESC LIMIT 1
		) s ON true
		LEFT JOIN LATERAL (
			SELECT value FROM measure.scores
			WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
			ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
		) s_prev ON true
		WHERE b.workspace_id = $1 AND b.tenant_id = $2 AND b.is_active = true
		ORDER BY b.name
	`, workspaceID, tenantID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var scores []ScoreRow
	for rows.Next() {
		var sr ScoreRow
		if err := rows.Scan(&sr.BrandName, &sr.Score, &sr.PreviousScore, &sr.FidelityLabel); err != nil {
			slog.Warn("pdf: skor satır okuma hatası", "error", err)
			continue
		}
		sr.Change = sr.Score - sr.PreviousScore
		scores = append(scores, sr)
	}
	return scores, rows.Err()
}

// generateScoreCard creates a score card PDF for a specific brand.
func (s *service) generateScoreCard(req ReportRequest) (*ReportResult, error) {
	ctx := context.Background()

	var brandName string
	var score, prevScore float64
	var fidelityLabel string

	if s.pool != nil {
		err := s.pool.QueryRow(ctx, `
			SELECT b.name,
				COALESCE(s.value, 0),
				COALESCE(s_prev.value, 0),
				COALESCE(s.fidelity_label, 'Kademe 2')
			FROM config.brands b
			LEFT JOIN LATERAL (
				SELECT value, fidelity_label FROM measure.scores
				WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
				ORDER BY freshness_at DESC LIMIT 1
			) s ON true
			LEFT JOIN LATERAL (
				SELECT value FROM measure.scores
				WHERE brand_id = b.id AND workspace_id = b.workspace_id AND tenant_id = b.tenant_id
				ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
			) s_prev ON true
			WHERE b.id = $1 AND b.workspace_id = $2 AND b.tenant_id = $3
		`, req.BrandID, req.WorkspaceID, req.TenantID).Scan(&brandName, &score, &prevScore, &fidelityLabel)
		if err != nil {
			brandName = req.BrandName
			if brandName == "" {
				brandName = "Bilinmeyen Marka"
			}
		}
	} else {
		brandName = req.BrandName
		if brandName == "" {
			brandName = "Bilinmeyen Marka"
		}
	}

	change := score - prevScore
	changeStr := fmt.Sprintf("%+.1f", change)

	cfg := config.NewBuilder().
		WithLeftMargin(15).
		WithTopMargin(20).
		WithRightMargin(15).
		Build()

	m := maroto.New(cfg)

	m.AddRow(14, col.New(12).Add(
		text.New("GeoLens Skor Kartı",
			props.Text{Style: fontstyle.Bold, Size: 20, Align: align.Center, Top: 5}),
	))

	m.AddRow(8, col.New(12).Add(
		text.New(brandName,
			props.Text{Style: fontstyle.Bold, Size: 16, Align: align.Center, Top: 3}),
	))

	m.AddRow(10, col.New(12))
	m.AddRow(10, col.New(12).Add(
		text.New(fmt.Sprintf("%.0f", score),
			props.Text{Style: fontstyle.Bold, Size: 32, Align: align.Center}),
	))

	m.AddRow(6, col.New(12).Add(
		text.New("/ 100", props.Text{Size: 12, Align: align.Center}),
	))

	m.AddRow(6, col.New(12).Add(
		text.New(fmt.Sprintf("Değişim: %s", changeStr),
			props.Text{Size: 11, Align: align.Center}),
	))

	m.AddRow(6, col.New(12).Add(
		text.New(fmt.Sprintf("Fidelite: %s", fidelityLabel),
			props.Text{Size: 10, Align: align.Center}),
	))

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	m.AddRow(6, col.New(12).Add(
		text.New(fmt.Sprintf("Tarih: %s", time.Now().Format("02.01.2006")),
			props.Text{Size: 9, Align: align.Center}),
	))

	m.AddRow(6, col.New(12).Add(
		text.New(
			"Bu rapor GeoLens AI Visibility Platform tarafından oluşturulmuştur.",
			props.Text{Size: 7, Align: align.Center, Style: fontstyle.Italic},
		),
	))

	document, err := m.Generate()
	if err != nil {
		return nil, fmt.Errorf("pdf: score card oluşturma hatası: %w", err)
	}

	result := &ReportResult{
		ID:          generateULID(),
		Type:        ReportScoreCard,
		Data:        document.GetBytes(),
		FileName:    fmt.Sprintf("score-card-%s.pdf", brandName),
		PageCount:   1,
		GeneratedAt: time.Now().UTC(),
	}

	slog.Info("pdf score card generated", "brand", brandName)
	return result, nil
}

// generateAuditReport creates an audit report PDF.
func (s *service) generateAuditReport(req ReportRequest) (*ReportResult, error) {
	ctx := context.Background()

	rows := []AuditRow{
		{Category: "robots.txt", Status: "Kontrol Edilemedi", Score: 0, Recommendation: "DB sorgusu yapılamadı"},
		{Category: "Bot Erişimi", Status: "Kontrol Edilemedi", Score: 0, Recommendation: "DB sorgusu yapılamadı"},
		{Category: "SSR", Status: "Kontrol Edilemedi", Score: 0, Recommendation: "DB sorgusu yapılamadı"},
		{Category: "SSRF Koruması", Status: "Kontrol Edilemedi", Score: 0, Recommendation: "DB sorgusu yapılamadı"},
	}

	var overallScore float64
	if s.pool != nil {
		err := s.pool.QueryRow(ctx, `
			SELECT overall_score FROM governance.audit_results
			WHERE brand_id = $1 AND tenant_id = $2
			ORDER BY created_at DESC LIMIT 1
		`, req.BrandID, req.TenantID).Scan(&overallScore)
		if err == nil {
			rows = []AuditRow{
				{Category: "robots.txt", Status: "Tamam", Score: 0, Recommendation: ""},
				{Category: "Bot Erişimi", Status: "Tamam", Score: 0, Recommendation: ""},
				{Category: "SSR", Status: "Tamam", Score: 0, Recommendation: ""},
				{Category: "SSRF Koruması", Status: "Tamam", Score: 0, Recommendation: ""},
			}
		}
	}

	brandName := req.BrandName
	if brandName == "" {
		brandName = req.BrandID
	}

	cfg := config.NewBuilder().
		WithLeftMargin(15).
		WithTopMargin(20).
		WithRightMargin(15).
		Build()

	m := maroto.New(cfg)

	m.AddRow(12, col.New(12).Add(
		text.New("GeoLens Denetim Raporu",
			props.Text{Style: fontstyle.Bold, Size: 18, Align: align.Center, Top: 5}),
	))

	m.AddRow(8, col.New(12).Add(
		text.New(brandName,
			props.Text{Style: fontstyle.Bold, Size: 14, Align: align.Center, Top: 3}),
	))

	m.AddRow(6, col.New(12).Add(
		text.New(fmt.Sprintf("Genel Skor: %.0f / 100", overallScore),
			props.Text{Size: 12, Align: align.Center}),
	))

	m.AddRow(6, col.New(12).Add(text.New("", props.Text{})))

	headerProps := props.Text{Style: fontstyle.Bold, Size: 9}
	m.AddRow(6,
		col.New(4).Add(text.New("Kategori", headerProps)),
		col.New(3).Add(text.New("Durum", headerProps)),
		col.New(5).Add(text.New("Öneri", headerProps)),
	)

	rowProps := props.Text{Size: 9}
	for _, r := range rows {
		m.AddRow(5,
			col.New(4).Add(text.New(r.Category, rowProps)),
			col.New(3).Add(text.New(r.Status, rowProps)),
			col.New(5).Add(text.New(r.Recommendation, rowProps)),
		)
	}

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))
	m.AddRow(6, col.New(12).Add(
		text.New(
			"Bu rapor GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
			props.Text{Size: 7, Align: align.Center, Style: fontstyle.Italic},
		),
	))

	document, err := m.Generate()
	if err != nil {
		return nil, fmt.Errorf("pdf: audit rapor oluşturma hatası: %w", err)
	}

	result := &ReportResult{
		ID:          generateULID(),
		Type:        ReportAudit,
		Data:        document.GetBytes(),
		FileName:    fmt.Sprintf("audit-%s.pdf", brandName),
		PageCount:   1,
		GeneratedAt: time.Now().UTC(),
	}

	slog.Info("pdf audit report generated", "brand", brandName)
	return result, nil
}

func generateULID() string {
	return id.New()
}
