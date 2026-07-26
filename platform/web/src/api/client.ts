import type { Score, Brand, Panel, AuditResult } from '../types'

const BASE = '/v1'

async function fetchJSON<T>(url: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(url, { ...init, headers })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(err.error || 'API error')
  }
  return res.json()
}

export function login(email: string, password: string) {
  return fetchJSON<{ token: string; user_id: string; tenant_id: string; workspace_id: string; role: string }>(
    `${BASE}/auth/login`,
    { method: 'POST', body: JSON.stringify({ email, password }) }
  )
}

export function register(email: string, password: string, name: string) {
  return fetchJSON<{ token: string; user_id: string; tenant_id: string; workspace_id: string; role: string }>(
    `${BASE}/auth/register`,
    { method: 'POST', body: JSON.stringify({ email, password, name }) }
  )
}

export function getScores(ws: string): Promise<Score[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/scores`)
}

export function getBrands(ws: string): Promise<Brand[]> {
  return fetchJSON(`${BASE}/workspaces/${ws}/brands`)
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

export function createPanel(ws: string, data: { name: string; description?: string; brand_ids?: string[] }): Promise<import('../types').Panel> {
  return fetchJSON(`${BASE}/workspaces/${ws}/panels`, { method: 'POST', body: JSON.stringify(data) })
}

export function createPromptSet(ws: string, data: { name: string; prompt_text: string; language?: string }): Promise<{ id: string; name: string }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/prompt-sets`, { method: 'POST', body: JSON.stringify(data) })
}

export function triggerMeasurement(ws: string, data: { brand_id: string; panel_id?: string }): Promise<{ status: string; run_id: string; brand: string; engines: string[] }> {
  return fetchJSON(`${BASE}/workspaces/${ws}/measurements`, { method: 'POST', body: JSON.stringify(data) })
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
    throw new Error(err.error || 'Failed to generate report')
  }
  return res.blob()
}
