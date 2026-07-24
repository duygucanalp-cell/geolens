package recommendation

import (
	"testing"
	"time"
)

// ---- Test Context Builders ----

func testContext(score *ScoreSnapshot, audit *AuditSnapshot) *EvaluationContext {
	return &EvaluationContext{
		BrandID:     "B01",
		BrandName:   "Acme",
		WorkspaceID: "WS01",
		TenantID:    "T01",
		Score:       score,
		Audit:       audit,
	}
}

func testScore(value, previous float64, breakdown map[string]float64) *ScoreSnapshot {
	now := time.Now()
	s := &ScoreSnapshot{
		Value:           value,
		FreshnessAt:     now,
		EngineBreakdown: breakdown,
	}
	if previous != 0 {
		s.PreviousValue = previous
		s.PreviousAt = now.Add(-24 * time.Hour)
	}
	return s
}

func testRule(id string, conditions []Condition) Rule {
	return Rule{
		ID:         id,
		Name:       "Test Rule",
		Active:     true,
		Conditions: conditions,
		Category:   CategoryVisibility,
		Severity:   SeverityMedium,
		Title:      "Test Recommendation",
		Detail:     "Test detail",
	}
}

// ---- Service Tests (Evaluate conditions via helper) ----

func TestEvaluate_All_ReturnsAllRulesWhenNoScore(t *testing.T) {
	// When no score data exists, no rules should fire
	svc := &service{rules: defaultRules}

	ctx := testContext(&ScoreSnapshot{}, nil)
	results := svc.evaluateBrand(ctx)

	if len(results) != 0 {
		t.Errorf("expected 0 results when no score, got %d", len(results))
	}
}

func TestEvaluate_ScoreDropFires(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(50, 70, nil), nil) // dropped from 70 to 50

	results := svc.evaluateBrand(ctx)

	found := false
	for _, r := range results {
		if r.Title == "Görünürlük skorunuz düşüyor" {
			found = true
			break
		}
	}
	if !found {
		t.Error("score drop rule should fire when score drops from 70 to 50")
	}
}

func TestEvaluate_ScoreDropNotFires(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(65, 70, nil), nil) // dropped from 70 to 65 (only 5)

	results := svc.evaluateBrand(ctx)

	for _, r := range results {
		if r.Title == "Görünürlük skorunuz düşüyor" {
			t.Error("score drop rule should NOT fire when drop is only 5 points")
		}
	}
}

func TestEvaluate_TrendDeclineFires(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(55, 70, nil), nil) // downward trend

	results := svc.evaluateBrand(ctx)

	found := false
	for _, r := range results {
		if r.Title == "Görünürlük trendiniz geriliyor" {
			found = true
			break
		}
	}
	if !found {
		t.Error("trend decline rule should fire when trend is declining (55 vs 70)")
	}
}

func TestEvaluate_TrendRisingNotFires(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(78, 65, nil), nil) // upward trend

	results := svc.evaluateBrand(ctx)

	for _, r := range results {
		if r.Title == "Görünürlük trendiniz geriliyor" {
			t.Error("trend decline rule should NOT fire when trend is rising")
		}
	}
}

func TestEvaluate_EngineGapFires(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(65, 0, map[string]float64{
		"perplexity": 85,
		"chatgpt":    45,
		"gemini":     80,
	}), nil) // gap: 85-45 = 40 > 30

	results := svc.evaluateBrand(ctx)

	found := false
	for _, r := range results {
		if r.Title == "Motorlar arasında büyük performans farkı var" {
			found = true
			break
		}
	}
	if !found {
		t.Error("engine gap rule should fire when gap is 40 (> 30)")
	}
}

func TestEvaluate_EngineGapNotFires(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(65, 0, map[string]float64{
		"perplexity": 72,
		"chatgpt":    68,
	}), nil) // gap: 72-68 = 4 < 30

	results := svc.evaluateBrand(ctx)

	for _, r := range results {
		if r.Title == "Motorlar arasında büyük performans farkı var" {
			t.Error("engine gap rule should NOT fire when gap is only 4")
		}
	}
}

func TestEvaluate_MultipleRules(t *testing.T) {
	svc := &service{rules: defaultRules}
	// Score 50 -> 30 (drop of 20 > 10, trend declining)
	ctx := testContext(testScore(30, 50, map[string]float64{
		"perplexity": 35,
		"chatgpt":    25,
	}), nil)

	results := svc.evaluateBrand(ctx)

	if len(results) < 2 {
		t.Errorf("expected multiple rules to fire, got %d", len(results))
	}
}

func TestEvaluate_ConfidenceScore(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(testScore(50, 70, nil), nil)

	results := svc.evaluateBrand(ctx)
	if len(results) == 0 {
		t.Fatal("expected at least one result")
	}

	for _, r := range results {
		if r.Score < 0 || r.Score > 100 {
			t.Errorf("confidence score %f should be between 0 and 100", r.Score)
		}
	}
}

// ---- RegisterCustomRule Tests ----

func TestRegisterCustomRule(t *testing.T) {
	svc := &service{rules: make([]Rule, 0)}

	rule := Rule{
		Name:   "Custom Rule",
		Active: false, // should be forced to true
	}
	err := svc.RegisterCustomRule(rule)
	if err != nil {
		t.Fatalf("RegisterCustomRule failed: %v", err)
	}

	if len(svc.rules) != 1 {
		t.Errorf("expected 1 rule, got %d", len(svc.rules))
	}
	if !svc.rules[0].Active {
		t.Error("registered rule should be active")
	}
	if svc.rules[0].ID == "" {
		t.Error("registered rule should have an ID")
	}
}

func TestRegisterCustomRule_PreservesID(t *testing.T) {
	svc := &service{rules: make([]Rule, 0)}

	rule := Rule{
		ID:     "my-custom-rule",
		Name:   "Custom Rule",
		Active: false,
	}
	err := svc.RegisterCustomRule(rule)
	if err != nil {
		t.Fatalf("RegisterCustomRule failed: %v", err)
	}

	if svc.rules[0].ID != "my-custom-rule" {
		t.Errorf("expected ID to be preserved, got %s", svc.rules[0].ID)
	}
}

// ---- Helper Function Tests ----

func TestToFloat64(t *testing.T) {
	tests := []struct {
		input    interface{}
		expected float64
	}{
		{float64(42.5), 42.5},
		{int(42), 42.0},
		{int64(42), 42.0},
		{string("hello"), 0.0},
		{nil, 0.0},
	}

	for _, tc := range tests {
		result := toFloat64(tc.input)
		if result != tc.expected {
			t.Errorf("toFloat64(%v) = %f, want %f", tc.input, result, tc.expected)
		}
	}
}

func TestCompareFloat(t *testing.T) {
	tests := []struct {
		actual   float64
		operator string
		expected float64
		want     bool
	}{
		{10, "gt", 5, true},
		{3, "gt", 5, false},
		{3, "lt", 5, true},
		{10, "lt", 5, false},
		{5, "eq", 5, true},
		{6, "eq", 5, false},
		{5, "gte", 5, true},
		{6, "gte", 5, true},
		{4, "gte", 5, false},
		{5, "lte", 5, true},
		{4, "lte", 5, true},
		{6, "lte", 5, false},
		{10, "unknown", 5, false},
	}

	for _, tc := range tests {
		result := compareFloat(tc.actual, tc.operator, tc.expected)
		if result != tc.want {
			t.Errorf("compareFloat(%f, %q, %f) = %v, want %v", tc.actual, tc.operator, tc.expected, result, tc.want)
		}
	}
}

func TestGenerateULID(t *testing.T) {
	id1 := generateULID()
	id2 := generateULID()

	if id1 == "" {
		t.Error("ULID should not be empty")
	}
	if id1 == id2 {
		t.Error("consecutive ULIDs should be unique")
	}
}

// ---- GetRules Test ----

func TestGetRules(t *testing.T) {
	svc := &service{rules: defaultRules}
	rules := svc.GetRules()

	if len(rules) != len(defaultRules) {
		t.Errorf("expected %d rules, got %d", len(defaultRules), len(rules))
	}
}

// ---- EvaluateCondition Edge Cases ----

func TestEvaluateCondition_EmptyConditions(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(nil, nil)

	// Empty conditions should return true
	if !svc.evaluateConditions(ctx, []Condition{}) {
		t.Error("empty conditions should evaluate to true")
	}
}

func TestEvaluateCondition_NilScore(t *testing.T) {
	svc := &service{rules: defaultRules}
	ctx := testContext(nil, nil)

	cond := []Condition{{Field: "score.drop", Operator: "gt", Value: 10.0}}
	if svc.evaluateConditions(ctx, cond) {
		t.Error("conditions should not match when score is nil")
	}
}

func TestEvaluateCondition_NoPreviousScore(t *testing.T) {
	svc := &service{rules: defaultRules}
	// Score has value but no previous value
	ctx := testContext(&ScoreSnapshot{Value: 50, FreshnessAt: time.Now()}, nil)

	// score.drop needs previous value
	cond := []Condition{{Field: "score.trend", Operator: "eq", Value: "declining"}}
	if svc.evaluateConditions(ctx, cond) {
		t.Error("trend condition should not match when no previous score")
	}

	// engine_gap needs breakdown
	cond2 := []Condition{{Field: "score.engine_gap", Operator: "gt", Value: 30.0}}
	if svc.evaluateConditions(ctx, cond2) {
		t.Error("engine_gap condition should not match when no breakdown")
	}
}
