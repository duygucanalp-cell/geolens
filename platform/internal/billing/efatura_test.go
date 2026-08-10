package billing

import (
	"bytes"
	"context"
	"strings"
	"testing"
	"time"
)

func TestNewEFaturaProvider_MockMode(t *testing.T) {
	for _, mode := range []string{"", "mock"} {
		p := NewEFaturaProvider(mode)
		if p.Mode() != "mock" {
			t.Fatalf("mode=%q için beklenen mock, gelen %q", mode, p.Mode())
		}
	}
}

func TestNewEFaturaProvider_GIB(t *testing.T) {
	p := NewEFaturaProvider("gib")
	if p.Mode() != "gib" {
		t.Fatalf("beklenen gib, gelen %q", p.Mode())
	}
}

func TestMockEFaturaProvider_Send(t *testing.T) {
	p := NewEFaturaProvider("mock")
	doc := &InvoiceDocument{
		DocumentID:  "123e4567-e89b-12d3-a456-426614174000",
		Number:      "INV-2026-001",
		InvoiceType: InvoiceTypeEFatura,
	}
	resp, err := p.Send(context.Background(), doc)
	if err != nil {
		t.Fatalf("mock gönderim hatası: %v", err)
	}
	if resp.Status != GIBStatusAccepted {
		t.Fatalf("beklenen accepted, gelen %q", resp.Status)
	}
	if resp.ResponseID == "" {
		t.Fatal("response_id boş olmamalı")
	}
}

func TestBuildUBLTREInvoice_EFatura(t *testing.T) {
	doc := &InvoiceDocument{
		DocumentID:      "123e4567-e89b-12d3-a456-426614174000",
		Number:          "INV-2026-001",
		InvoiceType:     InvoiceTypeEFatura,
		Currency:        "TRY",
		Subtotal:        100000, // 1000.00 TL
		VATRate:         20,
		VATAmount:       20000, // 200.00 TL
		Total:           120000,
		CustomerName:    "Acme Bilişim A.Ş.",
		CustomerTaxNo:   "1234567890",
		CustomerAddress: "Levent Mah. Büyükdere Cad. No:1 İstanbul",
		IssueDate:       time.Date(2026, 8, 3, 10, 30, 0, 0, time.UTC),
	}

	raw, err := BuildUBLTREInvoice(doc)
	if err != nil {
		t.Fatalf("xml üretim hatası: %v", err)
	}
	if len(raw) == 0 {
		t.Fatal("üretilen xml boş")
	}

	s := string(raw)
	for _, want := range []string{
		"<Invoice", "TR1.2", "TICARIFATURA", "123e4567-e89b-12d3-a456-426614174000",
		"2026-08-03", "SATIS", "TRY", "VKN", "1234567890", "KDV",
		"1000.00", "200.00", "1200.00", "Acme Bilişim A.Ş.",
	} {
		if !strings.Contains(s, want) {
			t.Fatalf("üretilen xml %q içermiyor:\n%s", want, s)
		}
	}
}

func TestBuildUBLTREInvoice_EArsiv_TCKN(t *testing.T) {
	doc := &InvoiceDocument{
		DocumentID:       "abc-123",
		Number:           "INV-2026-002",
		InvoiceType:      InvoiceTypeEArsiv,
		Subtotal:         5000,
		VATRate:          10,
		VATAmount:        500,
		Total:            5500,
		CustomerName:     "Ali Yılmaz",
		CustomerIdentity: "11122233344",
		IssueDate:        time.Now(),
	}

	raw, err := BuildUBLTREInvoice(doc)
	if err != nil {
		t.Fatalf("xml üretim hatası: %v", err)
	}
	s := string(raw)
	for _, want := range []string{"EARSIV", "TCKN", "11122233344"} {
		if !strings.Contains(s, want) {
			t.Fatalf("üretilen xml %q içermiyor:\n%s", want, s)
		}
	}
}

func TestBuildUBLTREInvoice_Validation(t *testing.T) {
	if _, err := BuildUBLTREInvoice(nil); err == nil {
		t.Fatal("nil doküman için hata dönülmedi")
	}
	if _, err := BuildUBLTREInvoice(&InvoiceDocument{}); err == nil {
		t.Fatal("boş belge kimliği için hata dönülmedi")
	}
}

func TestUBLXML_WellFormed(t *testing.T) {
	doc := &InvoiceDocument{
		DocumentID:  "d1",
		Number:      "INV-1",
		InvoiceType: InvoiceTypeEFatura,
		Subtotal:    100,
		VATRate:     20,
		VATAmount:   20,
		Total:       120,
		IssueDate:   time.Now(),
	}
	raw, err := BuildUBLTREInvoice(doc)
	if err != nil {
		t.Fatalf("xml üretim hatası: %v", err)
	}
	if !bytes.Contains(raw, []byte("<?xml")) {
		t.Fatal("xml başlık eksik")
	}
}
