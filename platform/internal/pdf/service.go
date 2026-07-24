package pdf

import (
	"fmt"
	"log/slog"
	"time"

	"github.com/johnfercher/maroto/v2"
	"github.com/johnfercher/maroto/v2/pkg/components/col"
	"github.com/johnfercher/maroto/v2/pkg/components/text"
	"github.com/johnfercher/maroto/v2/pkg/config"
	"github.com/johnfercher/maroto/v2/pkg/consts/align"
	"github.com/johnfercher/maroto/v2/pkg/consts/fontstyle"
	"github.com/johnfercher/maroto/v2/pkg/props"
	"github.com/oklog/ulid/v2"
)

// service implements the Service interface for PDF generation.
type service struct{}

// NewService creates a new PDF service.
func NewService() Service {
	return &service{}
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
	cfg := config.NewBuilder().
		WithLeftMargin(10).
		WithTopMargin(15).
		WithRightMargin(10).
		Build()

	m := maroto.New(cfg)

	// ── Header ──
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

	// ── Özet bölümü ──
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

	// ── Skor tablosu ──
	m.AddRow(8, col.New(12).Add(
		text.New("Marka Skorları",
			props.Text{Style: fontstyle.Bold, Size: 12, Top: 5}),
	))

	// Tablo başlığı
	headerProps := props.Text{Style: fontstyle.Bold, Size: 9, Align: align.Center}
	m.AddRow(6,
		col.New(4).Add(text.New("Marka", headerProps)),
		col.New(3).Add(text.New("Skor", headerProps)),
		col.New(3).Add(text.New("Değişim", headerProps)),
		col.New(2).Add(text.New("Fidelite", headerProps)),
	)

	// Mock veri — H10'da DB sorgulama eklenecek
	mockScores := []ScoreRow{
		{BrandName: "Acme", Score: 85, PreviousScore: 80, Change: 5, FidelityLabel: "Kademe 1"},
		{BrandName: "BetaCorp", Score: 62, PreviousScore: 70, Change: -8, FidelityLabel: "Kademe 1"},
		{BrandName: "GammaInc", Score: 43, PreviousScore: 45, Change: -2, FidelityLabel: "Kademe 2"},
	}

	rowProps := props.Text{Size: 9, Align: align.Center}
	for _, score := range mockScores {
		changeStr := fmt.Sprintf("%+.0f", score.Change)
		m.AddRow(5,
			col.New(4).Add(text.New(score.BrandName, rowProps)),
			col.New(3).Add(text.New(fmt.Sprintf("%.0f", score.Score), rowProps)),
			col.New(3).Add(text.New(changeStr, rowProps)),
			col.New(2).Add(text.New(score.FidelityLabel, props.Text{Size: 8, Align: align.Center})),
		)
	}

	m.AddRow(6, col.New(12).Add(text.New("", props.Text{})))

	// ── Öneriler ──
	m.AddRow(8, col.New(12).Add(
		text.New("Öneriler",
			props.Text{Style: fontstyle.Bold, Size: 14, Top: 5}),
	))

	recommendations := []string{
		"Acme: Görünürlük skoru yükselişte — mevcut stratejiyi koruyun.",
		"BetaCorp: Skor düşüşü tespit edildi — rakip analizi yapmanız önerilir.",
		"GammaInc: Yapılandırılmış veri ekleyerek görünürlüğü artırabilirsiniz.",
	}
	for _, rec := range recommendations {
		m.AddRow(5, col.New(12).Add(
			text.New("• "+rec, props.Text{Size: 9, Top: 1}),
		))
	}

	// ── Footer ──
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

	pdfBytes := document.GetBytes()

	result := &ReportResult{
		ID:          generateULID(),
		Type:        ReportWeeklyDigest,
		Data:        pdfBytes,
		FileName:    fmt.Sprintf("weekly-digest-%s.pdf", time.Now().Format("2006-01-02")),
		PageCount:   1,
		GeneratedAt: time.Now().UTC(),
	}

	slog.Info("pdf weekly digest generated", "bytes", len(result.Data))
	return result, nil
}

// generateScoreCard creates a score card PDF for a specific brand.
func (s *service) generateScoreCard(req ReportRequest) (*ReportResult, error) {
	// TODO(H11): Gerçek skor verisiyle doldur
	return nil, fmt.Errorf("pdf: score card henüz implemente edilmedi")
}

// generateAuditReport creates an audit report PDF.
func (s *service) generateAuditReport(req ReportRequest) (*ReportResult, error) {
	// TODO(H11): Gerçek audit verisiyle doldur
	return nil, fmt.Errorf("pdf: audit raporu henüz implemente edilmedi")
}

func generateULID() string {
	return ulid.Make().String()
}
