package recommendation

// NG10 — İddia Dili Filtresi
//
// NG10, önerilerin kullanıcıya gösterilmeden önce iddia dili (claim language)
// açısından filtrelenmesini sağlar. Amaç: AI görünürlük önerilerinin
// abartılı, kanıtlanamaz veya yanıltıcı ifadeler içermemesini garanti altına almaktır.
//
// Üç kategori:
//   N  (Negative)  — İddialı / kanıtlanamaz ifade (gösterilmez)
//   NG (Nötr)      — Nötr / veriye dayalı ifade (gösterilir)
//   P  (Positive)  — Olumlu / eyleme yönelik ifade (gösterilir)
//
// Referans: NG10 standardı (GeoLens AI Visibility Framework)

// ClaimLang represents the NG10 claim language category.
type ClaimLang string

const (
	ClaimN  ClaimLang = "N"  // Negative: iddialı/kanıtlanamaz — filtrelenir
	ClaimNG ClaimLang = "NG" // Nötr: veriye dayalı, ölçülebilir — gösterilir
	ClaimP  ClaimLang = "P"  // Positive: eyleme yönelik, yapıcı — gösterilir
)

// NG10Rule defines a single NG10 classification rule.
// Her kural, bir önerinin hangi claim language kategorisine girdiğini belirler.
type NG10Rule struct {
	ID          string    `json:"id"`
	Category    ClaimLang `json:"category"`
	Keywords    []string  `json:"keywords"` // Tetikleyici kelimeler
	Patterns    []string  `json:"patterns"` // Regex benzeri desenler (basit string match)
	Description string    `json:"description"`
}

// Default NG10 rules.
// Bu kurallar, öneri metinlerindeki iddia dilini sınıflandırır.
var defaultNG10Rules = []NG10Rule{
	// === N (Negative) — İddialı / Kanıtlanamaz ===
	{
		ID:          "ng10-absolutes",
		Category:    ClaimN,
		Keywords:    []string{"kesin", "kesinlikle", "asla", "her zaman", "tamamen", "bütünüyle"},
		Description: "Kesinlik ifadeleri — kanıtlanamaz iddialar",
	},
	{
		ID:          "ng10-guarantees",
		Category:    ClaimN,
		Keywords:    []string{"garanti", "garanti eder", "kesin sonuç", "%100", "yüzde yüz"},
		Description: "Garanti ifadeleri — abartılı iddialar",
	},
	{
		ID:          "ng10-unsubstantiated",
		Category:    ClaimN,
		Keywords:    []string{"en iyi", "en büyük", "lider", "bir numara", "number one"},
		Description: "Kanıtlanamaz üstünlük ifadeleri",
	},
	{
		ID:          "ng10-superlatives",
		Category:    ClaimN,
		Keywords:    []string{"mükemmel", "kusursuz", "hatasız", "benzersiz", "eşsiz"},
		Description: "Abartılı üstünlük ifadeleri",
	},

	// === NG (Nötr) — Veriye Dayalı ===
	{
		ID:          "ng10-data-driven",
		Category:    ClaimNG,
		Keywords:    []string{"veri", "ölçüm", "skor", "puan", "istatistik", "rapor", "analiz"},
		Description: "Veriye dayalı nötr ifadeler",
	},
	{
		ID:          "ng10-observation",
		Category:    ClaimNG,
		Keywords:    []string{"tespit", "gözlem", "bulgu", "sonuç", "değerlendirme"},
		Description: "Gözleme dayalı nötr ifadeler",
	},
	{
		ID:          "ng10-trend",
		Category:    ClaimNG,
		Keywords:    []string{"trend", "değişim", "değişiklik", "fark", "karşılaştırma"},
		Description: "Trend ve değişim ifadeleri",
	},

	// === P (Positive) — Eyleme Yönelik ===
	{
		ID:          "ng10-actionable",
		Category:    ClaimP,
		Keywords:    []string{"öneri", "önerilir", "tavsiye", "yapabilirsiniz", "ekleyin", "iyileştirin"},
		Description: "Eyleme yönelik yapıcı ifadeler",
	},
	{
		ID:          "ng10-constructive",
		Category:    ClaimP,
		Keywords:    []string{"geliştirme", "iyileştirme", "optimizasyon", "strateji", "fırsat"},
		Description: "Geliştirme odaklı olumlu ifadeler",
	},
	{
		ID:          "ng10-recommendation",
		Category:    ClaimP,
		Keywords:    []string{"kontrol edin", "araştırın", "inceleyin", "değerlendirin", "düşünün"},
		Description: "Aksiyon çağrısı içeren yapıcı ifadeler",
	},
}

// NG10Filter provides NG10 claim language filtering for recommendations.
type NG10Filter struct {
	rules []NG10Rule
}

// NewNG10Filter creates a new NG10 filter with default rules.
func NewNG10Filter() *NG10Filter {
	rules := make([]NG10Rule, len(defaultNG10Rules))
	copy(rules, defaultNG10Rules)
	return &NG10Filter{rules: rules}
}

// Classify classifies a text according to NG10 claim language rules.
// İlk eşleşen kategori döndürülür. Öncelik sırası: N > NG > P.
// Hiçbir kural eşleşmezse varsayılan olarak NG (nötr) döner.
func (f *NG10Filter) Classify(text string) ClaimLang {
	if text == "" {
		return ClaimNG
	}

	textLower := toLowerTurkish(text)

	// Önce N (Negative) kurallarını kontrol et — en katı filtre
	for _, rule := range f.rules {
		if rule.Category != ClaimN {
			continue
		}
		if matchKeywords(textLower, rule.Keywords) {
			return ClaimN
		}
	}

	// Sonra P (Positive) kurallarını kontrol et
	for _, rule := range f.rules {
		if rule.Category != ClaimP {
			continue
		}
		if matchKeywords(textLower, rule.Keywords) {
			return ClaimP
		}
	}

	// NG (Nötr) kurallarını kontrol et
	for _, rule := range f.rules {
		if rule.Category != ClaimNG {
			continue
		}
		if matchKeywords(textLower, rule.Keywords) {
			return ClaimNG
		}
	}

	// Varsayılan: nötr
	return ClaimNG
}

// IsAllowed checks if a text passes the NG10 filter (only NG and P are allowed).
func (f *NG10Filter) IsAllowed(text string) bool {
	return f.Classify(text) != ClaimN
}

// FilterRecommendations filters out recommendations that don't pass NG10.
// Sadece NG (nötr) ve P (pozitif) kategorisindeki öneriler döndürülür.
func (f *NG10Filter) FilterRecommendations(recs []Recommendation) []Recommendation {
	if len(recs) == 0 {
		return recs
	}

	filtered := make([]Recommendation, 0, len(recs))
	for _, rec := range recs {
		// Önerinin başlık ve detayını kontrol et
		titleLang := f.Classify(rec.Title)
		detailLang := f.Classify(rec.Detail)

		// İkisi de N değilse geçir
		if titleLang != ClaimN && detailLang != ClaimN {
			filtered = append(filtered, rec)
		}
	}

	return filtered
}

// AddRule adds a custom NG10 rule to the filter.
func (f *NG10Filter) AddRule(rule NG10Rule) {
	f.rules = append(f.rules, rule)
}

// GetRules returns all registered NG10 rules.
func (f *NG10Filter) GetRules() []NG10Rule {
	rules := make([]NG10Rule, len(f.rules))
	copy(rules, f.rules)
	return rules
}

// ---- Yardımcı Fonksiyonlar ----

// matchKeywords checks if any keyword is contained in the text.
func matchKeywords(text string, keywords []string) bool {
	for _, kw := range keywords {
		if containsWord(text, kw) {
			return true
		}
	}
	return false
}

// containsWord checks if a word is contained in text (word boundary aware).
// Basit string contains ile çalışır, Türkçe karakterleri dikkate alır.
func containsWord(text, word string) bool {
	if len(word) == 0 {
		return false
	}
	return stringContains(text, word)
}

// stringContains is a simple strings.Contains replacement to avoid import.
func stringContains(s, substr string) bool {
	return len(s) >= len(substr) && searchString(s, substr)
}

// searchString performs a simple substring search.
func searchString(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

// toLowerTurkish converts a string to lowercase with Turkish character support.
// Rune tabanlı çalışır, çok baytlı UTF-8 karakterleri doğru işler.
func toLowerTurkish(s string) string {
	runes := []rune(s)
	for i, r := range runes {
		switch {
		case r >= 'A' && r <= 'Z':
			runes[i] = r + 32
		case r == 'İ':
			runes[i] = 'i'
		case r == 'I':
			runes[i] = 'ı'
		case r == 'Ş':
			runes[i] = 'ş'
		case r == 'Ç':
			runes[i] = 'ç'
		case r == 'Ü':
			runes[i] = 'ü'
		case r == 'Ö':
			runes[i] = 'ö'
		case r == 'Ğ':
			runes[i] = 'ğ'
		}
	}
	return string(runes)
}
