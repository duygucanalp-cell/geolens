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

export interface NotificationSettings {
  workspace_id: string
  email_address: string
  digest_enabled: boolean
  digest_day: string
  digest_time: string
  digest_format: string
  notify_on_drop: boolean
  drop_threshold: number
}

export interface ReportSummary {
  id: string
  type: string
  file_name: string
  page_count: number
  generated_at: string
}

export interface Recommendation {
  id: string
  tenant_id: string
  workspace_id: string
  brand_id: string
  category: 'visibility' | 'content' | 'technical' | 'competitor'
  severity: 'critical' | 'high' | 'medium' | 'low'
  title: string
  detail: string
  action_url?: string
  score: number
  applied: boolean
  dismissed: boolean
  created_at: string
}

export const SEVERITY_LABELS: Record<string, string> = {
  critical: 'Kritik',
  high: 'Yüksek',
  medium: 'Orta',
  low: 'Düşük',
}

export const CATEGORY_LABELS: Record<string, string> = {
  visibility: 'Görünürlük',
  content: 'İçerik',
  technical: 'Teknik',
  competitor: 'Rakip',
}

export const SEVERITY_COLORS: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#22c55e',
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
