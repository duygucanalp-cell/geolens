package audit

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/geolens/platform/platform/db"
	"github.com/oklog/ulid/v2"
)

// List of known AI crawler user agents.
var aiCrawlers = []AICrawler{
	{UserAgent: "ChatGPT-User/1.0", Name: "ChatGPT (OpenAI)", Source: "chatgpt"},
	{UserAgent: "Google-Extended/1.0", Name: "Google-Extended (Gemini)", Source: "gemini"},
	{UserAgent: "PerplexityBot/1.0", Name: "PerplexityBot", Source: "perplexity"},
	{UserAgent: "CCBot/1.0", Name: "CCBot (Common Crawl)", Source: "perplexity"},
	{UserAgent: "Claude-Web/1.0", Name: "Claude (Anthropic)", Source: "chatgpt"},
	{UserAgent: "Applebot-Extended/1.0", Name: "Applebot-Extended", Source: "chatgpt"},
}

const defaultTimeout = 10 * time.Second

// service implements the Service interface for site auditing.
type service struct {
	pool       *db.Pool
	httpClient *http.Client
}

// NewService creates a new site audit service.
func NewService(pool *db.Pool) Service {
	return &service{
		pool: pool,
		httpClient: &http.Client{
			Timeout: defaultTimeout,
		},
	}
}

// Audit performs a complete site audit for the given brand.
func (s *service) Audit(brandID, brandName, websiteURL string) (*AuditResult, error) {
	result := &AuditResult{
		ID:         generateULID(),
		BrandID:    brandID,
		BrandName:  brandName,
		WebsiteURL: websiteURL,
		CreatedAt:  time.Now().UTC(),
	}

	// 1. Robots.txt kontrolü
	robotsResult := s.checkRobotsTxt(websiteURL)
	result.RobotsTxtCheck = robotsResult
	if robotsResult != nil && robotsResult.DisallowedAll {
		result.Issues = append(result.Issues, Issue{
			Severity:       "critical",
			Category:       "robots",
			Title:          "AI botları tamamen engellenmiş",
			Detail:         fmt.Sprintf("%s robots.txt tüm kullanıcı ajanlarını engelliyor. AI görünürlük ölçümü yapılamayabilir.", websiteURL),
			Recommendation: "robots.txt'den 'Disallow: /' kuralını kaldırın veya AI botlarına özel izin verin.",
		})
	}

	// 2. Bot erişim kontrolü
	botResult := s.checkBotAccess(websiteURL)
	result.BotAccessCheck = botResult

	// 3. SSR sinyal kontrolü
	ssrResult := s.checkSSR(websiteURL)
	result.SSRCheck = ssrResult
	if ssrResult != nil {
		if !ssrResult.HasMetaTags {
			result.Issues = append(result.Issues, Issue{
				Severity:       "medium",
				Category:       "ssr",
				Title:          "Meta etiketleri eksik",
				Detail:         "Sayfada meta description veya title etiketi bulunamadı. AI motorları içeriği doğru anlayamayabilir.",
				Recommendation: "Her sayfaya unique meta title ve description ekleyin.",
			})
		}
		if !ssrResult.HasOGTags {
			result.Issues = append(result.Issues, Issue{
				Severity:       "medium",
				Category:       "ssr",
				Title:          "Open Graph etiketleri eksik",
				Detail:         "Sayfada Open Graph (og:) etiketleri bulunamadı. Sosyal medya ve AI görünürlüğü için önemlidir.",
				Recommendation: "Standart OG etiketlerini (og:title, og:description, og:image) ekleyin.",
			})
		}
		if !ssrResult.HasStructuredData {
			result.Issues = append(result.Issues, Issue{
				Severity:       "medium",
				Category:       "ssr",
				Title:          "Yapılandırılmış veri eksik",
				Detail:         "Sayfada JSON-LD veya Schema.org yapılandırılmış verisi bulunamadı. AI motorları için bağlam sağlamak önemlidir.",
				Recommendation: "JSON-LD formatında Organization, WebSite veya BreadcrumbList şeması ekleyin.",
			})
		}
	}

	// 4. SSRF koruma kontrolü
	ssrfResult := s.checkSSRFProtection(websiteURL)
	result.SSRFCheck = ssrfResult

	// Genel skor hesapla
	result.OverallScore = s.computeOverallScore(result)

	// Varsayılan başarı durumu
	if botResult != nil && botResult.Accessible {
		result.Issues = append(result.Issues, Issue{
			Severity:       "info",
			Category:       "bot_access",
			Title:          "AI botları siteye erişebiliyor",
			Detail:         fmt.Sprintf("Site %d HTTP kodu döndü, %d ms içinde yanıt verdi.", botResult.StatusCode, botResult.ResponseTimeMs),
			Recommendation: "Mevcut durumu koruyun. Yanıt süresini iyileştirmek için CDN kullanmayı değerlendirin.",
		})
	}

	return result, nil
}

// Save persists an audit result to the audit_results table.
// This should be called after WorkspaceID and TenantID are set on the result.
func (s *service) Save(result *AuditResult) error {
	if s.pool == nil {
		return nil
	}

	robotsJSON, _ := json.Marshal(result.RobotsTxtCheck)
	botJSON, _ := json.Marshal(result.BotAccessCheck)
	ssrJSON, _ := json.Marshal(result.SSRCheck)
	ssrfJSON, _ := json.Marshal(result.SSRFCheck)
	issuesJSON, _ := json.Marshal(result.Issues)
	resultJSON, _ := json.Marshal(result)

	_, err := s.pool.Exec(context.Background(), `
		INSERT INTO governance.audit_results
			(id, brand_id, workspace_id, tenant_id, brand_name, website_url,
			 overall_score, robots_txt, bot_access, ssr, ssrf, issues, raw_result, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9::jsonb, $10::jsonb, $11::jsonb, $12::jsonb, $13::jsonb, now())
	`, result.ID, result.BrandID, result.WorkspaceID, result.TenantID,
		result.BrandName, result.WebsiteURL,
		result.OverallScore,
		string(robotsJSON), string(botJSON), string(ssrJSON), string(ssrfJSON),
		string(issuesJSON), string(resultJSON))

	if err != nil {
		return fmt.Errorf("audit kaydetme: %w", err)
	}

	slog.Debug("audit result saved", "brand", result.BrandID, "score", result.OverallScore)
	return nil
}

// checkRobotsTxt fetches and parses robots.txt.
func (s *service) checkRobotsTxt(websiteURL string) *RobotsTxtCheck {
	baseURL := normalizeURL(websiteURL)
	robotsURL := baseURL + "/robots.txt"

	req, err := http.NewRequest("GET", robotsURL, nil)
	if err != nil {
		return &RobotsTxtCheck{Exists: false}
	}
	req.Header.Set("User-Agent", "GeoLens-Audit/1.0")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return &RobotsTxtCheck{Exists: false}
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return &RobotsTxtCheck{
			Exists: resp.StatusCode == http.StatusNotFound,
		}
	}

	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20)) // max 1MB
	return parseRobotsTxtContent(body, true)
}

// checkBotAccess tests AI bot accessibility.
func (s *service) checkBotAccess(websiteURL string) *BotAccessCheck {
	check := &BotAccessCheck{
		AIBotNamesTested: make([]string, 0, len(aiCrawlers)),
	}

	// İlk AI bot ile test et
	testAgent := aiCrawlers[0]
	check.AIBotNamesTested = append(check.AIBotNamesTested, testAgent.UserAgent)

	req, err := http.NewRequest("GET", websiteURL, nil)
	if err != nil {
		return check
	}
	req.Header.Set("User-Agent", testAgent.UserAgent)

	start := time.Now()
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return check
	}
	_, _ = io.Copy(io.Discard, resp.Body)
	resp.Body.Close()

	check.ResponseTimeMs = time.Since(start).Milliseconds()
	check.StatusCode = resp.StatusCode

	// 2xx veya 4xx (izin yok) erişim sayılır, 3xx redirect de
	check.Accessible = resp.StatusCode < 400 || resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusUnauthorized

	return check
}

// checkSSR checks server-side rendering signals.
func (s *service) checkSSR(websiteURL string) *SSRCheck {
	check := &SSRCheck{}

	req, err := http.NewRequest("GET", websiteURL, nil)
	if err != nil {
		return check
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; GeoLens-Audit/1.0)")
	req.Header.Set("Accept", "text/html,application/xhtml+xml")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return check
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20)) // max 1MB
	content := string(body)
	check.ContentLength = len(body)

	// Meta etiketleri kontrolü
	lowerContent := strings.ToLower(content)
	check.HasMetaTags = strings.Contains(lowerContent, "<title") &&
		(strings.Contains(lowerContent, "name=\"description\"") || strings.Contains(lowerContent, "name='description'"))

	// Open Graph kontrolü
	check.HasOGTags = strings.Contains(lowerContent, "property=\"og:") || strings.Contains(lowerContent, "property='og:")

	// Yapılandırılmış veri kontrolü
	check.HasStructuredData = strings.Contains(lowerContent, "application/ld+json") ||
		strings.Contains(lowerContent, "itemscope") ||
		strings.Contains(lowerContent, "itemtype=\"http")

	return check
}

// checkSSRFProtection checks security headers for SSRF protection.
func (s *service) checkSSRFProtection(websiteURL string) *SSRFCheck {
	check := &SSRFCheck{}

	req, err := http.NewRequest("GET", websiteURL, nil)
	if err != nil {
		return check
	}
	req.Header.Set("User-Agent", "GeoLens-Audit/1.0")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return check
	}
	defer resp.Body.Close()

	headers := resp.Header

	// Cloudflare kontrolü (CF-Ray, Server: cloudflare)
	check.HasCloudflare = headers.Get("CF-Ray") != "" ||
		strings.Contains(strings.ToLower(headers.Get("Server")), "cloudflare")

	// AWS security headers
	check.HasAWSSecurityHeaders = headers.Get("X-Amz-Request-Id") != "" ||
		headers.Get("X-Amz-Rid") != ""

	// Rate limit headers
	check.HasRateLimitHeaders = headers.Get("X-RateLimit-Limit") != "" ||
		headers.Get("X-RateLimit-Remaining") != "" ||
		headers.Get("Retry-After") != ""

	// CSP
	check.CSPPresent = headers.Get("Content-Security-Policy") != ""

	return check
}

// computeOverallScore calculates an overall audit score from individual checks.
func (s *service) computeOverallScore(result *AuditResult) float64 {
	var score float64 = 100

	// Robots.txt kritik hata
	if result.RobotsTxtCheck != nil && result.RobotsTxtCheck.DisallowedAll {
		score -= 40
	}

	// Bot erişim sorunu
	if result.BotAccessCheck != nil && !result.BotAccessCheck.Accessible {
		score -= 20
	}

	// SSR eksikleri
	if result.SSRCheck != nil {
		if !result.SSRCheck.HasMetaTags {
			score -= 10
		}
		if !result.SSRCheck.HasOGTags {
			score -= 5
		}
		if !result.SSRCheck.HasStructuredData {
			score -= 10
		}
	}

	// Güvenlik
	if result.SSRFCheck != nil {
		if !result.SSRFCheck.HasCloudflare {
			score -= 5
		}
		if !result.SSRFCheck.CSPPresent {
			score -= 5
		}
	}

	if score < 0 {
		score = 0
	}

	return score
}

// parseRobotsTxtContent parses raw robots.txt content into a RobotsTxtCheck.
// exists parameter indicates whether the file was found (HTTP 200).
func parseRobotsTxtContent(body []byte, exists bool) *RobotsTxtCheck {
	content := string(body)

	check := &RobotsTxtCheck{
		Exists:       exists,
		BlockedPaths: []string{},
		AllowsAIBots: true, // Varsayılan: AI botlarına izin var
	}

	lines := strings.Split(content, "\n")
	var currentAgent string

	for _, line := range lines {
		line = strings.TrimSpace(line)
		lineLower := strings.ToLower(line)

		if strings.HasPrefix(lineLower, "user-agent:") {
			currentAgent = strings.TrimSpace(line[11:])
		} else if strings.HasPrefix(lineLower, "disallow:") {
			path := strings.TrimSpace(line[9:])
			if currentAgent == "*" || currentAgent == "" {
				if path == "/" {
					check.DisallowedAll = true
				}
				check.BlockedPaths = append(check.BlockedPaths, path)
			}

			// AI bot kontrolü — eşleşen AI botu için Disallow: / varsa AllowsAIBots=false
			for _, crawler := range aiCrawlers {
				if strings.EqualFold(currentAgent, crawler.UserAgent) || strings.EqualFold(currentAgent, strings.Split(crawler.UserAgent, "/")[0]) {
					if path == "/" {
						check.AllowsAIBots = false
					}
				}
			}
		}
	}

	// Tüm botlar engellenmişse (User-agent: * Disallow: /) AI botları da engellenmiştir
	if check.DisallowedAll {
		check.AllowsAIBots = false
	}

	return check
}

// ---- Yardımcı Fonksiyonlar ----

func generateULID() string {
	return ulid.Make().String()
}

func normalizeURL(url string) string {
	if !strings.HasPrefix(url, "http://") && !strings.HasPrefix(url, "https://") {
		url = "https://" + url
	}
	parts := strings.Split(url, "/")
	if len(parts) >= 3 {
		return parts[0] + "//" + parts[2]
	}
	return url
}
