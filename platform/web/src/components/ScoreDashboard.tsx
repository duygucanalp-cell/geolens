import { useTranslation } from 'react-i18next'
import { lazy, Suspense, useEffect, useMemo, useState } from 'react'
import { ScoreCard } from './ScoreCard'
import { TrendChart } from './TrendChart'
import { EngineComparison } from './EngineComparison'
import { BenchmarkWidget } from './BenchmarkWidget'
import { getScores, getBrands, getPanels } from '../api/client'
import type { Score, Brand, Panel } from '../types'
import { ENGINE_NAMES } from '../types'

// Lazy-loaded tab panels — each loads only when its tab is activated
const BrandManagement = lazy(() => import('./BrandManagement').then(m => ({ default: m.BrandManagement })))
const AuditPanel = lazy(() => import('./AuditPanel').then(m => ({ default: m.AuditPanel })))
const NotificationSettings = lazy(() => import('./NotificationSettings').then(m => ({ default: m.NotificationSettings })))
const ReportsPanel = lazy(() => import('./ReportsPanel').then(m => ({ default: m.ReportsPanel })))
const RecommendationsPanel = lazy(() => import('./RecommendationsPanel').then(m => ({ default: m.RecommendationsPanel })))
const MonitoringPanel = lazy(() => import('./MonitoringPanel').then(m => ({ default: m.MonitoringPanel })))
const CostPanel = lazy(() => import('./CostPanel').then(m => ({ default: m.CostPanel })))
const UsagePanel = lazy(() => import('./UsagePanel').then(m => ({ default: m.UsagePanel })))
const OptimizationPanel = lazy(() => import('./OptimizationPanel').then(m => ({ default: m.OptimizationPanel })))
const VersionPanel = lazy(() => import('./VersionPanel').then(m => ({ default: m.VersionPanel })))
const IncidentPanel = lazy(() => import('./IncidentPanel').then(m => ({ default: m.IncidentPanel })))
const GuardrailsPanel = lazy(() => import('./GuardrailsPanel').then(m => ({ default: m.GuardrailsPanel })))
const AgentTracePanel = lazy(() => import('./AgentTracePanel').then(m => ({ default: m.AgentTracePanel })))
const RegistryPanel = lazy(() => import('./RegistryPanel').then(m => ({ default: m.RegistryPanel })))
const PolicyPacksPanel = lazy(() => import('./PolicyPacksPanel').then(m => ({ default: m.PolicyPacksPanel })))
const BiasPanel = lazy(() => import('./BiasPanel').then(m => ({ default: m.BiasPanel })))
const ExplainPanel = lazy(() => import('./ExplainPanel').then(m => ({ default: m.ExplainPanel })))
const DiscoveryPanel = lazy(() => import('./DiscoveryPanel').then(m => ({ default: m.DiscoveryPanel })))
const GatePanel = lazy(() => import('./GatePanel').then(m => ({ default: m.GatePanel })))
const PromptAuditPanel = lazy(() => import('./PromptAuditPanel').then(m => ({ default: m.PromptAuditPanel })))
const BenchmarkPanel = lazy(() => import('./BenchmarkPanel').then(m => ({ default: m.BenchmarkPanel })))
const SentimentPanel = lazy(() => import('./SentimentPanel').then(m => ({ default: m.SentimentPanel })))
const HallucinationPanel = lazy(() => import('./HallucinationPanel').then(m => ({ default: m.HallucinationPanel })))
const AlertRulesPanel = lazy(() => import('./AlertRulesPanel'))
const TenantSettingsPanel = lazy(() => import('./TenantSettingsPanel'))
const CompliancePanel = lazy(() => import('./CompliancePanel'))
const PromptSetsPanel = lazy(() => import('./PromptSetsPanel'))

interface ScoreDashboardProps {
  workspaceId: string
}

type Tab = 'scores' | 'brands' | 'audit' | 'notifications' | 'reports' | 'recommendations' | 'monitoring'
  | 'cost' | 'usage' | 'optimization' | 'version' | 'incident'
  | 'guardrails' | 'agenttracing' | 'registry' | 'policy' | 'bias' | 'explain' | 'discovery' | 'gate'
  | 'promptaudit' | 'benchmark' | 'sentiment' | 'hallucination' | 'alertrules'
  | 'tenant' | 'compliance' | 'prompts'

export function ScoreDashboard({ workspaceId }: ScoreDashboardProps) {
  const { t } = useTranslation()
  const [scores, setScores] = useState<Score[]>([])
  const [brands, setBrands] = useState<Brand[]>([])
  const [panels, setPanels] = useState<Panel[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>('scores')
  const [filterEngine, setFilterEngine] = useState<string>('all')
  const [selectedPanel, setSelectedPanel] = useState<string>('all')

  useEffect(() => {
    loadAll()
  }, [workspaceId])

  async function loadAll() {
    try {
      setLoading(true)
      setError(null)
      const [scoresData, brandsData, panelsData] = await Promise.all([
        getScores(workspaceId),
        getBrands(workspaceId),
        getPanels(workspaceId),
      ])
      setScores(scoresData)
      setBrands(brandsData)
      setPanels(panelsData)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('dashboard.error_load'))
    } finally {
      setLoading(false)
    }
  }

  // Engine listesi (scores'dan çıkar)
  const availableEngines = useMemo(() => {
    const engines = new Set<string>()
    for (const s of scores) {
      if (s.engine_breakdown) {
        Object.keys(s.engine_breakdown).forEach((e) => engines.add(e))
      }
    }
    return Array.from(engines)
  }, [scores])

  // Filtrelenmiş skorlar (engine + panel)
  const filteredScores = useMemo(() => {
    let result = scores
    if (filterEngine !== 'all') {
      result = result.filter((s) => {
        if (!s.engine_breakdown) return false
        return s.engine_breakdown[filterEngine] !== undefined
      })
    }
    if (selectedPanel !== 'all') {
      result = result.filter((s) => s.panel_id === selectedPanel)
    }
    return result
  }, [scores, filterEngine, selectedPanel])

  const scoresByBrand = useMemo(() => {
    const map = new Map<string, Score[]>()
    for (const s of filteredScores) {
      const existing = map.get(s.brand_name) ?? []
      existing.push(s)
      map.set(s.brand_name, existing)
    }
    return map
  }, [filteredScores])

  if (loading) {
    return <div className="dashboard-loading">{t('dashboard.loading')}</div>
  }

  if (error) {
    return (
      <div className="dashboard-error">
        <p>{error}</p>
        <button onClick={loadAll}>{t('dashboard.retry')}</button>
      </div>
    )
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'scores', label: t('tab.scores') },
    { key: 'brands', label: t('tab.brands') },
    { key: 'audit', label: t('tab.audit') },
    { key: 'reports', label: t('tab.reports') },
    { key: 'notifications', label: t('tab.notifications') },
    { key: 'recommendations', label: t('tab.recommendations') },
    { key: 'monitoring', label: t('tab.monitoring') },
    { key: 'guardrails', label: t('tab.guardrails') },
    { key: 'agenttracing', label: t('tab.agenttracing') },
    { key: 'registry', label: t('tab.registry') },
    { key: 'policy', label: t('tab.policy') },
    { key: 'bias', label: t('tab.bias') },
    { key: 'explain', label: t('tab.explain') },
    { key: 'discovery', label: t('tab.discovery') },
    { key: 'gate', label: t('tab.gate') },
    { key: 'cost', label: t('tab.cost') },
    { key: 'usage', label: t('tab.usage') },
    { key: 'optimization', label: t('tab.optimization') },
    { key: 'version', label: t('tab.version') },
    { key: 'incident', label: t('tab.incident') },
    { key: 'promptaudit', label: t('tab.promptaudit') },
    { key: 'benchmark', label: '🧪 Benchmark' },
    { key: 'sentiment', label: '💬 Sentiment' },
    { key: 'hallucination', label: '🧠 Halüsinasyon' },
    { key: 'alertrules', label: '🔔 Uyarılar' },
    { key: 'tenant', label: t('tab.tenant') },
    { key: 'compliance', label: t('tab.compliance') },
    { key: 'prompts', label: '💬 Prompts' },
  ]

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>{t('dashboard.title')}</h2>
        <button className="refresh-btn" onClick={loadAll}>
          {t('dashboard.refresh')}
        </button>
      </div>

      {/* Tabs */}
      <div className="dashboard-tabs">
        {tabs.map(tab => (
          <button
            key={tab.key}
            className={`tab-btn ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <Suspense fallback={<div className="dashboard-loading">{t('dashboard.component_loading')}</div>}>
        {activeTab === 'brands' ? (
          <BrandManagement workspaceId={workspaceId} />
        ) : activeTab === 'audit' ? (
          <AuditPanel workspaceId={workspaceId} brands={brands} />
        ) : activeTab === 'reports' ? (
          <ReportsPanel workspaceId={workspaceId} />
        ) : activeTab === 'notifications' ? (
          <NotificationSettings workspaceId={workspaceId} />
        ) : activeTab === 'recommendations' ? (
          <RecommendationsPanel workspaceId={workspaceId} brands={brands} />
        ) : activeTab === 'monitoring' ? (
          <MonitoringPanel workspaceId={workspaceId} />
        ) : activeTab === 'cost' ? (
          <CostPanel workspaceId={workspaceId} />
        ) : activeTab === 'usage' ? (
          <UsagePanel workspaceId={workspaceId} />
        ) : activeTab === 'optimization' ? (
          <OptimizationPanel workspaceId={workspaceId} />
        ) : activeTab === 'version' ? (
          <VersionPanel workspaceId={workspaceId} />
        ) : activeTab === 'incident' ? (
          <IncidentPanel workspaceId={workspaceId} />
        ) : activeTab === 'guardrails' ? (
          <GuardrailsPanel workspaceId={workspaceId} />
        ) : activeTab === 'agenttracing' ? (
          <AgentTracePanel workspaceId={workspaceId} />
        ) : activeTab === 'registry' ? (
          <RegistryPanel workspaceId={workspaceId} />
        ) : activeTab === 'policy' ? (
          <PolicyPacksPanel workspaceId={workspaceId} />
        ) : activeTab === 'bias' ? (
          <BiasPanel workspaceId={workspaceId} />
        ) : activeTab === 'explain' ? (
          <ExplainPanel workspaceId={workspaceId} />
        ) : activeTab === 'discovery' ? (
          <DiscoveryPanel workspaceId={workspaceId} />
        ) : activeTab === 'gate' ? (
          <GatePanel workspaceId={workspaceId} />
        ) : activeTab === 'promptaudit' ? (
          <PromptAuditPanel workspaceId={workspaceId} />
        ) : activeTab === 'benchmark' ? (
          <BenchmarkPanel workspaceId={workspaceId} />
        ) : activeTab === 'sentiment' ? (
          <SentimentPanel workspaceId={workspaceId} />
        ) : activeTab === 'hallucination' ? (
          <HallucinationPanel workspaceId={workspaceId} />
        ) : activeTab === 'alertrules' ? (
          <AlertRulesPanel workspaceId={workspaceId} brands={brands} />
        ) : activeTab === 'tenant' ? (
          <TenantSettingsPanel />
        ) : activeTab === 'compliance' ? (
          <CompliancePanel />
        ) : activeTab === 'prompts' ? (
          <PromptSetsPanel workspaceId={workspaceId} />
        ) : (
          <>
            {/* Benchmark Widget: Sektör Kıyası (FR-D5) */}
            {activeTab === 'scores' && <BenchmarkWidget workspaceId={workspaceId} />}

            {/* Filters */}
            <div className="dashboard-filters">
              {panels.length > 0 && (
                <select
                  value={selectedPanel}
                  onChange={(e) => setSelectedPanel(e.target.value)}
                  className="filter-select"
                >
                  <option value="all">{t('dashboard.filter_all_panels')}</option>
                  {panels.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              )}
              <select
                value={filterEngine}
                onChange={(e) => setFilterEngine(e.target.value)}
                className="filter-select"
              >
                <option value="all">{t('dashboard.filter_all_engines')}</option>
                {availableEngines.map((e) => (
                  <option key={e} value={e}>
                    {t(ENGINE_NAMES[e]) || e}
                  </option>
                ))}
              </select>
            </div>

            {filteredScores.length === 0 ? (
              <div className="dashboard-empty">
                <h2>{t('dashboard.empty_title')}</h2>
                <p>{t('dashboard.empty_desc')}</p>
              </div>
            ) : (
              <>
                {Array.from(scoresByBrand.entries()).map(([brandName, brandScores]) => (
                  <div key={brandName} className="brand-section">
                    <TrendChart scores={brandScores} brandName={brandName} />
                    <EngineComparison scores={brandScores} brandName={brandName} />
                  </div>
                ))}

                <div className="scores-grid">
                  {filteredScores.map((score) => (
                    <ScoreCard key={score.id} score={score} />
                  ))}
                </div>
              </>
            )}
          </>
        )}
      </Suspense>
    </div>
  )
}
