package recommendation

import "testing"

// ---- Classify Tests ----

func TestClassify_Negative_Absolutes(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Bu kesin bir sonuçtur", ClaimN},
		{"Kesinlikle doğru bir tespit", ClaimN},
		{"Bu asla değişmez", ClaimN},
		{"Her zaman böyle olur", ClaimN},
		{"Tamamen doğru bir analiz", ClaimN},
		{"Bütünüyle yanlış bir yaklaşım", ClaimN},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Negative_Guarantees(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Bu garanti bir sonuçtur", ClaimN},
		{"Garanti ederiz ki skor yükselir", ClaimN},
		{"Kesin sonuç alırsınız", ClaimN},
		{"%100 başarı oranı", ClaimN},
		{"Yüzde yüz memnuniyet", ClaimN},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Negative_Unsubstantiated(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"En iyi çözüm budur", ClaimN},
		{"En büyük firma", ClaimN},
		{"Sektör lideri konumunda", ClaimN},
		{"Bir numara strateji", ClaimN},
		{"Number one tercih", ClaimN},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Negative_Superlatives(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Mükemmel bir sonuç", ClaimN},
		{"Kusursuz bir strateji", ClaimN},
		{"Hatasız bir uygulama", ClaimN},
		{"Benzersiz bir fırsat", ClaimN},
		{"Eşsiz bir çözüm", ClaimN},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Neutral_DataDriven(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Veri analizi yapıldı", ClaimNG},
		{"Ölçüm sonuçları değerlendirildi", ClaimNG},
		{"Skor değeri 75 olarak hesaplandı", ClaimNG},
		{"Puan ortalaması yükseldi", ClaimNG},
		{"İstatistiksel olarak anlamlı", ClaimNG},
		{"Rapor sonuçları incelendi", ClaimNG},
		{"Analiz tamamlandı", ClaimNG},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Neutral_Observation(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Tespit edilen bulgular", ClaimNG},
		{"Gözlem sonuçları", ClaimNG},
		{"Bulgu raporu hazırlandı", ClaimNG},
		{"Sonuç değerlendirmesi", ClaimNG},
		{"Değerlendirme tamamlandı", ClaimNG},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Neutral_Trend(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Trend analizi yapıldı", ClaimNG},
		{"Değişim oranı hesaplandı", ClaimNG},
		{"Değişiklik tespit edildi", ClaimNG},
		{"Fark analizi tamamlandı", ClaimNG},
		{"Karşılaştırma sonuçları", ClaimNG},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Positive_Actionable(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Öneri: içerik stratejinizi gözden geçirin", ClaimP},
		{"Önerilir: yapılandırılmış veri ekleyin", ClaimP},
		{"Tavsiye: rakip analizi yapın", ClaimP},
		{"Yapabilirsiniz: skorunuzu iyileştirin", ClaimP},
		{"Ekleyin: JSON-LD yapılandırması", ClaimP},
		{"İyileştirin: bot erişim ayarlarını", ClaimP},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Positive_Constructive(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Geliştirme fırsatı mevcut", ClaimP},
		{"İyileştirme önerisi", ClaimP},
		{"Optimizasyon stratejisi", ClaimP},
		{"Strateji değişikliği önerilir", ClaimP},
		{"Fırsat analizi yapıldı", ClaimP},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_Positive_Recommendation(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"Kontrol edin: robots.txt ayarlarınızı", ClaimP},
		{"Araştırın: rakip stratejilerini", ClaimP},
		{"İnceleyin: motor bazlı skorları", ClaimP},
		{"Değerlendirin: içerik stratejinizi", ClaimP},
		{"Düşünün: yeni bir yaklaşım", ClaimP},
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

func TestClassify_EmptyString(t *testing.T) {
	f := NewNG10Filter()
	got := f.Classify("")
	if got != ClaimNG {
		t.Errorf("Classify('') = %v, want NG", got)
	}
}

func TestClassify_DefaultNeutral(t *testing.T) {
	f := NewNG10Filter()
	// Hiçbir kurala uymayan metin varsayılan olarak NG dönmeli
	got := f.Classify("Bugün hava çok güzel.")
	if got != ClaimNG {
		t.Errorf("Classify('Bugün hava çok güzel.') = %v, want NG", got)
	}
}

func TestClassify_NegativePriority(t *testing.T) {
	f := NewNG10Filter()
	// N (Negative) her zaman önceliklidir
	// "kesin" N kuralını, "öneri" P kuralını tetikler — N önce gelmeli
	got := f.Classify("Kesin öneri: stratejinizi değiştirin")
	if got != ClaimN {
		t.Errorf("Classify should return N (negative has priority), got %v", got)
	}
}

// ---- IsAllowed Tests ----

func TestIsAllowed_Negative(t *testing.T) {
	f := NewNG10Filter()
	if f.IsAllowed("Kesin sonuç") {
		t.Error("IsAllowed should return false for negative claim language")
	}
}

func TestIsAllowed_Neutral(t *testing.T) {
	f := NewNG10Filter()
	if !f.IsAllowed("Veri analizi sonuçları") {
		t.Error("IsAllowed should return true for neutral claim language")
	}
}

func TestIsAllowed_Positive(t *testing.T) {
	f := NewNG10Filter()
	if !f.IsAllowed("Öneri: stratejinizi gözden geçirin") {
		t.Error("IsAllowed should return true for positive claim language")
	}
}

// ---- FilterRecommendations Tests ----

func TestFilterRecommendations_AllAllowed(t *testing.T) {
	f := NewNG10Filter()
	recs := []Recommendation{
		{Title: "Veri analizi sonuçları", Detail: "Skor değerlendirmesi yapıldı"},
		{Title: "Öneri: stratejinizi gözden geçirin", Detail: "İyileştirme fırsatı mevcut"},
	}

	filtered := f.FilterRecommendations(recs)
	if len(filtered) != 2 {
		t.Errorf("expected 2 recommendations, got %d", len(filtered))
	}
}

func TestFilterRecommendations_SomeFiltered(t *testing.T) {
	f := NewNG10Filter()
	recs := []Recommendation{
		{Title: "Veri analizi sonuçları", Detail: "Skor değerlendirmesi yapıldı"},
		{Title: "Kesin sonuç garantisi", Detail: "En iyi çözüm budur"},
		{Title: "Öneri: stratejinizi gözden geçirin", Detail: "İyileştirme fırsatı mevcut"},
	}

	filtered := f.FilterRecommendations(recs)
	if len(filtered) != 2 {
		t.Errorf("expected 2 recommendations (1 filtered), got %d", len(filtered))
	}
}

func TestFilterRecommendations_Empty(t *testing.T) {
	f := NewNG10Filter()
	filtered := f.FilterRecommendations(nil)
	if filtered == nil {
		t.Error("expected empty slice, got nil")
	}
	if len(filtered) != 0 {
		t.Errorf("expected 0 recommendations, got %d", len(filtered))
	}
}

func TestFilterRecommendations_AllFiltered(t *testing.T) {
	f := NewNG10Filter()
	recs := []Recommendation{
		{Title: "Kesin sonuç", Detail: "En iyi çözüm"},
		{Title: "Mükemmel strateji", Detail: "Garanti başarı"},
	}

	filtered := f.FilterRecommendations(recs)
	if len(filtered) != 0 {
		t.Errorf("expected 0 recommendations (all filtered), got %d", len(filtered))
	}
}

// ---- AddRule Tests ----

func TestAddRule(t *testing.T) {
	f := NewNG10Filter()
	initialCount := len(f.GetRules())

	f.AddRule(NG10Rule{
		ID:          "test-rule",
		Category:    ClaimN,
		Keywords:    []string{"test"},
		Description: "Test rule",
	})

	rules := f.GetRules()
	if len(rules) != initialCount+1 {
		t.Errorf("expected %d rules, got %d", initialCount+1, len(rules))
	}

	// Yeni kural çalışmalı
	if f.Classify("Bu bir test mesajıdır") != ClaimN {
		t.Error("new rule should classify 'test' as N")
	}
}

// ---- Turkish Character Tests ----

func TestClassify_TurkishCharacters(t *testing.T) {
	f := NewNG10Filter()

	tests := []struct {
		text string
		want ClaimLang
	}{
		{"İSTİSTATİKSEL ANALİZ", ClaimNG},   // Büyük harf İ ile
		{"Şirket lideri konumunda", ClaimN}, // Ş ile
		{"Çözüm önerisi", ClaimP},           // Ç ile
		{"Üstün kalite", ClaimN},            // Ü ile
		{"Ölçüm sonuçları", ClaimNG},        // Ö ile
		{"Değerlendirme raporu", ClaimNG},   // Ğ ile
	}

	for _, tc := range tests {
		got := f.Classify(tc.text)
		if got != tc.want {
			t.Errorf("Classify(%q) = %v, want %v", tc.text, got, tc.want)
		}
	}
}

// ---- toLowerTurkish Tests ----

func TestToLowerTurkish(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"İSTANBUL", "istanbul"},
		{"IŞIK", "ışık"},
		{"ŞİŞLİ", "şişli"},
		{"ÇANKAYA", "çankaya"},
		{"ÜSKÜDAR", "üsküdar"},
		{"ÖDEMİŞ", "ödeniş"},
		{"Ğ", "ğ"},
		{"Merhaba Dünya", "merhaba dünya"},
		{"", ""},
	}

	for _, tc := range tests {
		got := toLowerTurkish(tc.input)
		if got != tc.want {
			t.Errorf("toLowerTurkish(%q) = %q, want %q", tc.input, got, tc.want)
		}
	}
}

// ---- matchKeywords Tests ----

func TestMatchKeywords(t *testing.T) {
	tests := []struct {
		text     string
		keywords []string
		want     bool
	}{
		{"kesin sonuç", []string{"kesin"}, true},
		{"nötr metin", []string{"kesin"}, false},
		{"", []string{"test"}, false},
		{"çoklu anahtar kelime testi", []string{"anahtar", "test"}, true},
		{"hiçbiri", []string{"yok", "bulunamadı"}, false},
	}

	for _, tc := range tests {
		got := matchKeywords(tc.text, tc.keywords)
		if got != tc.want {
			t.Errorf("matchKeywords(%q, %v) = %v, want %v", tc.text, tc.keywords, got, tc.want)
		}
	}
}
