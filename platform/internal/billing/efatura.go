package billing

import (
	"bytes"
	"context"
	"encoding/xml"
	"fmt"
	"log/slog"
	"time"

	"github.com/geolens/platform/internal/id"
)

// InvoiceType, bir faturanın e-Fatura/e-Arşiv tipini belirtir.
type InvoiceType string

const (
	InvoiceTypeEFatura InvoiceType = "efatura"
	InvoiceTypeEArsiv  InvoiceType = "earsiv"
)

// GIBStatus, bir faturanın Gelir İdaresi Başkanlığı (GİB) entegrasyon durumudur.
type GIBStatus string

const (
	GIBStatusNone     GIBStatus = "none"
	GIBStatusPending  GIBStatus = "pending"
	GIBStatusAccepted GIBStatus = "accepted"
	GIBStatusRejected GIBStatus = "rejected"
)

// InvoiceDocument, e-Fatura/e-Arşiv için GİB'e gönderilen fatura içeriğidir.
// Tüm para tutarları kuruş cinsindendir.
type InvoiceDocument struct {
	DocumentID       string
	Number           string
	InvoiceType      InvoiceType
	Currency         string
	Subtotal         int64
	VATRate          int
	VATAmount        int64
	Total            int64
	CustomerName     string
	CustomerTaxNo    string
	CustomerIdentity string
	CustomerAddress  string
	IssueDate        time.Time
}

// GIBResponse, GİB entegrasyon servisinin faturanın gönderimine verdiği yanıttır.
type GIBResponse struct {
	Status      GIBStatus `json:"status"`
	ResponseID  string    `json:"response_id"`
	Message     string    `json:"message"`
	SubmittedAt time.Time `json:"submitted_at"`
}

// EFaturaProvider, faturaları e-Fatura/e-Arşiv olarak GİB'e gönderen arayüzdür.
// Stripe mock deseniyle aynı: gerçek GİB entegrasyonu kimlik bilgisi gerektirir,
// sandbox/mock modda Send simüle edilmiş kabul döndürür.
type EFaturaProvider interface {
	Send(ctx context.Context, doc *InvoiceDocument) (*GIBResponse, error)
	// Mode, sağlayıcının çalışma modunu döndürür: "mock" veya "gib".
	Mode() string
}

// mockEFaturaProvider, geliştirme/test ortamları için simüle edilmiş GİB sağlayıcısıdır.
type mockEFaturaProvider struct{}

func (m *mockEFaturaProvider) Mode() string { return "mock" }

func (m *mockEFaturaProvider) Send(_ context.Context, doc *InvoiceDocument) (*GIBResponse, error) {
	status := GIBStatusAccepted
	msg := "GİB Entegrasyon Servisi: fatura kabul edildi (mock mod)"
	slog.Info("e-fatura mock gönderimi", "document_id", doc.DocumentID, "type", doc.InvoiceType)
	return &GIBResponse{
		Status:      status,
		ResponseID:  "gib_" + id.New(),
		Message:     msg,
		SubmittedAt: time.Now().UTC(),
	}, nil
}

// gibEFaturaProvider, üretim GİB entegrasyonu için iskelettir.
// GİB (Gelir İdaresi Başkanlığı) web servis kimlik bilgileri (kullanıcı, şifre, mali mühür)
// sağlanmadan gerçek gönderim yapılamaz; bu nedenle net bir hata döndürür.
type gibEFaturaProvider struct{}

func (g *gibEFaturaProvider) Mode() string { return "gib" }

func (g *gibEFaturaProvider) Send(_ context.Context, _ *InvoiceDocument) (*GIBResponse, error) {
	return nil, fmt.Errorf("gerçek GİB entegrasyonu için mali mühür ve web servis kimlik bilgileri gerekir")
}

// NewEFaturaProvider, yapılandırılan moda göre sağlayıcı döndürür.
// Boş veya "mock" modunda simüle edilmiş GİB kullanılır; aksi hâlde gerçek GİB iskeleti döner.
func NewEFaturaProvider(mode string) EFaturaProvider {
	if mode == "" || mode == "mock" {
		return &mockEFaturaProvider{}
	}
	return &gibEFaturaProvider{}
}

// ---- UBL-TR XML Üretimi ----

// ublXML, GİB e-Fatura/e-Arşiv UBL-TR formatının gerekli alt kümesini üretir.
type ublXML struct {
	XMLName              xml.Name         `xml:"Invoice"`
	Xmlns                string           `xml:"xmlns,attr"`
	XmlnsCac             string           `xml:"xmlns:cac,attr"`
	XmlnsCbc             string           `xml:"xmlns:cbc,attr"`
	UBLVersionID         string           `xml:"cbc:UBLVersionID"`
	CustomizationID      string           `xml:"cbc:CustomizationID"`
	ProfileID            string           `xml:"cbc:ProfileID"`
	ID                   string           `xml:"cbc:ID"`
	CopyIndicator        string           `xml:"cbc:CopyIndicator"`
	UUID                 string           `xml:"cbc:UUID"`
	IssueDate            string           `xml:"cbc:IssueDate"`
	IssueTime            string           `xml:"cbc:IssueTime"`
	InvoiceTypeCode      string           `xml:"cbc:InvoiceTypeCode"`
	Note                 string           `xml:"cbc:Note"`
	DocumentCurrencyCode string           `xml:"cbc:DocumentCurrencyCode"`
	SupplierParty        ublParty         `xml:"cac:AccountingSupplierParty>cac:Party"`
	CustomerParty        ublParty         `xml:"cac:AccountingCustomerParty>cac:Party"`
	TaxTotal             ublTaxTotal      `xml:"cac:TaxTotal"`
	LegalMonetaryTotal   ublMonetaryTotal `xml:"cac:LegalMonetaryTotal"`
}

type ublParty struct {
	PartyName      string        `xml:"cac:PartyName>cbc:Name"`
	PartyTaxScheme *ublTaxScheme `xml:"cac:PartyTaxScheme"`
	PostalAddress  *ublAddress   `xml:"cac:PostalAddress"`
	Person         *ublPerson    `xml:"cac:Person"`
}

type ublTaxScheme struct {
	Identifier string `xml:"cbc:Identifier"`
	Name       string `xml:"cbc:Name"`
	SchemeID   string `xml:"cac:TaxScheme>cbc:ID"`
	SchemeName string `xml:"cac:TaxScheme>cbc:Name"`
}

type ublAddress struct {
	CitySubdivision string `xml:"cbc:CitySubdivisionName"`
	StreetName      string `xml:"cbc:StreetName"`
	CityName        string `xml:"cbc:CityName"`
}

type ublPerson struct {
	FirstName string `xml:"cbc:FirstName"`
	LastName  string `xml:"cbc:FamilyName"`
}

type ublTaxTotal struct {
	TaxAmount   string         `xml:"cbc:TaxAmount"`
	TaxSubtotal ublTaxSubtotal `xml:"cac:TaxSubtotal"`
}

type ublTaxSubtotal struct {
	TaxableAmount string `xml:"cbc:TaxableAmount"`
	TaxAmount     string `xml:"cbc:TaxAmount"`
	TaxCategory   string `xml:"cac:TaxCategory>cbc:ID"`
	Percent       string `xml:"cac:TaxCategory>cbc:Percent"`
	TaxScheme     string `xml:"cac:TaxCategory>cac:TaxScheme>cbc:ID"`
	TaxSchemeName string `xml:"cac:TaxCategory>cac:TaxScheme>cbc:Name"`
}

type ublMonetaryTotal struct {
	LineExtensionAmount string `xml:"cbc:LineExtensionAmount"`
	TaxExclusiveAmount  string `xml:"cbc:TaxExclusiveAmount"`
	TaxInclusiveAmount  string `xml:"cbc:TaxInclusiveAmount"`
	PayableAmount       string `xml:"cbc:PayableAmount"`
}

// InvoiceTypeCodeFor, GİB UBL InvoiceTypeCode değerini döndürür.
// e-Fatura → SATIS (satış faturası), e-Arşiv → EARSIV.
func InvoiceTypeCodeFor(t InvoiceType) string {
	switch t {
	case InvoiceTypeEArsiv:
		return "EARSIV"
	default:
		return "SATIS"
	}
}

// BuildUBLTREInvoice, bir faturanın UBL-TR XML belgesini üretir (e-Fatura/e-Arşiv).
func BuildUBLTREInvoice(doc *InvoiceDocument) ([]byte, error) {
	if doc == nil {
		return nil, fmt.Errorf("fatura dokümanı boş")
	}
	if doc.DocumentID == "" || doc.Number == "" {
		return nil, fmt.Errorf("belge kimliği ve numara zorunludur")
	}

	amount := func(v int64) string {
		// kuruş → TL (2 ondalık)
		return fmt.Sprintf("%.2f", float64(v)/100)
	}

	currency := doc.Currency
	if currency == "" {
		currency = "TRY"
	}

	customer := ublParty{PartyName: doc.CustomerName}
	if doc.CustomerTaxNo != "" {
		customer.PartyTaxScheme = &ublTaxScheme{
			Identifier: doc.CustomerTaxNo,
			SchemeID:   "VKN",
			SchemeName: "Vergi Kimlik Numarası",
		}
	} else if doc.CustomerIdentity != "" {
		customer.Person = &ublPerson{FirstName: doc.CustomerName}
		customer.PartyTaxScheme = &ublTaxScheme{
			Identifier: doc.CustomerIdentity,
			SchemeID:   "TCKN",
			SchemeName: "T.C. Kimlik Numarası",
		}
	}
	if doc.CustomerAddress != "" {
		customer.PostalAddress = &ublAddress{StreetName: doc.CustomerAddress}
	}

	x := &ublXML{
		Xmlns:                "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
		XmlnsCac:             "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
		XmlnsCbc:             "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
		UBLVersionID:         "2.1",
		CustomizationID:      "TR1.2",
		ProfileID:            "TICARIFATURA",
		ID:                   doc.Number,
		CopyIndicator:        "false",
		UUID:                 doc.DocumentID,
		IssueDate:            doc.IssueDate.Format("2006-01-02"),
		IssueTime:            doc.IssueDate.Format("15:04:05"),
		InvoiceTypeCode:      InvoiceTypeCodeFor(doc.InvoiceType),
		Note:                 "Bu belge GeoLens AI Visibility Platform tarafından otomatik oluşturulmuştur.",
		DocumentCurrencyCode: currency,
		SupplierParty: ublParty{
			PartyName: "GeoLens Teknoloji A.Ş.",
		},
		CustomerParty: customer,
		TaxTotal: ublTaxTotal{
			TaxAmount: amount(doc.VATAmount),
			TaxSubtotal: ublTaxSubtotal{
				TaxableAmount: amount(doc.Subtotal),
				TaxAmount:     amount(doc.VATAmount),
				TaxCategory:   "S",
				Percent:       fmt.Sprintf("%d", doc.VATRate),
				TaxScheme:     "KDV",
				TaxSchemeName: "Katma Değer Vergisi",
			},
		},
		LegalMonetaryTotal: ublMonetaryTotal{
			LineExtensionAmount: amount(doc.Subtotal),
			TaxExclusiveAmount:  amount(doc.Subtotal),
			TaxInclusiveAmount:  amount(doc.Total),
			PayableAmount:       amount(doc.Total),
		},
	}

	var buf bytes.Buffer
	if _, err := buf.WriteString(xml.Header); err != nil {
		return nil, fmt.Errorf("xml başlık: %w", err)
	}
	if err := xml.NewEncoder(&buf).Encode(x); err != nil {
		return nil, fmt.Errorf("ubl xml üretimi: %w", err)
	}
	return buf.Bytes(), nil
}
