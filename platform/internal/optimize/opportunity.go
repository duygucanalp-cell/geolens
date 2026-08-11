// Package optimize — A3-4 (İP-07) Opportunity Scoring.
//
// Formula (0420 İP-07): OpportunityScore = Impact × Urgency × Confidence
//   - Impact    1-10 : müşterinin visibility'ine potansiyel etki
//   - Urgency   1-10 : aksiyon alınmazsa kaybın büyüme hızı
//   - Confidence 0-1 : tespitin doğruluk olasılığı
package optimize

import "math"

// ImpactInt maps the human "impact" string used in optimize.recommendations
// to a 1-10 scale used by OpportunityScore.
func ImpactInt(impact string) int {
	switch impact {
	case "critical":
		return 10
	case "high":
		return 9
	case "medium":
		return 6
	case "low":
		return 3
	default:
		return 5
	}
}

// UrgencyFromEffort derives a 1-10 urgency from the "effort" string: low
// effort is fast to fix, so urgency stays moderate; high effort justifies
// higher urgency (Kayıp büyümeden erken aksiyon).
func UrgencyFromEffort(effort string) int {
	switch effort {
	case "high":
		return 9
	case "medium":
		return 7
	case "low":
		return 4
	default:
		return 5
	}
}

// OpportunityScore computes the normalized opportunity score the same way
// the Python side (geolens.opportunity) does. Range: 0-100.
func OpportunityScore(impact, urgency int, confidence float64) float64 {
	score := float64(impact) * float64(urgency) * confidence
	return math.Round(score*100) / 100
}
