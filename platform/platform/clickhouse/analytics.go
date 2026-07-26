package clickhouse

import (
	"context"
	"encoding/json"
)

type ScoreAnalytics struct {
	Date             string  `json:"date"`
	AvgScore         float64 `json:"avg_score"`
	MaxScore         float64 `json:"max_score"`
	MinScore         float64 `json:"min_score"`
	MeasurementCount int     `json:"measurement_count"`
}

type TenantAnalytics struct {
	TenantID          string  `json:"tenant_id"`
	ActiveBrands      int     `json:"active_brands"`
	TotalMeasurements int     `json:"total_measurements"`
	AvgScore          float64 `json:"avg_score"`
}

type AnalyticsClient struct {
	*Client
}

func NewAnalyticsClient(client *Client) *AnalyticsClient {
	return &AnalyticsClient{Client: client}
}

func (a *AnalyticsClient) GetScoreTrends(ctx context.Context, tenantID, brandID string, days int) ([]ScoreAnalytics, error) {
	result, err := a.Query(ctx, `
		SELECT
			toDate(freshness_at) AS date,
			avg(value) AS avg_score,
			max(value) AS max_score,
			min(value) AS min_score,
			count() AS measurement_count
		FROM measure.scores
		WHERE tenant_id = ? AND brand_id = ? AND freshness_at >= now() - INTERVAL ? DAY
		GROUP BY date
		ORDER BY date ASC
	`, tenantID, brandID, days)
	if err != nil {
		return nil, err
	}

	analytics := make([]ScoreAnalytics, 0, len(result.Data))
	for _, d := range result.Data {
		var s ScoreAnalytics
		if err := json.Unmarshal(d, &s); err != nil {
			continue
		}
		analytics = append(analytics, s)
	}
	return analytics, nil
}

func (a *AnalyticsClient) GetTenantComparison(ctx context.Context, tenantID string) ([]TenantAnalytics, error) {
	result, err := a.Query(ctx, `
		SELECT
			tenant_id,
			countDistinct(brand_id) AS active_brands,
			count() AS total_measurements,
			avg(value) AS avg_score
		FROM measure.scores
		WHERE freshness_at >= now() - INTERVAL 30 DAY
		GROUP BY tenant_id
		ORDER BY avg_score DESC
	`)
	if err != nil {
		return nil, err
	}

	analytics := make([]TenantAnalytics, 0, len(result.Data))
	for _, d := range result.Data {
		var t TenantAnalytics
		if err := json.Unmarshal(d, &t); err != nil {
			continue
		}
		analytics = append(analytics, t)
	}
	return analytics, nil
}
