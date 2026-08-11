package optimize

import "testing"

func TestOpportunityScoreFormula(t *testing.T) {
	got := OpportunityScore(9, 8, 0.95)
	if got != 68.4 {
		t.Fatalf("expected 68.4, got %v", got)
	}
}

func TestOpportunityScoreRange(t *testing.T) {
	lo := OpportunityScore(1, 1, 0.0)
	hi := OpportunityScore(10, 10, 1.0)
	if lo < 0 || hi > 100 {
		t.Fatalf("score out of range: lo=%v hi=%v", lo, hi)
	}
	if hi != 100 {
		t.Fatalf("expected 100, got %v", hi)
	}
}

func TestImpactInt(t *testing.T) {
	cases := map[string]int{"critical": 10, "high": 9, "medium": 6, "low": 3, "unknown": 5}
	for in, want := range cases {
		if got := ImpactInt(in); got != want {
			t.Fatalf("ImpactInt(%q)=%d want %d", in, got, want)
		}
	}
}

func TestUrgencyFromEffort(t *testing.T) {
	cases := map[string]int{"high": 9, "medium": 7, "low": 4, "unknown": 5}
	for in, want := range cases {
		if got := UrgencyFromEffort(in); got != want {
			t.Fatalf("UrgencyFromEffort(%q)=%d want %d", in, got, want)
		}
	}
}

func TestAnalyzeUsesOpportunityScore(t *testing.T) {
	h := NewHandler(nil)
	recs := h.analyze(0)
	if len(recs) == 0 {
		t.Fatal("no recommendations")
	}
	// score_potential değeri Impact×Urgency×Confidence ürünü olmalı (0-100).
	for i, rec := range recs {
		v, ok := rec["score_potential"].(float64)
		if !ok {
			t.Fatalf("rec[%d] score_potential type %T", i, rec["score_potential"])
		}
		if v <= 0 || v > 100 {
			t.Fatalf("rec[%d] score_potential out of range: %v", i, v)
		}
	}
}
