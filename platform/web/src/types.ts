export interface Score {
  id: string
  brand_name: string
  value: number
  ci_low: number
  ci_high: number
  fidelity_label: string
  engine_breakdown?: Record<string, number>
  freshness_at: string
}

export interface Brand {
  id: string
  name: string
  website_url: string
}

export interface Panel {
  id: string
  name: string
  is_active: boolean
}

export interface ScoreComponent {
  label: string
  value: number
  weight: number
  color: string
}

export interface ScoreHistory {
  brand_name: string
  brand_id: string
  scores: Score[]
}

export interface TrendDataPoint {
  date: string
  value: number
  ci_low: number
  ci_high: number
}
