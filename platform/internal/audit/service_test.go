package audit

import (
	"testing"
)

// ---- computeOverallScore tests ----

func TestComputeOverallScore_Perfect(t *testing.T) {
	result := &AuditResult{
		RobotsTxtCheck: &RobotsTxtCheck{Exists: true, AllowsAIBots: true, DisallowedAll: false},
		BotAccessCheck: &BotAccessCheck{Accessible: true, StatusCode: 200},
		SSRCheck:       &SSRCheck{HasMetaTags: true, HasOGTags: true, HasStructuredData: true},
		SSRFCheck:      &SSRFCheck{HasCloudflare: true, CSPPresent: true},
	}

	svc := &service{}
	score := svc.computeOverallScore(result)
	if score != 100 {
		t.Errorf("beklenen 100, gerçek %f", score)
	}
}

func TestComputeOverallScore_RobotsDisallowedAll(t *testing.T) {
	result := &AuditResult{
		RobotsTxtCheck: &RobotsTxtCheck{Exists: true, AllowsAIBots: false, DisallowedAll: true},
		BotAccessCheck: &BotAccessCheck{Accessible: true, StatusCode: 200},
		SSRCheck:       &SSRCheck{HasMetaTags: true, HasOGTags: true, HasStructuredData: true},
		SSRFCheck:      &SSRFCheck{HasCloudflare: true, CSPPresent: true},
	}

	svc := &service{}
	score := svc.computeOverallScore(result)
	if score != 60 { // 100 - 40 = 60
		t.Errorf("beklenen 60, gerçek %f", score)
	}
}

func TestComputeOverallScore_BotInaccessible(t *testing.T) {
	result := &AuditResult{
		RobotsTxtCheck: &RobotsTxtCheck{Exists: true, AllowsAIBots: true},
		BotAccessCheck: &BotAccessCheck{Accessible: false, StatusCode: 403},
		SSRCheck:       &SSRCheck{HasMetaTags: true, HasOGTags: true, HasStructuredData: true},
		SSRFCheck:      &SSRFCheck{HasCloudflare: true, CSPPresent: true},
	}

	svc := &service{}
	score := svc.computeOverallScore(result)
	if score != 80 { // 100 - 20 = 80
		t.Errorf("beklenen 80, gerçek %f", score)
	}
}

func TestComputeOverallScore_AllSSRMissing(t *testing.T) {
	result := &AuditResult{
		RobotsTxtCheck: &RobotsTxtCheck{Exists: true, AllowsAIBots: true},
		BotAccessCheck: &BotAccessCheck{Accessible: true, StatusCode: 200},
		SSRCheck:       &SSRCheck{HasMetaTags: false, HasOGTags: false, HasStructuredData: false},
		SSRFCheck:      &SSRFCheck{HasCloudflare: true, CSPPresent: true},
	}

	svc := &service{}
	score := svc.computeOverallScore(result)
	if score != 75 { // 100 - 10 - 5 - 10 = 75
		t.Errorf("beklenen 75, gerçek %f", score)
	}
}

func TestComputeOverallScore_NoSecurity(t *testing.T) {
	result := &AuditResult{
		RobotsTxtCheck: &RobotsTxtCheck{Exists: true, AllowsAIBots: true},
		BotAccessCheck: &BotAccessCheck{Accessible: true, StatusCode: 200},
		SSRCheck:       &SSRCheck{HasMetaTags: true, HasOGTags: true, HasStructuredData: true},
		SSRFCheck:      &SSRFCheck{HasCloudflare: false, CSPPresent: false},
	}

	svc := &service{}
	score := svc.computeOverallScore(result)
	if score != 90 { // 100 - 5 - 5 = 90
		t.Errorf("beklenen 90, gerçek %f", score)
	}
}

func TestComputeOverallScore_NegativeProtection(t *testing.T) {
	result := &AuditResult{
		RobotsTxtCheck: &RobotsTxtCheck{Exists: true, AllowsAIBots: false, DisallowedAll: true},
		BotAccessCheck: &BotAccessCheck{Accessible: false, StatusCode: 403},
		SSRCheck:       &SSRCheck{HasMetaTags: false, HasOGTags: false, HasStructuredData: false},
		SSRFCheck:      &SSRFCheck{HasCloudflare: false, CSPPresent: false},
	}

	svc := &service{}
	score := svc.computeOverallScore(result)
	expected := 5.0 // 100 - 40 - 20 - 10 - 5 - 10 - 5 - 5 = 5
	if score != expected {
		t.Errorf("beklenen %f, gerçek %f", expected, score)
	}
}

func TestComputeOverallScore_NilChecks(t *testing.T) {
	result := &AuditResult{}
	svc := &service{}
	score := svc.computeOverallScore(result)
	if score != 100 { // Tüm kontroller nil → kesinti yok
		t.Errorf("beklenen 100, gerçek %f", score)
	}
}

// ---- normalizeURL tests ----

func TestNormalizeURL_HTTPS(t *testing.T) {
	got := normalizeURL("https://example.com")
	if got != "https://example.com" {
		t.Errorf("beklenen 'https://example.com', gerçek %s", got)
	}
}

func TestNormalizeURL_WithPath(t *testing.T) {
	got := normalizeURL("https://example.com/path/to/page")
	if got != "https://example.com" {
		t.Errorf("beklenen 'https://example.com', gerçek %s", got)
	}
}

func TestNormalizeURL_HTTP(t *testing.T) {
	got := normalizeURL("http://test.org/page")
	if got != "http://test.org" {
		t.Errorf("beklenen 'http://test.org', gerçek %s", got)
	}
}

func TestNormalizeURL_NoProtocol(t *testing.T) {
	got := normalizeURL("example.com")
	if got != "https://example.com" {
		t.Errorf("beklenen 'https://example.com', gerçek %s", got)
	}
}

func TestNormalizeURL_Subdomain(t *testing.T) {
	got := normalizeURL("https://sub.domain.co.uk/path")
	if got != "https://sub.domain.co.uk" {
		t.Errorf("beklenen 'https://sub.domain.co.uk', gerçek %s", got)
	}
}

func TestNormalizeURL_EmptyString(t *testing.T) {
	got := normalizeURL("")
	if got != "https://" {
		t.Errorf("beklenen 'https://', gerçek %s", got)
	}
}

// ---- parseRobotsTxtContent tests ----

func TestParseRobotsTxt_Empty(t *testing.T) {
	check := parseRobotsTxtContent([]byte{}, true)
	if !check.Exists {
		t.Error("exists true olmalı")
	}
	if !check.AllowsAIBots {
		t.Error("boş robots.txt AI botlarına izin vermeli")
	}
	if check.DisallowedAll {
		t.Error("boş robots.txt disallowed_all=true olmamalı")
	}
}

func TestParseRobotsTxt_DisallowAll(t *testing.T) {
	content := []byte("User-agent: *\nDisallow: /")
	check := parseRobotsTxtContent(content, true)
	if !check.DisallowedAll {
		t.Error("Disallow: / için disallowed_all=true olmalı")
	}
	if check.AllowsAIBots {
		t.Error("Disallow: / için allows_ai_bots=false olmalı")
	}
}

func TestParseRobotsTxt_PartialBlock(t *testing.T) {
	content := []byte("User-agent: *\nDisallow: /admin/\nDisallow: /private/")
	check := parseRobotsTxtContent(content, true)
	if check.DisallowedAll {
		t.Error("kısmi blok için disallowed_all=false olmalı")
	}
	if !check.AllowsAIBots {
		t.Error("kısmi blok AI botlarına izin vermeli")
	}
	if len(check.BlockedPaths) != 2 {
		t.Errorf("beklenen 2 engelli yol, gerçek %d", len(check.BlockedPaths))
	}
}

func TestParseRobotsTxt_AIBotSpecificBlock(t *testing.T) {
	content := []byte("User-agent: ChatGPT-User\nDisallow: /\nUser-agent: *\nAllow: /")
	check := parseRobotsTxtContent(content, true)
	if check.AllowsAIBots {
		t.Error("ChatGPT-User için Disallow: / var, allows_ai_bots=false olmalı")
	}
}

func TestParseRobotsTxt_NotFound(t *testing.T) {
	check := parseRobotsTxtContent([]byte{}, false)
	if check.Exists {
		t.Error("exists false olmalı")
	}
	if !check.AllowsAIBots {
		t.Error("robots.txt yoksa AI botlarına izin var sayılır")
	}
}

func TestParseRobotsTxt_MultipleAIBots(t *testing.T) {
	content := []byte("User-agent: Google-Extended\nDisallow: /\nUser-agent: ChatGPT-User\nDisallow: /\nUser-agent: *\nAllow: /")
	check := parseRobotsTxtContent(content, true)
	if check.DisallowedAll {
		t.Error("genel kural Allow: / olduğu için disallowed_all=false olmalı")
	}
	if check.AllowsAIBots {
		t.Error("Google-Extended ve ChatGPT-User için Disallow: / var, allows_ai_bots=false olmalı")
	}
}

func TestParseRobotsTxt_NoUserAgent(t *testing.T) {
	content := []byte("Disallow: /")
	check := parseRobotsTxtContent(content, true)
	if !check.DisallowedAll {
		t.Error("User-agent belirtilmemiş Disallow: / için disallowed_all=true olmalı")
	}
}

func TestParseRobotsTxt_CaseInsensitive(t *testing.T) {
	content := []byte("USER-AGENT: *\nDISALLOW: /")
	check := parseRobotsTxtContent(content, true)
	if !check.DisallowedAll {
		t.Error("büyük/küçük harf duyarsız olmalı")
	}
}
