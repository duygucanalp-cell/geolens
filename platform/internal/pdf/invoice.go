package pdf

import (
	"fmt"
	"time"

	"github.com/johnfercher/maroto/v2"
	"github.com/johnfercher/maroto/v2/pkg/components/col"
	"github.com/johnfercher/maroto/v2/pkg/components/text"
	"github.com/johnfercher/maroto/v2/pkg/config"
	"github.com/johnfercher/maroto/v2/pkg/consts/align"
	"github.com/johnfercher/maroto/v2/pkg/consts/fontstyle"
	"github.com/johnfercher/maroto/v2/pkg/props"
)

// InvoiceData, Türkçe fatura PDF şablonu için gerekli alanları taşır.
// Tutarlar kuruş cinsindendir; PDF'te TL olarak biçimlendirilir.
type InvoiceData struct {
	Number           string
	Status           string
	InvoiceType      string // standard, efatura, earsiv
	GIBStatus        string // none, pending, accepted, rejected
	DocumentID       string
	Currency         string
	Subtotal         int64
	VATRate          int
	VATAmount        int64
	Total            int64
	CustomerName     string
	CustomerTaxNo    string
	CustomerIdentity string
	CustomerAddress  string
	PeriodStart      *time.Time
	PeriodEnd        *time.Time
	CreatedAt        time.Time
}

// RenderInvoice, FR-A6 TR özel kapsamındaki Türkçe fatura PDF şablonunu üretir.
// KDV kırılımı, e-Fatura/e-Arşiv bilgileri ve GİB durumunu gösterir.
func RenderInvoice(inv InvoiceData) ([]byte, error) {
	currency := inv.Currency
	if currency == "" {
		currency = "TRY"
	}
	amount := func(v int64) string {
		return fmt.Sprintf("%s%.2f", currencySymbol(currency), float64(v)/100)
	}

	cfg := config.NewBuilder().
		WithLeftMargin(12).
		WithTopMargin(15).
		WithRightMargin(12).
		Build()

	m := maroto.New(cfg)

	// Başlık
	m.AddRow(12, col.New(12).Add(
		text.New("FATURA",
			props.Text{Style: fontstyle.Bold, Size: 20, Align: align.Center, Top: 5}),
	))
	m.AddRow(8, col.New(12).Add(
		text.New("GeoLens AI Visibility Platform",
			props.Text{Size: 11, Align: align.Center, Top: 2}),
	))
	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	// Fatura bilgileri
	metaProps := props.Text{Size: 10, Top: 2}
	boldMeta := props.Text{Style: fontstyle.Bold, Size: 10, Top: 2}
	m.AddRow(6,
		col.New(6).Add(text.New("Fatura No: "+inv.Number, boldMeta)),
		col.New(6).Add(text.New("Tarih: "+inv.CreatedAt.Format("02.01.2006"), props.Text{Size: 10, Top: 2, Align: align.Right})),
	)
	if inv.DocumentID != "" {
		m.AddRow(5, col.New(12).Add(
			text.New("Belge Kimliği (UUID): "+inv.DocumentID, props.Text{Size: 9, Top: 2}),
		))
	}

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	// Satıcı
	m.AddRow(6, col.New(12).Add(text.New("SATICI", props.Text{Style: fontstyle.Bold, Size: 10, Top: 2})))
	m.AddRow(5, col.New(12).Add(text.New("GeoLens Teknoloji A.Ş.", metaProps)))

	// Alıcı
	m.AddRow(6, col.New(12).Add(text.New("ALICI", props.Text{Style: fontstyle.Bold, Size: 10, Top: 4})))
	m.AddRow(5, col.New(12).Add(text.New(inv.CustomerName, metaProps)))
	if inv.CustomerTaxNo != "" {
		m.AddRow(5, col.New(12).Add(text.New("Vergi Kimlik No: "+inv.CustomerTaxNo, metaProps)))
	} else if inv.CustomerIdentity != "" {
		m.AddRow(5, col.New(12).Add(text.New("T.C. Kimlik No: "+inv.CustomerIdentity, metaProps)))
	}
	if inv.CustomerAddress != "" {
		m.AddRow(5, col.New(12).Add(text.New(inv.CustomerAddress, props.Text{Size: 9, Top: 1})))
	}

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	// Dönem
	if inv.PeriodStart != nil || inv.PeriodEnd != nil {
		start := "-"
		end := "-"
		if inv.PeriodStart != nil {
			start = inv.PeriodStart.Format("02.01.2006")
		}
		if inv.PeriodEnd != nil {
			end = inv.PeriodEnd.Format("02.01.2006")
		}
		m.AddRow(5, col.New(12).Add(text.New("Dönem: "+start+" - "+end, metaProps)))
	}

	m.AddRow(4, col.New(12).Add(text.New("", props.Text{})))

	// Tutar kırılımı
	headerProps := props.Text{Style: fontstyle.Bold, Size: 10, Align: align.Center}
	m.AddRow(6,
		col.New(8).Add(text.New("Açıklama", headerProps)),
		col.New(4).Add(text.New("Tutar", headerProps)),
	)
	rowProps := props.Text{Size: 10, Align: align.Center}
	desc := "GeoLens abonelik ücreti"
	if inv.InvoiceType == "efatura" || inv.InvoiceType == "earsiv" {
		desc = "GeoLens abonelik ücreti (KDV dahil)"
	}
	m.AddRow(6,
		col.New(8).Add(text.New(desc, props.Text{Size: 10, Top: 2})),
		col.New(4).Add(text.New(amount(inv.Subtotal), rowProps)),
	)
	m.AddRow(5,
		col.New(8).Add(text.New(fmt.Sprintf("KDV (%d%%)", inv.VATRate), props.Text{Size: 10, Top: 2})),
		col.New(4).Add(text.New(amount(inv.VATAmount), rowProps)),
	)
	m.AddRow(6,
		col.New(8).Add(text.New("GENEL TOPLAM", props.Text{Style: fontstyle.Bold, Size: 11, Top: 2})),
		col.New(4).Add(text.New(amount(inv.Total), props.Text{Style: fontstyle.Bold, Size: 11, Align: align.Center, Top: 2})),
	)

	m.AddRow(6, col.New(12).Add(text.New("", props.Text{})))

	// e-Fatura / GİB durumu
	invoiceTypeLabel := "Standart Fatura"
	switch inv.InvoiceType {
	case "efatura":
		invoiceTypeLabel = "e-Fatura"
	case "earsiv":
		invoiceTypeLabel = "e-Arşiv"
	}
	m.AddRow(5, col.New(12).Add(
		text.New("Fatura Tipi: "+invoiceTypeLabel, props.Text{Size: 9, Top: 2}),
	))
	gibLabel := map[string]string{
		"none":     "GİB gönderimi yok",
		"pending":  "GİB gönderimde",
		"accepted": "GİB tarafından kabul edildi",
		"rejected": "GİB tarafından reddedildi",
	}[inv.GIBStatus]
	if gibLabel == "" {
		gibLabel = inv.GIBStatus
	}
	m.AddRow(5, col.New(12).Add(
		text.New("GİB Durumu: "+gibLabel, props.Text{Size: 9, Top: 2}),
	))

	m.AddRow(8, col.New(12).Add(text.New("", props.Text{})))

	m.AddRow(6, col.New(12).Add(
		text.New(
			"Bu fatura GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
			props.Text{Size: 7, Align: align.Center, Style: fontstyle.Italic},
		),
	))

	document, err := m.Generate()
	if err != nil {
		return nil, fmt.Errorf("pdf: fatura oluşturma hatası: %w", err)
	}
	return document.GetBytes(), nil
}

func currencySymbol(currency string) string {
	switch currency {
	case "usd":
		return "$"
	case "eur":
		return "€"
	case "try":
		return "₺"
	default:
		return "₺"
	}
}
