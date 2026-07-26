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
  critical: 'severity.critical',
  high: 'severity.high',
  medium: 'severity.medium',
  low: 'severity.low',
}

export const CATEGORY_LABELS: Record<string, string> = {
  visibility: 'category.visibility',
  content: 'category.content',
  technical: 'category.technical',
  competitor: 'category.competitor',
}

export const SEVERITY_COLORS: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#22c55e',
}

// R3: Runtime Guardrails
export interface GuardrailRule {
  id: string
  tenant_id: string
  name: string
  category: string
  pattern: string
  action: string
  severity: string
  enabled: boolean
  created_at: string
  updated_at: string
}

// R8: Agent Tracing
export interface Trace {
  trace_id: string
  agent_name: string
  workflow_name: string
  status: string
  total_steps: number
  completed_steps: number
  total_duration_ms: number
  started_at: string
  completed_at: string | null
}

export interface TraceDetail extends Trace {
  steps: TraceStep[]
}

export interface TraceStep {
  step_id: string
  step_name: string
  agent: string
  input: string
  output: string
  status: string
  duration_ms: number
  error_message?: string
  started_at?: string
  completed_at?: string
}

// R1: AI Registry
export interface RegistryEntity {
  id: string
  tenant_id: string
  entity_type: string
  name: string
  description: string
  version: string
  provider: string
  lifecycle_state: string
  risk_class: string
  owner: string
  documentation_url: string
  created_at: string
  updated_at: string
}

// R4: Policy Packs
export interface PolicyPack {
  id: string
  name: string
  framework: string
  description: string
  version: string
  enabled: boolean
  applied_at: string | null
  created_at: string
}

export interface PolicyControl {
  id: string
  pack_id: string
  control_id: string
  title: string
  description: string
  category: string
  status: string
  evidence: string
  due_date: string | null
}

// R5: Bias/Fairness
export interface BiasTest {
  id: string
  model_id: string
  metric_type: string
  fairness_score: number
  has_bias: boolean
  max_gap: number
  details: Record<string, unknown>
  recommendations: string[]
  created_at: string
}

// R7: Explainability
export interface ExplainResult {
  analysis_id?: string
  entity_id: string
  entity_name: string
  entity_type: string
  method: string
  base_value: number
  prediction: number
  feature_importance: Record<string, number>
  shap_values: { feature: string; value: number; shap: number; impact: string }[]
  interpretation: string
}

// R2: Shadow AI Discovery
export interface ScanResult {
  scan_id: string
  scan_type: string
  status: string
  total_found: number
  findings: DiscoveryFinding[]
}

export interface DiscoveryFinding {
  id: string
  resource_type: string
  resource_name: string
  resource_id: string
  provider: string
  region: string
  risk_level: string
  discovered_at: string
}

// R6: CI/CD Governance Gate
export interface GateCheckResult {
  check_id: string
  entity_id: string
  entity_type: string
  target_env: string
  decision: string
  passed: number
  total: number
  checks: { name: string; passed: boolean; details: string }[]
  checked_at: string
}

export interface GateHistoryEntry {
  id: string
  entity_id: string
  entity_type: string
  target_env: string
  decision: string
  passed_checks: number
  total_checks: number
  checked_at: string
}

// R11: Cost Analytics
export interface CostEntry {
  id: string
  engine_name: string
  model_name: string
  operation: string
  token_count: number
  cost_usd: number
  recorded_at: string
}

export interface CostSummary {
  period: string
  total_cost_usd: number
  total_tokens: number
  engine_breakdown: { engine: string; cost: number; tokens: number }[]
}

// R12: Usage Analytics
export interface UsageMetric {
  id: string
  endpoint: string
  method: string
  status_code: number
  latency_ms: number
  recorded_at: string
}

export interface UsageSummary {
  period: string
  total_requests: number
  error_rate_pct: number
  avg_latency_ms: number
  top_endpoints: { endpoint: string; hits: number; avg_latency_ms: number }[]
}

// R13: Optimization
export interface OptimizationRec {
  id: string
  category: string
  title: string
  description: string
  impact: string
  effort: string
  status: string
  score_potential: number
  created_at: string
}

// R14: Version Tracking
export interface VersionEntry {
  id: string
  entity_type: string
  entity_id: string
  entity_name: string
  old_version: string
  new_version: string
  change_notes: string
  changed_by: string
  created_at: string
}

// Generic paginated list response wrapper
export interface ListResponse<T> {
  data: T[]
  has_more: boolean
}

// R15: Incident Management
export interface Incident {
  id: string
  severity: string
  category: string
  title: string
  status: string
  source: string
  entity_id: string
  assigned_to: string
  severity_score: number
  occurred_at: string
  resolved_at: string | null
  created_at: string
}

export interface IncidentListResponse {
  incidents: Incident[]
  count: number
  has_more: boolean
  open_count: number
  critical_count: number
}

// Onboarding Wizard
export interface SetupStep {
  key: string
  label: string
  done: boolean
}

export interface SetupStatus {
  setup_complete: boolean
  steps: SetupStep[]
}

export const ENGINE_COLORS: Record<string, string> = {
  perplexity: '#6366f1',
  chatgpt: '#10b981',
  gemini: '#f59e0b',
  claude: '#ef4444',
  grok: '#22c55e',
  copilot: '#a855f7',
}

export const ENGINE_NAMES: Record<string, string> = {
  perplexity: 'engine.perplexity',
  chatgpt: 'engine.chatgpt',
  gemini: 'engine.gemini',
  claude: 'engine.claude',
  grok: 'engine.grok',
  copilot: 'engine.copilot',
}
