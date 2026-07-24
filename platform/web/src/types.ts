export interface Score {
  id: string
  brand_name: string
  brand_id?: string
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
  description?: string
  is_active: boolean
}

export interface AuditResult {
  id: string
  brand_id: string
  brand_name: string
  website_url: string
  overall_score: number
  robots_txt?: {
    exists: boolean
    allows_ai_bots: boolean
    blocked_paths: string[]
    disallowed_all: boolean
  }
  bot_access?: {
    accessible: boolean
    status_code: number
    response_time_ms: number
    ai_bot_names_tested: string[]
  }
  ssr?: {
    has_meta_tags: boolean
    has_og_tags: boolean
    has_structured_data: boolean
    content_length: number
  }
  ssrf?: {
    has_cloudflare: boolean
    has_aws_security_headers: boolean
    has_rate_limit_headers: boolean
    csp_present: boolean
  }
  issues: AuditIssue[]
  created_at: string
}

export interface AuditIssue {
  severity: 'critical' | 'high' | 'medium' | 'low' | 'info'
  category: string
  title: string
  detail: string
  recommendation?: string
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

export const ENGINE_COLORS: Record<string, string> = {
  perplexity: '#6366f1',
  chatgpt: '#10b981',
  gemini: '#f59e0b',
}

export const ENGINE_NAMES: Record<string, string> = {
  perplexity: 'Perplexity',
  chatgpt: 'ChatGPT',
  gemini: 'Gemini',
}
