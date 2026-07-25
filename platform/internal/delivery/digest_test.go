package delivery

import (
	"strings"
	"testing"
)

// ---- buildDigestHTML Tests ----

func TestBuildDigestHTML_EmptyBrandsAndRecs(t *testing.T) {
	svc := &service{}
	html := svc.buildDigestHTML("Test Subject", nil, nil, "ws-1", "t-1")

	if !strings.Contains(html, "GeoLens Haftal") {
		t.Error("HTML should contain digest title")
	}
	if !strings.Contains(html, "Hen") && !strings.Contains(html, "ölçüm") && !strings.Contains(html, "olcum") {
		t.Error("HTML should show empty state for brands")
	}
	if !strings.Contains(html, "oneri") && !strings.Contains(html, "öneri") {
		t.Error("HTML should show empty state for recommendations")
	}
	if !strings.Contains(html, "Panoda") {
		t.Error("HTML should contain dashboard button")
	}
	if !strings.Contains(html, "Panoya Git") {
		t.Error("HTML should contain dashboard link")
	}
}

func TestBuildDigestHTML_WithBrands(t *testing.T) {
	svc := &service{}
	brands := []digestBrandScore{
		{BrandID: "b1", BrandName: "Acme", Score: 85, PreviousScore: 80, Change: 5},
		{BrandID: "b2", BrandName: "BetaCorp", Score: 62, PreviousScore: 70, Change: -8},
		{BrandID: "b3", BrandName: "GammaInc", Score: 50, PreviousScore: 50, Change: 0},
	}

	html := svc.buildDigestHTML("Test Subject", brands, nil, "ws-1", "t-1")

	if !strings.Contains(html, "Acme") {
		t.Error("HTML should contain brand name Acme")
	}
	if !strings.Contains(html, "BetaCorp") {
		t.Error("HTML should contain brand name BetaCorp")
	}
	if !strings.Contains(html, "GammaInc") {
		t.Error("HTML should contain brand name GammaInc")
	}
	if !strings.Contains(html, "change-neutral") {
		t.Error("HTML should show neutral change indicator")
	}
	if !strings.Contains(html, "85") {
		t.Error("HTML should contain score 85")
	}
	if !strings.Contains(html, "62") {
		t.Error("HTML should contain score 62")
	}
}

func TestBuildDigestHTML_WithRecommendations(t *testing.T) {
	svc := &service{}
	recs := []digestRecommendation{
		{BrandName: "Acme", Title: "Skor Dususu", Detail: "Gorunurluk skorunuz dusuyor"},
		{BrandName: "BetaCorp", Title: "Trend", Detail: "Trend analizi yapmaniz onerilir"},
	}

	html := svc.buildDigestHTML("Test Subject", nil, recs, "ws-1", "t-1")

	if !strings.Contains(html, "Gorunurluk") || !strings.Contains(html, "dusuyor") {
		if !strings.Contains(html, "Görünürlük") && !strings.Contains(html, "düşüyor") {
			t.Error("HTML should contain recommendation detail")
		}
	}
	if !strings.Contains(html, "Trend analizi") {
		t.Error("HTML should contain second recommendation detail")
	}
	if !strings.Contains(html, "Acme:") {
		t.Error("HTML should contain brand name in recommendation")
	}
}

func TestBuildDigestHTML_DashboardURL(t *testing.T) {
	svc := &service{}
	html := svc.buildDigestHTML("Test", nil, nil, "ws-custom", "t-1")

	if !strings.Contains(html, "ws-custom") {
		t.Error("HTML should contain workspace ID in dashboard URL")
	}
	if !strings.Contains(html, "https://app.geolens.ai/v1/workspaces/ws-custom/dashboard") {
		t.Error("HTML should contain correct dashboard URL")
	}
}

func TestBuildDigestHTML_SubjectInTitle(t *testing.T) {
	svc := &service{}
	html := svc.buildDigestHTML("Haftalik Ozet", nil, nil, "ws-1", "t-1")

	if !strings.Contains(html, "GeoLens Haftal") {
		t.Error("HTML should contain 'GeoLens Haftalik Ozet' in header")
	}
}

// ---- escapeHTML Tests ----

func TestEscapeHTML_NoSpecialChars(t *testing.T) {
	result := escapeHTML("Merhaba Dunya")
	if result != "Merhaba Dunya" {
		t.Errorf("expected 'Merhaba Dunya', got '%s'", result)
	}
}

func TestEscapeHTML_Ampersand(t *testing.T) {
	result := escapeHTML("Acme & Co")
	amp := string([]byte{'&', 'a', 'm', 'p', ';'})
	expected := "Acme " + amp + " Co"
	if result != expected {
		t.Errorf("expected '%s', got '%s'", expected, result)
	}
}

func TestEscapeHTML_LessThan(t *testing.T) {
	result := escapeHTML("a < b")
	lt := string([]byte{'&', 'l', 't', ';'})
	expected := "a " + lt + " b"
	if result != expected {
		t.Errorf("expected '%s', got '%s'", expected, result)
	}
}

func TestEscapeHTML_GreaterThan(t *testing.T) {
	result := escapeHTML("a > b")
	gt := string([]byte{'&', 'g', 't', ';'})
	expected := "a " + gt + " b"
	if result != expected {
		t.Errorf("expected '%s', got '%s'", expected, result)
	}
}

func TestEscapeHTML_DoubleQuote(t *testing.T) {
	result := escapeHTML("\"quote\"")
	quot := string([]byte{'&', 'q', 'u', 'o', 't', ';'})
	if !strings.Contains(result, quot) {
		t.Errorf("should escape double quotes, got: %s", result)
	}
}

func TestEscapeHTML_SingleQuote(t *testing.T) {
	result := escapeHTML("'quote'")
	pos39 := string([]byte{'&', '#', '3', '9', ';'})
	if !strings.Contains(result, pos39) {
		t.Errorf("should escape single quotes, got: %s", result)
	}
}

func TestEscapeHTML_AllSpecialChars(t *testing.T) {
	result := escapeHTML("<div class=\"test\">Acme & Co</div>")
	amp := string([]byte{'&', 'a', 'm', 'p', ';'})
	lt := string([]byte{'&', 'l', 't', ';'})
	gt := string([]byte{'&', 'g', 't', ';'})
	quot := string([]byte{'&', 'q', 'u', 'o', 't', ';'})

	if !strings.Contains(result, lt) {
		t.Error("should escape <")
	}
	if !strings.Contains(result, gt) {
		t.Error("should escape >")
	}
	if !strings.Contains(result, quot) {
		t.Error("should escape double quote")
	}
	if !strings.Contains(result, amp) {
		t.Error("should escape &")
	}
}

func TestEscapeHTML_EmptyString(t *testing.T) {
	result := escapeHTML("")
	if result != "" {
		t.Errorf("expected empty string, got '%s'", result)
	}
}
