import i18n from '../i18n'
import type { Score, Brand, Panel, AuditResult } from '../types'

const BASE = '/v1'

// ApiError, HTTP durum kodunu taşıyan API hatasıdır.
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

// Oturum süresi dolduğunda (401) tetiklenen global geri çağırma.
// App bileşeni bu callback'i kaydederek logout + login yönlendirmesi yapar.
type SessionExpiredHandler = () => void
let sessionExpiredHandler: SessionExpiredHandler | null = null

/**
 * 401 (oturum süresi doldu) durumunda çağrılacak handler'ı kaydeder.
 * Bileşen unmount olduğunda null geçirerek kaydı temizleyin.
 */
export function onSessionExpired(handler: SessionExpiredHandler | null) {
  sessionExpiredHandler = handler
}

function notifySessionExpired() {
  sessionExpiredHandler?.()
}

// 403 (yetki yetersiz) durumunda global toast gösterecek handler.
type PermissionDeniedHandler = (message: string) => void
let permissionDeniedHandler: PermissionDeniedHandler | null = null

export function onPermissionDenied(handler: PermissionDeniedHandler | null) {
  permissionDeniedHandler = handler
}

function notifyPermissionDenied(message: string) {
  permissionDeniedHandler?.(message)
}

// Auth endpoint'leri: bu uçlarda 401 'geçersiz kimlik bilgisi' anlamına gelir,
// oturum süresinin dolması DEĞİL. Bu yüzden oturum kapatma tetiklenmez.
const AUTH_ENDPOINTS = [
  `${BASE}/auth/login`,
  `${BASE}/auth/register`,
  `${BASE}/auth/accept-invitation`,
]

// 403 (yetki yetersiz) hatası mı? — panel içi gösterim kararlarında kullanılır.
// 403'te hata mesajı bilinçli olarak boştur; gösterimi global toast yönetir.
export function isPermissionDenied(err: unknown): boolean {
  return err instanceof ApiError && err.status === 403
}

// Bilinen API hata kodlarını kullanıcı dostu mesajlara çevirir.
// Sunucu tarafından okunabilir bir mesaj gelirse (örn. login hatası) olduğu gibi gösterilir.
const FRIENDLY_ERRORS: Record<string, string> = {
  authentication_required: 'session.expired',
  authorization_required: 'session.expired',
  invalid_token: 'session.expired',
  insufficient_permissions: 'errors.permission_denied',
  workspace_access_denied: 'errors.workspace_access_denied',
  rate_limited: 'errors.rate_limited',
  plan_upgrade_required: 'errors.plan_upgrade_required',
}

function friendlyMessage(code: string): string {
  // Yalnızca bilinen makine kodlarını çevir; sunucunun verdiği
  // okunabilir mesaj (örn. "geçersiz e-posta veya şifre") olduğu gibi korunur.
  const key = FRIENDLY_ERRORS[code]
  if (key) {
    return i18n.t(key)
  }
  return code || i18n.t('api.error')
}

async function fetchJSON<T>(url: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(url, { ...init, headers })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    const code = err.error || res.statusText

    // Oturum süresi doldu: 401 + istek token ile yapılmış + auth endpoint'i değil
    if (res.status === 401 && token && !AUTH_ENDPOINTS.includes(url)) {
      notifySessionExpired()
    }

    // Yetki yetersiz (403): oturum kapanmaz, kullanıcıya global toast gösterilir.
    // Panel içi hata gösterimi yerine YALNIZCA toast kullanılır — fırlatılan hata
    // boş mesajlıdır; paneller {error && ...} kalıbıyla boş mesajı göstermez.
    if (res.status === 403 && !AUTH_ENDPOINTS.includes(url)) {
      notifyPermissionDenied(friendlyMessage(code))
      throw new ApiError(res.status, '')
    }

    throw new ApiError(res.status, friendlyMessage(code))
  }
  return res.json()
}

export interface AuthResponse {
  token: string
  expires_at: string
  user_id: string
  tenant_id: string
  workspace_id: string
  role: string
}

export function login(email: string, password: string) {
  return fetchJSON<AuthResponse>(
    `${BASE}/auth/login`,
    { method: 'POST', body: JSON.stringify({ email, password }) }
  )
}

export function register(email: string, password: string, name: string) {
  return fetchJSON<AuthResponse>(
    `${BASE}/auth/register`,
    { method: 'POST', body: JSON.stringify({ email, password, name }) }
  )
}

export function acceptInvitation(token: string, email: string, password: string, name: string) {
  return fetchJSON<AuthResponse>(
    `${BASE}/auth/accept-invitation`,
    { method: 'POST', body: JSON.stringify({ token, email, password, name }) }
  )
}

// Kayan oturum: geçerli token ile yeni bir JWT alır (süre dolmadan önce çağrılır).
// Token süresi dolmuşsa 401 döner → normal oturum sonu akışı tetiklenir.
export function refreshSession() {
  return fetchJSON<AuthResponse>(`${BASE}/auth/refresh`, { method: 'POST' })
}

// Sunucu tarafında token'ı blacklist'e ekler (fire-and-forget).
export function logout() {
  return fetchJSON<{ status: string }>(`${BASE}/auth/logout`, { method: 'POST' }).catch(() => null)
}

export function getScores(ws: string): Promise<Score[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/scores`)
}

export function getBrands(ws: string): Promise<Brand[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands`)
}

export function searchBrands(ws: string, query: string, exclude?: string, offset?: number, limit?: number): Promise<{ data: Brand[]; total: number; offset: number; limit: number }> {
  const excl = exclude ? `&exclude=${encodeURIComponent(exclude)}` : ''
  const off = offset !== undefined ? `&offset=${offset}` : ''
  const lim = limit !== undefined ? `&limit=${limit}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/search?q=${encodeURIComponent(query)}${excl}${off}${lim}`)
}

export function getPanels(ws: string): Promise<Panel[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/panels`)
}

export function triggerAudit(ws: string, brandId: string, brandName: string, websiteUrl: string): Promise<AuditResult> {
  return fetchJSON(`${BASE}/workspaces/${ws}/audit`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId, brand_name: brandName, website_url: websiteUrl }),
  })
}

export function getNotificationSettings(ws: string) {
  return fetchJSON<import('../types').NotificationSettings>(`${BASE}/workspaces/${ws}/notifications/settings`)
}

export function updateNotificationSettings(ws: string, settings: Partial<import('../types').NotificationSettings>) {
  return fetchJSON<import('../types').NotificationSettings>(`${BASE}/workspaces/${ws}/notifications/settings`, {
    method: 'PUT',
    body: JSON.stringify(settings),
  })
}

export function sendTestEmail(ws: string, email: string) {
  return fetchJSON<{ status: string; to: string }>(`${BASE}/workspaces/${ws}/notifications/test`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function getRecommendations(ws: string, brandId?: string) {
  const query = brandId ? `?brand_id=${brandId}` : ''
  return fetchJSON<import('../types').Recommendation[]>(`${BASE}/workspaces/${ws}/recommendations${query}`)
}

export function markRecommendationApplied(ws: string, recId: string) {
  return fetchJSON<{ status: string }>(`${BASE}/workspaces/${ws}/recommendations/${recId}/apply`, {
    method: 'POST',
  })
}

export function markRecommendationDismissed(ws: string, recId: string) {
  return fetchJSON<{ status: string }>(`${BASE}/workspaces/${ws}/recommendations/${recId}/dismiss`, {
    method: 'POST',
  })
}

// R3: Runtime Guardrails
export function getGuardrailRules(): Promise<{ rules: import('../types').GuardrailRule[] }> {
  return fetchJSON(`${BASE}/guardrails/rules`)
}

export function createGuardrailRule(data: {
  name: string
  category: string
  pattern: string
  action?: string
  severity?: string
}): Promise<import('../types').GuardrailRule> {
  return fetchJSON(`${BASE}/guardrails/rules`, { method: 'POST', body: JSON.stringify(data) })
}

export function toggleGuardrailRule(ruleId: string, enabled: boolean): Promise<{ status: string; enabled: string }> {
  return fetchJSON(`${BASE}/guardrails/rules/${ruleId}/toggle`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

export function deleteGuardrailRule(ruleId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/guardrails/rules/${ruleId}`, { method: 'DELETE' })
}

export function evaluateGuardrail(prompt: string, response?: string): Promise<{
  results: { rule_id: string; rule_name: string; category: string; matched: boolean; action_taken: string }[]
  blocked: boolean
  allowed: boolean
}> {
  return fetchJSON(`${BASE}/guardrails/evaluate`, {
    method: 'POST',
    body: JSON.stringify({ prompt, response: response || '' }),
  })
}

// R8: Agent Tracing
export function listTraces(status?: string): Promise<{ traces: import('../types').Trace[]; total: number }> {
  const query = status ? `?status=${status}` : ''
  return fetchJSON(`${BASE}/agents/traces${query}`)
}

export function getTrace(traceId: string): Promise<import('../types').TraceDetail> {
  return fetchJSON(`${BASE}/agents/traces/${traceId}`)
}

export function startTrace(agentName: string, workflowName?: string): Promise<{ trace_id: string; status: string }> {
  return fetchJSON(`${BASE}/agents/traces`, {
    method: 'POST',
    body: JSON.stringify({ agent_name: agentName, workflow_name: workflowName || '' }),
  })
}

// R1: AI Registry
export function listRegistryEntities(): Promise<{ entities: import('../types').RegistryEntity[] }> {
  return fetchJSON(`${BASE}/registry/entities`)
}

export function getRegistryEntity(entityId: string): Promise<import('../types').RegistryEntity> {
  return fetchJSON(`${BASE}/registry/entities/${entityId}`)
}

export function createRegistryEntity(data: {
  entity_type: string
  name: string
  description?: string
  version?: string
  provider?: string
  lifecycle_state?: string
  risk_class?: string
  owner?: string
}): Promise<import('../types').RegistryEntity> {
  return fetchJSON(`${BASE}/registry/entities`, { method: 'POST', body: JSON.stringify(data) })
}

export function deleteRegistryEntity(entityId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/registry/entities/${entityId}`, { method: 'DELETE' })
}

// R4: Policy Packs
export function listPolicyPacks(): Promise<{ packs: import('../types').PolicyPack[] }> {
  return fetchJSON(`${BASE}/policies/packs`)
}

export function listPolicyControls(packId: string): Promise<{ controls: import('../types').PolicyControl[] }> {
  return fetchJSON(`${BASE}/policies/packs/${packId}/controls`)
}

export function updatePolicyControl(controlId: string, data: { status: string; evidence?: string }): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/policies/controls/${controlId}`, { method: 'PUT', body: JSON.stringify(data) })
}

// R5: Bias/Fairness
export function evaluateBias(data: { model_id: string; metric_type: string; data: Record<string, number> }): Promise<{
  test_id: string; fairness_score: number; has_bias: boolean; results: Record<string, unknown>
}> {
  return fetchJSON(`${BASE}/bias/evaluate`, { method: 'POST', body: JSON.stringify(data) })
}

export async function listBiasTests(): Promise<import('../types').BiasTest[]> {
  const res = await fetchJSON<import('../types').ListResponse<import('../types').BiasTest>>(`${BASE}/bias/tests`)
  return res.data
}

// R7: Explainability
export function explainEntity(entityId: string): Promise<import('../types').ExplainResult> {
  return fetchJSON(`${BASE}/explain/${entityId}`, { method: 'POST' })
}

export async function listExplainResults(): Promise<import('../types').ExplainResult[]> {
  const res = await fetchJSON<import('../types').ListResponse<import('../types').ExplainResult>>(`${BASE}/explain/results`)
  return res.data
}

// R2: Shadow AI Discovery
export function startDiscoveryScan(scanType?: string): Promise<{ scan_id: string; status: string }> {
  return fetchJSON(`${BASE}/discovery/scan`, {
    method: 'POST',
    body: JSON.stringify({ scan_type: scanType || 'api' }),
  })
}

export function getScanResults(scanId: string): Promise<import('../types').ScanResult> {
  return fetchJSON(`${BASE}/discovery/scans/${scanId}`)
}

// R6: CI/CD Governance Gate
export function runGateCheck(entityId: string): Promise<import('../types').GateCheckResult> {
  return fetchJSON(`${BASE}/gate/check`, { method: 'POST', body: JSON.stringify({ entity_id: entityId }) })
}

export async function getGateHistory(entityId: string): Promise<import('../types').GateHistoryEntry[]> {
  const res = await fetchJSON<{ history: import('../types').GateHistoryEntry[]; total: number }>(`${BASE}/gate/history/${entityId}`)
  return res.history
}

// R16: LLM Red Teaming
export function listRedTeamCases(): Promise<{ cases: import('../types').RedTeamCase[] }> {
  return fetchJSON(`${BASE}/redteam/cases`)
}

export function createRedTeamCase(data: {
  name: string
  category: string
  payload: string
  attack_vector?: string
  severity?: string
}): Promise<import('../types').RedTeamCase> {
  return fetchJSON(`${BASE}/redteam/cases`, { method: 'POST', body: JSON.stringify(data) })
}

export function deleteRedTeamCase(caseId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/redteam/cases/${caseId}`, { method: 'DELETE' })
}

export function runRedTeam(targetName: string, targetPrompt: string): Promise<{
  run: import('../types').RedTeamRun
  results: import('../types').RedTeamResult[]
  total_cases: number
  passed: number
  failed: number
  defense_score: number
  status: string
}> {
  return fetchJSON(`${BASE}/redteam/runs`, {
    method: 'POST',
    body: JSON.stringify({ target_name: targetName, target_prompt: targetPrompt }),
  })
}

export function listRedTeamRuns(): Promise<{ runs: import('../types').RedTeamRun[]; total: number }> {
  return fetchJSON(`${BASE}/redteam/runs`)
}

export function getRedTeamRun(runId: string): Promise<{ run: import('../types').RedTeamRun; results: import('../types').RedTeamResult[] }> {
  return fetchJSON(`${BASE}/redteam/runs/${runId}`)
}

// R17: Drift Detection
export function recordDriftObservation(data: {
  entity_id: string
  entity_name?: string
  metric: string
  value: number
  window_start?: string
}): Promise<import('../types').DriftObservation> {
  return fetchJSON(`${BASE}/drift/record`, { method: 'POST', body: JSON.stringify(data) })
}

export function listDriftEntities(): Promise<{ entities: import('../types').DriftEntitySummary[] }> {
  return fetchJSON(`${BASE}/drift/entities`)
}

export function analyzeDrift(entityId: string, metric: string, threshold?: number): Promise<import('../types').DriftAnalysis> {
  const q = threshold !== undefined ? `&threshold=${threshold}` : ''
  return fetchJSON(`${BASE}/drift/analysis?entity_id=${encodeURIComponent(entityId)}&metric=${encodeURIComponent(metric)}${q}`)
}

export function listDriftObservations(entityId: string, metric: string, limit?: number): Promise<{ observations: import('../types').DriftObservation[]; total: number }> {
  const lim = limit !== undefined ? `&limit=${limit}` : ''
  return fetchJSON(`${BASE}/drift/observations?entity_id=${encodeURIComponent(entityId)}&metric=${encodeURIComponent(metric)}${lim}`)
}

export function listDriftAlerts(): Promise<{ alerts: import('../types').DriftAlert[]; total: number }> {
  return fetchJSON(`${BASE}/drift/alerts`)
}

// R11: Cost Analytics
export async function getCostEntries(engineFilter?: string): Promise<import('../types').CostEntry[]> {
  const query = engineFilter ? `?engine=${engineFilter}` : ''
  const res = await fetchJSON<import('../types').ListResponse<import('../types').CostEntry>>(`${BASE}/costs/entries${query}`)
  return res.data
}

export function recordCostEntry(data: {
  engine_name: string
  model_name?: string
  operation?: string
  token_count?: number
  cost_usd: number
}): Promise<{ entry_id: string; cost_usd: number }> {
  return fetchJSON(`${BASE}/costs/entries`, { method: 'POST', body: JSON.stringify(data) })
}

export function getCostSummary(period?: string): Promise<import('../types').CostSummary> {
  const query = period ? `?period=${period}` : ''
  return fetchJSON(`${BASE}/costs/summary${query}`)
}

// R12: Usage Analytics
export async function getUsageMetrics(): Promise<import('../types').UsageMetric[]> {
  const res = await fetchJSON<import('../types').ListResponse<import('../types').UsageMetric>>(`${BASE}/usage/metrics`)
  return res.data
}

export function getUsageSummary(period?: string): Promise<import('../types').UsageSummary> {
  const query = period ? `?period=${period}` : ''
  return fetchJSON(`${BASE}/usage/summary${query}`)
}

// R13: Optimization
export async function getOptimizationRecommendations(): Promise<import('../types').OptimizationRec[]> {
  const res = await fetchJSON<import('../types').ListResponse<import('../types').OptimizationRec>>(`${BASE}/optimizations/recommendations`)
  return res.data
}

export function generateOptimizationRecommendations(autoSave?: boolean): Promise<{ recommendations: import('../types').OptimizationRec[]; count: number }> {
  return fetchJSON(`${BASE}/optimizations/recommendations/generate`, {
    method: 'POST',
    body: JSON.stringify({ auto_save: autoSave ?? true }),
  })
}

export function updateOptimizationStatus(recId: string, status: string): Promise<{ id: string; status: string }> {
  return fetchJSON(`${BASE}/optimizations/recommendations/${recId}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}

// R14: Version Tracking
export async function getVersionEntries(): Promise<import('../types').VersionEntry[]> {
  const res = await fetchJSON<import('../types').ListResponse<import('../types').VersionEntry>>(`${BASE}/versions/entries`)
  return res.data
}

export function getVersionDiff(entryId: string): Promise<{ entry: import('../types').VersionEntry; has_changes: boolean }> {
  return fetchJSON(`${BASE}/versions/entries/${entryId}`)
}

// R15: Incident Management
export function getIncidents(): Promise<import('../types').IncidentListResponse> {
  return fetchJSON(`${BASE}/incidents/events`)
}

export function createIncident(data: {
  severity: string
  category: string
  title: string
  description?: string
  source?: string
  entity_id?: string
}): Promise<{ incident_id: string; status: string }> {
  return fetchJSON(`${BASE}/incidents/events`, { method: 'POST', body: JSON.stringify(data) })
}

export function updateIncident(incidentId: string, data: { status?: string; resolution?: string }): Promise<{ incident_id: string; status: string }> {
  return fetchJSON(`${BASE}/incidents/events/${incidentId}`, { method: 'PUT', body: JSON.stringify(data) })
}

// Onboarding Wizard API
export function getSetupStatus(ws: string): Promise<import('../types').SetupStatus> {
  return fetchJSON(`${BASE}/workspaces/${ws}/setup-status`)
}

export function createBrand(ws: string, data: { name: string; website_url: string; competitors?: string[] }): Promise<import('../types').Brand> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands`, { method: 'POST', body: JSON.stringify(data) })
}

export function updateBrand(ws: string, brandId: string, data: { name?: string; website_url?: string }): Promise<import('../types').Brand> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/${brandId}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteBrand(ws: string, brandId: string): Promise<{ status: string; brand_id: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/${brandId}`, { method: 'DELETE' })
}

export interface CompetitorItem {
  competitor_id: string
  competitor_name: string
  created_at: string
}

export function getBrandCompetitors(ws: string, brandId: string): Promise<CompetitorItem[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/${brandId}/competitors`)
}

export function updateBrandCompetitors(ws: string, brandId: string, competitorIds: string[]): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/${brandId}/competitors`, {
    method: 'PUT',
    body: JSON.stringify({ competitors: competitorIds }),
  })
}

export function deleteBrandCompetitor(ws: string, brandId: string, competitorId: string): Promise<{ status: string; competitor_id: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands/${brandId}/competitors/${competitorId}`, {
    method: 'DELETE',
  })
}

export function createPanel(ws: string, data: { name: string; description?: string; brand_ids?: string[] }): Promise<import('../types').Panel> {
  return fetchJSON(`${BASE}/workspaces/${ws}/panels`, { method: 'POST', body: JSON.stringify(data) })
}

export function createPromptSet(ws: string, data: { name: string; prompt_text: string; language?: string }): Promise<{ id: string; name: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/prompt-sets`, { method: 'POST', body: JSON.stringify(data) })
}

export function listPromptSets(ws: string): Promise<{ id: string; name: string; description?: string; prompt_text: string; is_active: boolean }[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/prompt-sets`)
}

export function triggerMeasurement(ws: string, data: { brand_id: string; panel_id?: string }): Promise<{ status: string; run_id: string; brand: string; engines: string[] }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/measurements`, { method: 'POST', body: JSON.stringify(data) })
}

// SEO Entegrasyonları (FR-B8)
export interface SEOConnection {
  id: string
  platform: string
  email: string
  is_active: boolean
  last_synced_at: string | null
  created_at: string
}

export interface GA4DataRow {
  page_views: number
  sessions: number
  bounce_rate: number
  avg_session_duration: number
  measured_at: string
}

export interface SearchConsoleRow {
  query: string
  clicks: number
  impressions: number
  ctr: number
  avg_position: number
  measured_at: string
}

export function getSEOConnections(ws: string): Promise<SEOConnection[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/seo/connections`)
}

export function getSEOAuthURL(ws: string, platform: string): Promise<{ auth_url: string; state_token: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/seo/auth-url?platform=${platform}`)
}

export function disconnectSEO(ws: string, platform: string): Promise<{ status: string; platform: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/seo/connections/${platform}`, { method: 'DELETE' })
}

export function getSearchConsoleData(ws: string, brandId?: string): Promise<SearchConsoleRow[]> {
  const q = brandId ? `?brand_id=${brandId}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/seo/search-console${q}`)
}

export function getGA4Data(ws: string, brandId?: string): Promise<GA4DataRow[]> {
  const q = brandId ? `?brand_id=${brandId}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/seo/ga4${q}`)
}

// Site Audit (FR-B4)
export interface AuditFindingsCatalog {
  brand_id: string
  overall_score: number
  summary: {
    total: number
    critical: number
    high: number
    medium: number
    low: number
  }
  catalog: {
    robots_txt?: AuditFindingItem[]
    bot_access?: AuditFindingItem[]
    ssr?: AuditFindingItem[]
    ssrf?: AuditFindingItem[]
  }
}

export interface AuditFindingItem {
  title: string
  detail: string
  severity: string
  recommendation?: string
}

export function getAuditFindings(ws: string, brandId: string): Promise<AuditFindingsCatalog> {
  return fetchJSON(`${BASE}/workspaces/${ws}/audit/findings?brand_id=${brandId}`)
}

export async function triggerDigest(ws: string): Promise<Blob> {
  const token = localStorage.getItem('token')
  const res = await fetch(`${BASE}/workspaces/${ws}/reports/digest`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    const code = err.error || res.statusText
    if (res.status === 401 && token) {
      notifySessionExpired()
    }
    if (res.status === 403) {
      notifyPermissionDenied(friendlyMessage(code))
      throw new ApiError(res.status, '')
    }
    throw new ApiError(res.status, friendlyMessage(code))
  }
  return res.blob()
}

// R9: Prompt Audit
export function runPromptAudit(data: { prompt_text: string; model: string }): Promise<import('../types').PromptAudit> {
  return fetchJSON(`${BASE}/prompts/audit`, { method: 'POST', body: JSON.stringify(data) })
}

export function listPromptAudits(): Promise<import('../types').ListResponse<import('../types').PromptAudit>> {
  return fetchJSON(`${BASE}/prompts/audits`)
}

export function getPromptAudit(auditId: string): Promise<import('../types').PromptAudit> {
  return fetchJSON(`${BASE}/prompts/audits/${auditId}`)
}

// R10: Model Benchmark
export function runBenchmark(data: { model_name: string; benchmark_name?: string }): Promise<import('../types').BenchmarkResult> {
  return fetchJSON(`${BASE}/benchmarks/models`, { method: 'POST', body: JSON.stringify(data) })
}

export function listBenchmarks(): Promise<import('../types').ListResponse<import('../types').BenchmarkResult>> {
  return fetchJSON(`${BASE}/benchmarks/models`)
}

export function compareBenchmarks(): Promise<import('../types').BenchmarkComparison> {
  return fetchJSON(`${BASE}/benchmarks/compare`)
}

// Sentiment Analysis
export function analyzeSentiment(ws: string, text: string): Promise<import('../types').SentimentResult> {
  return fetchJSON(`${BASE}/workspaces/${ws}/sentiment/analyze`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

export function getSentimentSummary(ws: string): Promise<import('../types').SentimentSummary> {
  return fetchJSON(`${BASE}/workspaces/${ws}/sentiment/summary`)
}

export function listSentiment(ws: string): Promise<import('../types').ListResponse<import('../types').SentimentResult>> {
  return fetchJSON(`${BASE}/workspaces/${ws}/sentiment/`)
}

// Hallucination Detection
export function detectHallucinations(ws: string, text: string): Promise<import('../types').HallucinationFlag> {
  return fetchJSON(`${BASE}/workspaces/${ws}/hallucination/detect`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  })
}

export function listHallucinations(ws: string): Promise<import('../types').ListResponse<import('../types').HallucinationFlag>> {
  return fetchJSON(`${BASE}/workspaces/${ws}/hallucination/`)
}

export function verifyHallucination(ws: string, flagId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/hallucination/${flagId}/verify`, { method: 'POST' })
}

// Alert Rules
export function listAlertRules(ws: string, brandId?: string): Promise<{ rules: import('../types').AlertRule[] }> {
  const q = brandId ? `?brand_id=${brandId}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/alert-rules${q}`)
}

export function createAlertRule(ws: string, data: {
  brand_id: string; name: string; metric: string; condition: string
  threshold: number; channel: string; channel_config?: Record<string, string>
}): Promise<import('../types').AlertRule> {
  return fetchJSON(`${BASE}/workspaces/${ws}/alert-rules`, { method: 'POST', body: JSON.stringify(data) })
}

export function updateAlertRule(ws: string, ruleId: string, data: Partial<import('../types').AlertRule>): Promise<import('../types').AlertRule> {
  return fetchJSON(`${BASE}/workspaces/${ws}/alert-rules/${ruleId}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteAlertRule(ws: string, ruleId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/alert-rules/${ruleId}`, { method: 'DELETE' })
}

// Tenants & Members
export function getTenant(): Promise<{ id: string; name: string; slug: string; tier: string; created_at: string }> {
  return fetchJSON(`${BASE}/tenant`)
}

export function listMembers(): Promise<{ members: import('../types').TenantMember[] }> {
  return fetchJSON(`${BASE}/tenant/members`)
}

export function inviteMember(email: string, workspaceId: string, role: string): Promise<{ status: string; email: string; token: string }> {
  return fetchJSON(`${BASE}/tenant/invitations`, {
    method: 'POST',
    body: JSON.stringify({ email, workspace_id: workspaceId, role }),
  })
}

export function listInvitations(): Promise<{ invitations: import('../types').TenantInvitation[] }> {
  return fetchJSON(`${BASE}/tenant/invitations`)
}

// API Keys
export function listApiKeys(): Promise<{ keys: import('../types').ApiKey[] }> {
  return fetchJSON(`${BASE}/api-keys`)
}

export function createApiKey(data: { name: string; role?: string; expires_at?: string }): Promise<{ id: string; api_key: string; key_prefix: string; warning: string }> {
  return fetchJSON(`${BASE}/api-keys`, { method: 'POST', body: JSON.stringify(data) })
}

export function deleteApiKey(keyId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/api-keys/${keyId}`, { method: 'DELETE' })
}

// Compliance
export function getComplianceReport(): Promise<{ report: Record<string, unknown>; frameworks: string[] }> {
  return fetchJSON(`${BASE}/compliance/report`)
}

export function getComplianceSOC2(): Promise<Record<string, unknown>> {
  return fetchJSON(`${BASE}/compliance/soc2`)
}

export function getComplianceEvidence(): Promise<{ evidence: { id: string; framework: string; name: string; status: string; updated_at: string }[] }> {
  return fetchJSON(`${BASE}/compliance/evidence`)
}

// Benchmark Context (FR-D5: DP korumalı sektör kıyası)
export function getBenchmarkContext(ws: string): Promise<import('../types').BenchmarkContext> {
  return fetchJSON(`${BASE}/workspaces/${ws}/benchmark/context`)
}

// Billing
export function getSubscription(): Promise<{ tenant_id: string; tier: string; updated_at: string }> {
  return fetchJSON(`${BASE}/billing/subscription`)
}

// FR-A6: Self-serve ödeme — checkout oturumu
export function createCheckoutSession(data: {
  tier: string
  success_url: string
  cancel_url: string
}): Promise<{ session_id: string; url: string }> {
  return fetchJSON(`${BASE}/billing/checkout`, { method: 'POST', body: JSON.stringify(data) })
}

// FR-A6: Otomatik fatura listesi
export interface BillingInvoice {
  id: string
  stripe_invoice_id: string
  number: string
  status: string
  amount_total: number
  currency: string
  period_start?: string
  period_end?: string
  hosted_invoice_url: string
  invoice_pdf: string
  created_at: string
  // FR-A6 TR özel vergi alanları
  subtotal: number
  vat_rate: number
  vat_amount: number
  invoice_type: string
  customer_name: string
  customer_tax_no: string
  customer_identity: string
  customer_address: string
  gib_status: string
  document_id: string
  gib_response_id: string
}

export function listBillingInvoices(): Promise<{ invoices: BillingInvoice[]; count: number }> {
  return fetchJSON(`${BASE}/billing/invoices`)
}

export function getBillingInvoice(invoiceId: string): Promise<BillingInvoice> {
  return fetchJSON(`${BASE}/billing/invoices/${invoiceId}`)
}

// FR-A6: e-Fatura/e-Arşiv gönderimi (KDV hesaplaması dahil)
export function submitEFatura(invoiceId: string, data: {
  invoice_type: 'efatura' | 'earsiv'
  vat_rate: number
  customer_name: string
  customer_tax_no?: string
  customer_identity?: string
  customer_address?: string
}): Promise<{ invoice: BillingInvoice; gib: { status: string; response_id: string; message: string; submitted_at: string } }> {
  return fetchJSON(`${BASE}/billing/invoices/${invoiceId}/efatura`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

// FR-A6: UBL-TR XML belgesi ve Türkçe fatura PDF indirme.
// Endpoint'ler Bearer token gerektirdiği için dosyalar fetch + blob ile indirilir
// (tarayıcı <a href> ile Authorization header gönderemez).
export async function downloadBillingFile(path: string, fallbackName: string): Promise<void> {
  const token = localStorage.getItem('token')
  const res = await fetch(`${BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) {
    throw new Error(`İndirme başarısız (HTTP ${res.status})`)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function eFaturaXMLDownloadUrl(invoiceId: string): string {
  return `/billing/invoices/${invoiceId}/efatura/xml`
}

export function invoicePDFDownloadUrl(invoiceId: string): string {
  return `/billing/invoices/${invoiceId}/pdf`
}

// FR-A6: Stripe Billing Portal (kart yönetimi, paket değişikliği, iptal)
export function createBillingPortalSession(returnUrl: string): Promise<{ url: string }> {
  return fetchJSON(`${BASE}/billing/portal`, {
    method: 'POST',
    body: JSON.stringify({ return_url: returnUrl }),
  })
}

// Conversation Replay (FR-D12)
export function listReplaySnapshots(ws: string, brandId?: string): Promise<import('../types').ReplaySnapshot[]> {
  const q = brandId ? `?brand_id=${encodeURIComponent(brandId)}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/replay${q}`)
}

export function getReplaySnapshot(ws: string, snapshotId: string): Promise<import('../types').ReplaySnapshotDetail> {
  return fetchJSON(`${BASE}/workspaces/${ws}/replay/${snapshotId}`)
}

export function captureReplaySnapshot(ws: string, brandId: string, prompt: string): Promise<import('../types').ReplaySnapshot> {
  return fetchJSON(`${BASE}/workspaces/${ws}/replay/capture`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId, prompt }),
  })
}

export function compareReplaySnapshots(ws: string, snapshotA: string, snapshotB: string): Promise<import('../types').ReplayDiff> {
  return fetchJSON(`${BASE}/workspaces/${ws}/replay/compare?snapshot_a=${encodeURIComponent(snapshotA)}&snapshot_b=${encodeURIComponent(snapshotB)}`)
}

export function deleteReplaySnapshot(ws: string, snapshotId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/replay/${snapshotId}`, { method: 'DELETE' })
}

// Response Archive (FR-D13)
export function listArchiveEntries(ws: string, brandId?: string, engine?: string): Promise<import('../types').ArchiveEntry[]> {
  const params = new URLSearchParams()
  if (brandId) params.set('brand_id', brandId)
  if (engine) params.set('engine', engine)
  const qs = params.toString() ? `?${params.toString()}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/archive${qs}`)
}

export function getArchiveEntry(ws: string, entryId: string): Promise<import('../types').ArchiveEntryDetail> {
  return fetchJSON(`${BASE}/workspaces/${ws}/archive/${entryId}`)
}

export function archiveResponse(ws: string, data: { brand_id: string; engine_name?: string; prompt_text?: string; response: string }): Promise<import('../types').ArchiveEntry> {
  return fetchJSON(`${BASE}/workspaces/${ws}/archive`, { method: 'POST', body: JSON.stringify(data) })
}

export function getArchiveVersionHistory(ws: string, brandId: string, engine?: string): Promise<import('../types').ArchiveVersion[]> {
  const params = new URLSearchParams({ brand_id: brandId })
  if (engine) params.set('engine', engine)
  return fetchJSON(`${BASE}/workspaces/${ws}/archive/versions?${params.toString()}`)
}

// Technical GEO (FR-B6/B7/E7)
export function getTechnicalGEOScore(ws: string, brandId: string): Promise<import('../types').TechnicalGEOScore> {
  return fetchJSON(`${BASE}/workspaces/${ws}/technical-geo/score?brand_id=${encodeURIComponent(brandId)}`)
}

export function analyzeBotAccess(ws: string, brandId: string, url?: string): Promise<import('../types').BotAnalysis> {
  return fetchJSON(`${BASE}/workspaces/${ws}/technical-geo/bots`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId, url: url || '' }),
  })
}

export function listBotAnalyses(ws: string, brandId?: string): Promise<import('../types').BotAnalysis[]> {
  const q = brandId ? `?brand_id=${encodeURIComponent(brandId)}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/technical-geo/bots${q}`)
}

export function analyzeSchema(ws: string, brandId: string): Promise<import('../types').SchemaAnalysis> {
  return fetchJSON(`${BASE}/workspaces/${ws}/technical-geo/schema`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId }),
  })
}

export function listSchemaAnalyses(ws: string, brandId?: string): Promise<import('../types').SchemaAnalysis[]> {
  const q = brandId ? `?brand_id=${encodeURIComponent(brandId)}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/technical-geo/schema${q}`)
}

// Content GEO (FR-E5/E6)
export function analyzeContentGap(ws: string, brandId: string): Promise<import('../types').ContentGap[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/content-geo/gap`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId }),
  })
}

export function listContentGaps(ws: string, brandId?: string): Promise<import('../types').ContentGap[]> {
  const q = brandId ? `?brand_id=${encodeURIComponent(brandId)}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/content-geo/gap${q}`)
}

export function getContentHubScore(ws: string, brandId: string): Promise<import('../types').ContentHubScore> {
  return fetchJSON(`${BASE}/workspaces/${ws}/content-geo/hub-score?brand_id=${encodeURIComponent(brandId)}`)
}

export function listTopicClusters(ws: string, brandId?: string): Promise<import('../types').TopicCluster[]> {
  const q = brandId ? `?brand_id=${encodeURIComponent(brandId)}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/content-geo/topics${q}`)
}

// Competitive Gap (FR-D11)
export function analyzeCompetitiveGap(ws: string, brandId: string): Promise<import('../types').GapSnapshot[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/competitive-gap/analyze`, {
    method: 'POST',
    body: JSON.stringify({ brand_id: brandId }),
  })
}

export function getCompetitiveGapOverview(ws: string, brandId: string): Promise<import('../types').CompetitiveGapOverview[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/competitive-gap/overview?brand_id=${encodeURIComponent(brandId)}`)
}

export function getCompetitiveGapDetail(ws: string, brandId: string, competitorId: string): Promise<import('../types').GapDetail | null> {
  return fetchJSON(`${BASE}/workspaces/${ws}/competitive-gap/visibility?brand_id=${encodeURIComponent(brandId)}&competitor_id=${encodeURIComponent(competitorId)}`)
}

export function getCompetitiveRecommendations(ws: string, brandId?: string): Promise<import('../types').CompetitiveRecommendation[]> {
  const q = brandId ? `?brand_id=${encodeURIComponent(brandId)}` : ''
  return fetchJSON(`${BASE}/workspaces/${ws}/competitive-gap/recommendations${q}`)
}

// Retention Policies
export function listRetentionPolicies(ws: string): Promise<{ policies: import('../types').RetentionPolicy[] }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/retention/policies`)
}

export function upsertRetentionPolicy(ws: string, data: Partial<import('../types').RetentionPolicy>): Promise<import('../types').RetentionPolicy> {
  return fetchJSON(`${BASE}/workspaces/${ws}/retention/policies`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteRetentionPolicy(ws: string, policyId: string): Promise<{ status: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/retention/policies/${policyId}`, { method: 'DELETE' })
}

// Panorama
export function getWorkspacePanorama(): Promise<{ workspaces: { id: string; name: string; avg_score: number; brand_count: number; measurement_count: number; archived: boolean; created_at: string }[] }> {
  return fetchJSON(`${BASE}/tenant/panorama`)
}
