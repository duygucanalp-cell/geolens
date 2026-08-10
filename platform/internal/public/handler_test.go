package public

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/testutil"
	"github.com/geolens/platform/platform/httpmw"
)

func newTestHandler() *Handler {
	return NewHandler(&testutil.MockPool{})
}

func TestGetScore_EmptyBrandID(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodGet, "/public/v1/scores/", nil)
	w := httptest.NewRecorder()
	h.GetScore(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

func TestListTrends_NoQuery(t *testing.T) {
	h := newTestHandler()
	req := httptest.NewRequest(http.MethodGet, "/public/v1/trends", nil)
	w := httptest.NewRecorder()
	h.ListTrends(w, req)
	if w.Result().StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Result().StatusCode)
	}
}

// TestListReports_ReadsFromMeasureReports — public rapor listesi, FR-F5 akışının
// gerçek tablosu olan measure.reports üzerinden okunur (report_type ve params
// içindeki page_count ile). Eski measurement_reports şeması kullanılmaz.
func TestListReports_ReadsFromMeasureReports(t *testing.T) {
	now := time.Now()
	h := &Handler{pool: &testutil.MockPool{
		QueryFunc: func(ctx context.Context, sql string, args ...any) (dbiface.RowsIter, error) {
			return testutil.NewMockRows([][]any{
				{"R1", "score_card", "score-card-Acme.pdf", 1, now},
				{"R2", "digest", "weekly-digest.pdf", 2, now},
			}), nil
		},
	}}

	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, "T01")
	req := httptest.NewRequest(http.MethodGet, "/public/v1/reports", nil).WithContext(ctx)
	w := httptest.NewRecorder()

	h.ListReports(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var reports []map[string]interface{}
	if err := json.NewDecoder(w.Body).Decode(&reports); err != nil {
		t.Fatalf("yanıt ayrıştırılamadı: %v", err)
	}
	if len(reports) != 2 {
		t.Fatalf("expected 2 reports, got %d", len(reports))
	}
	if reports[0]["id"] != "R1" || reports[0]["type"] != "score_card" || reports[0]["file_name"] != "score-card-Acme.pdf" {
		t.Fatalf("beklenmeyen ilk satır: %+v", reports[0])
	}
	if reports[1]["page_count"].(float64) != 2 {
		t.Fatalf("page_count params'tan okunmalı, got %v", reports[1]["page_count"])
	}
}

// TestDownloadReport_DecodesPDFB64 — hazır rapor, params içindeki base64
// pdf_b64 verisinden çözülür ve PDF olarak döner.
func TestDownloadReport_DecodesPDFB64(t *testing.T) {
	pdfBytes := []byte("%PDF-1.4 test içerik")
	paramsJSON := fmt.Sprintf(`{"brand_name":"Acme","pdf_b64":%q,"page_count":1}`, base64.StdEncoding.EncodeToString(pdfBytes))

	h := &Handler{pool: &testutil.MockPool{
		QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"score-card-Acme.pdf", paramsJSON}}
		},
	}}

	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, "T01")
	req := httptest.NewRequest(http.MethodGet, "/public/v1/reports/R1/download", nil).WithContext(ctx)
	w := httptest.NewRecorder()

	h.DownloadReport(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	if ct := w.Header().Get("Content-Type"); ct != "application/pdf" {
		t.Fatalf("expected application/pdf, got %q", ct)
	}
	if w.Body.String() != string(pdfBytes) {
		t.Fatalf("PDF verisi eşleşmedi: %q vs %q", w.Body.String(), pdfBytes)
	}
}

// TestDownloadReport_RedirectsToS3 — params içinde s3_url varsa harici depoya yönlendirilir.
func TestDownloadReport_RedirectsToS3(t *testing.T) {
	h := &Handler{pool: &testutil.MockPool{
		QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{Values: []any{"r.pdf", `{"s3_url":"https://s3.example.com/report.pdf"}`}}
		},
	}}

	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, "T01")
	req := httptest.NewRequest(http.MethodGet, "/public/v1/reports/R1/download", nil).WithContext(ctx)
	w := httptest.NewRecorder()

	h.DownloadReport(w, req)

	if w.Code != http.StatusFound {
		t.Fatalf("expected 302, got %d", w.Code)
	}
	if loc := w.Header().Get("Location"); loc != "https://s3.example.com/report.pdf" {
		t.Fatalf("expected S3 redirect, got %q", loc)
	}
}

// TestDownloadReport_NotFound — rapor yoksa veya ready değilse 404 döner.
func TestDownloadReport_NotFound(t *testing.T) {
	h := &Handler{pool: &testutil.MockPool{
		QueryRowFunc: func(ctx context.Context, sql string, args ...any) dbiface.RowScanner {
			return &testutil.MockRow{Err: fmt.Errorf("satır bulunamadı")}
		},
	}}

	ctx := context.WithValue(context.Background(), httpmw.CtxKeyTenantID, "T01")
	req := httptest.NewRequest(http.MethodGet, "/public/v1/reports/NOPE/download", nil).WithContext(ctx)
	w := httptest.NewRecorder()

	h.DownloadReport(w, req)

	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Code)
	}
}
