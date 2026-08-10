import { useTranslation } from 'react-i18next'
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
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
const RedTeamPanel = lazy(() => import('./RedTeamPanel').then(m => ({ default: m.RedTeamPanel })))
const DriftPanel = lazy(() => import('./DriftPanel').then(m => ({ default: m.DriftPanel })))
const PromptAuditPanel = lazy(() => import('./PromptAuditPanel').then(m => ({ default: m.PromptAuditPanel })))
const BenchmarkPanel = lazy(() => import('./BenchmarkPanel').then(m => ({ default: m.BenchmarkPanel })))
const SentimentPanel = lazy(() => import('./SentimentPanel').then(m => ({ default: m.SentimentPanel })))
const HallucinationPanel = lazy(() => import('./HallucinationPanel').then(m => ({ default: m.HallucinationPanel })))
const AlertRulesPanel = lazy(() => import('./AlertRulesPanel'))
const TenantSettingsPanel = lazy(() => import('./TenantSettingsPanel'))
const CompliancePanel = lazy(() => import('./CompliancePanel'))
const PromptSetsPanel = lazy(() => import('./PromptSetsPanel'))
const BillingPanel = lazy(() => import('./BillingPanel'))
const ReplayPanel = lazy(() => import('./ReplayPanel').then(m => ({ default: m.ReplayPanel })))
const ArchivePanel = lazy(() => import('./ArchivePanel').then(m => ({ default: m.ArchivePanel })))
const TechnicalGeoPanel = lazy(() => import('./TechnicalGeoPanel').then(m => ({ default: m.TechnicalGeoPanel })))
const ContentGeoPanel = lazy(() => import('./ContentGeoPanel').then(m => ({ default: m.ContentGeoPanel })))
const CompetitiveGapPanel = lazy(() => import('./CompetitiveGapPanel').then(m => ({ default: m.CompetitiveGapPanel })))

interface ScoreDashboardProps {
  workspaceId: string
}

type Tab = 'scores' | 'brands' | 'audit' | 'notifications' | 'reports' | 'recommendations' | 'monitoring'
  | 'cost' | 'usage' | 'optimization' | 'version' | 'incident'
  | 'guardrails' | 'agenttracing' | 'registry' | 'policy' | 'bias' | 'explain' | 'discovery' | 'gate'
  | 'redteam' | 'drift'
  | 'promptaudit' | 'benchmark' | 'sentiment' | 'hallucination' | 'alertrules'
  | 'tenant' | 'compliance' | 'prompts' | 'billing'
  | 'replay' | 'archive' | 'technicalgeo' | 'contentgeo' | 'competitive'

// Aktif sekme oturum boyunca hatırlanır — oturum süresi dolup tekrar
// giriş yapıldığında kullanıcı kaldığı sekmeye geri döner.
const TAB_STORAGE_KEY = 'geolens.last_tab'

// Testlerin bütünlük kuralını doğrulayabilmesi için dışa açıktır.
export const VALID_TABS: Tab[] = [
  'scores', 'brands', 'audit', 'notifications', 'reports', 'recommendations', 'monitoring',
  'cost', 'usage', 'optimization', 'version', 'incident',
  'guardrails', 'agenttracing', 'registry', 'policy', 'bias', 'explain', 'discovery', 'gate',
  'redteam', 'drift',
  'promptaudit', 'benchmark', 'sentiment', 'hallucination', 'alertrules',
  'tenant', 'compliance', 'prompts', 'billing',
  'replay', 'archive', 'technicalgeo', 'contentgeo', 'competitive',
]

// Sekmeler mantıksal gruplara ayrılır — 34 sekme tek satırda kalabalık görünmesin diye
// gruplu dropdown navigasyon kullanılır (akordeon davranışıyla).
// Testlerin bütünlük kuralını doğrulayabilmesi için dışa açıktır.
export const TAB_GROUPS: { key: string; labelKey: string; tabKeys: Tab[] }[] = [
  { key: 'measurement', labelKey: 'tabgroup.measurement', tabKeys: ['scores', 'brands', 'audit', 'reports', 'monitoring', 'benchmark', 'prompts', 'recommendations'] },
  { key: 'geo', labelKey: 'tabgroup.geo', tabKeys: ['sentiment', 'hallucination', 'replay', 'archive', 'technicalgeo', 'contentgeo', 'competitive'] },
  { key: 'governance', labelKey: 'tabgroup.governance', tabKeys: ['guardrails', 'agenttracing', 'registry', 'policy', 'bias', 'explain', 'discovery', 'gate', 'redteam', 'drift', 'promptaudit'] },
  { key: 'operations', labelKey: 'tabgroup.operations', tabKeys: ['notifications', 'alertrules', 'cost', 'usage', 'optimization', 'version', 'incident'] },
  { key: 'account', labelKey: 'tabgroup.account', tabKeys: ['tenant', 'compliance', 'billing'] },
]

function readSavedTab(): Tab {
  try {
    const saved = sessionStorage.getItem(TAB_STORAGE_KEY)
    if (saved && (VALID_TABS as string[]).includes(saved)) {
      return saved as Tab
    }
  } catch { /* sessionStorage erişilemiyorsa varsayılan sekme */ }
  return 'scores'
}

function saveTab(tab: Tab) {
  try {
    sessionStorage.setItem(TAB_STORAGE_KEY, tab)
  } catch { /* yoksay */ }
}

// Pin'lenen sekmeler: sık kullanılan sekmeler menünün üstünde hızlı erişim çipi olarak görünür.
// localStorage'da saklanır (oturumlar arası kalıcı).
const PINS_KEY = 'geolens.pinned_tabs'

export function readPinnedTabs(): Tab[] {
  try {
    const raw = localStorage.getItem(PINS_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((p): p is Tab => typeof p === 'string' && (VALID_TABS as string[]).includes(p))
  } catch {
    return []
  }
}

export function savePinnedTabs(pins: Tab[]) {
  try {
    localStorage.setItem(PINS_KEY, JSON.stringify(pins))
  } catch { /* yoksay */ }
}

export function ScoreDashboard({ workspaceId }: ScoreDashboardProps) {
  const { t } = useTranslation()
  const [scores, setScores] = useState<Score[]>([])
  const [brands, setBrands] = useState<Brand[]>([])
  const [panels, setPanels] = useState<Panel[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<Tab>(readSavedTab)
  const [pinned, setPinned] = useState<Tab[]>(readPinnedTabs)
  // Keep-alive: bir kez açılan sekme kaldırılmaz, yalnızca gizlenir —
  // veriler bellekte kalır, sekme değişiminde yeniden yüklenmez.
  const [visitedTabs, setVisitedTabs] = useState<Set<Tab>>(() => new Set([readSavedTab()]))
  const [filterEngine, setFilterEngine] = useState<string>('all')
  const [selectedPanel, setSelectedPanel] = useState<string>('all')

  // Gruplu navigasyon durumu
  const [navOpen, setNavOpen] = useState(false)
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set())
  const [navQuery, setNavQuery] = useState('')
  const navRef = useRef<HTMLDivElement | null>(null)
  const activeItemRef = useRef<HTMLButtonElement | null>(null)
  const navSearchRef = useRef<HTMLInputElement | null>(null)

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
      // 403'te hata mesajı bilinçli olarak boştur (gösterimi toast yönetir).
      // Yine de yükleme ekranı sessizce boş kalmasın: genel hata görünümü +
      // 'tekrar dene' butonu korunur, asıl neden toast'ta gösterilir.
      const message = err instanceof Error ? err.message : ''
      setError(message || t('dashboard.error_load'))
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

  // Navigasyonu dış tıklama / Escape ile kapat (arama sorgusu da sıfırlanır)
  useEffect(() => {
    if (!navOpen) return
    function onDocClick(e: MouseEvent) {
      if (navRef.current && !navRef.current.contains(e.target as Node)) {
        setNavOpen(false)
        setNavQuery('')
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setNavOpen(false)
        setNavQuery('')
      }
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [navOpen])

  // Menü açıldığında arama kutusuna odaklan (komut paleti deneyimi),
  // aktif öğe menüde görünür olsun diye kaydırılır.
  useEffect(() => {
    if (navOpen) {
      navSearchRef.current?.focus?.({ preventScroll: true })
      activeItemRef.current?.scrollIntoView?.({ block: 'nearest' })
    }
  }, [navOpen])

  function closeNav() {
    setNavOpen(false)
    setNavQuery('')
  }

  function toggleGroup(groupKey: string) {
    setCollapsedGroups(prev => {
      const next = new Set(prev)
      if (next.has(groupKey)) next.delete(groupKey)
      else next.add(groupKey)
      return next
    })
  }

  function selectTab(key: Tab) {
    setActiveTab(key)
    setVisitedTabs(prev => new Set(prev).add(key))
    saveTab(key)
    closeNav()
  }

  function togglePin(key: Tab) {
    setPinned(prev => {
      const next = prev.includes(key) ? prev.filter(p => p !== key) : [...prev, key]
      savePinnedTabs(next)
      return next
    })
  }

  // Arama kutusu klavye davranışı: Escape sorguyu temizler (boşsa menüyü kapatır),
  // ArrowDown ilk eşleşen öğeye odaklanır.
  function handleSearchKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Escape' && navQuery) {
      e.stopPropagation()
      setNavQuery('')
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      const first = navRef.current?.querySelector<HTMLElement>('.tab-group-item')
      first?.focus()
    }
  }

  // role="menu" için temel klavye navigasyonu (ok tuşları + Home/End)
  function handleMenuKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    // Arama kutusundaki tuşlar kendi handler'ında yönetilir
    if ((e.target as HTMLElement).classList.contains('tab-nav-search-input')) return
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) return
    const items = Array.from(
      e.currentTarget.querySelectorAll<HTMLButtonElement>('.tab-group-item')
    )
    if (items.length === 0) return
    const currentIdx = items.indexOf(document.activeElement as HTMLButtonElement)
    let next = currentIdx
    if (e.key === 'ArrowDown') next = Math.min(currentIdx + 1, items.length - 1)
    else if (e.key === 'ArrowUp') next = Math.max(currentIdx - 1, 0)
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = items.length - 1
    e.preventDefault()
    items[Math.max(next, 0)]?.focus()
  }

  if (loading) {
    return <PanelSkeleton message={t('dashboard.loading')} rows={6} />
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
    { key: 'redteam', label: t('tab.redteam') },
    { key: 'drift', label: t('tab.drift') },
    { key: 'cost', label: t('tab.cost') },
    { key: 'usage', label: t('tab.usage') },
    { key: 'optimization', label: t('tab.optimization') },
    { key: 'version', label: t('tab.version') },
    { key: 'incident', label: t('tab.incident') },
    { key: 'promptaudit', label: t('tab.promptaudit') },
    { key: 'benchmark', label: `🧪 ${t('tab.benchmark')}` },
    { key: 'sentiment', label: `💬 ${t('tab.sentiment')}` },
    { key: 'hallucination', label: `🧠 ${t('tab.hallucination')}` },
    { key: 'alertrules', label: `🔔 ${t('tab.alertrules')}` },
    { key: 'tenant', label: t('tab.tenant') },
    { key: 'compliance', label: t('tab.compliance') },
    { key: 'prompts', label: `💬 ${t('tab.prompts')}` },
    { key: 'billing', label: `💳 ${t('tab.billing')}` },
    { key: 'replay', label: `▶ ${t('tab.replay')}` },
    { key: 'archive', label: `🗄 ${t('tab.archive')}` },
    { key: 'technicalgeo', label: `⚙️ ${t('tab.technicalgeo')}` },
    { key: 'contentgeo', label: `📚 ${t('tab.contentgeo')}` },
    { key: 'competitive', label: `🥊 ${t('tab.competitive')}` },
  ]

  const tabsMap = new Map(tabs.map(tab => [tab.key, tab]))
  const groups = TAB_GROUPS.map(g => ({
    key: g.key,
    label: t(g.labelKey),
    tabs: g.tabKeys.map(k => tabsMap.get(k)).filter((x): x is { key: Tab; label: string } => Boolean(x)),
  }))
  const activeTabLabel = tabsMap.get(activeTab)?.label ?? t('tab.scores')

  // Açılışta yalnızca aktif sekmeyi içeren grup açık olsun (akordeon odak)
  function openNav() {
    setNavQuery('')
    const activeGroupKeys = new Set(
      groups.filter(g => g.tabs.some(tab => tab.key === activeTab)).map(g => g.key)
    )
    // Aktif sekme hiçbir grupta yoksa (tutarsızlık) ilk grup açık kalsın
    if (activeGroupKeys.size === 0 && groups.length > 0) {
      activeGroupKeys.add(groups[0].key)
    }
    setCollapsedGroups(new Set(TAB_GROUPS.map(g => g.key).filter(k => !activeGroupKeys.has(k))))
    setNavOpen(true)
  }

  // Türkçe 'İ' gibi büyük/küçük harf eşleşmeleri için normalleştirme:
  // küçük harf + aksan işaretlerini ayır (NFD) + birleştirici karakterleri at.
  // Böylece 'İçerik', 'içerik' ve 'icerik' aynı sonucu bulur.
  function normalizeSearch(s: string): string {
    return s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  }

  // Normalize edilmiş dizideki eşleşmeyi orijinal label'daki karakter aralığına
  // geri eşle (İ → i̇ gibi uzunluk farklarına dayanıklı) — vurgu için.
  function findHighlightRange(label: string, query: string): [number, number] | null {
    const normLabel = normalizeSearch(label)
    const idx = normLabel.indexOf(query)
    if (idx === -1) return null
    const normEnd = idx + query.length
    let start: number | null = null
    let end: number | null = null
    let normPos = 0
    for (let i = 0; i < label.length; i++) {
      const chunkLen = normalizeSearch(label[i]).length
      if (start === null && normPos <= idx && idx < normPos + chunkLen) start = i
      // Bitiş: eşleşme aralığı [idx, normEnd) yarı-açıktır. normPos === normEnd
      // olduğunda karakter eşleşmenin DIŞINDADIR (örn. 'İçerik GEO'da 'İçerik'ten
      // sonraki boşluk) — bu yüzden sol taraf sıkı (normPos < normEnd) olmalıdır.
      if (normPos < normEnd && normEnd <= normPos + chunkLen) end = i + 1
      normPos += chunkLen
    }
    if (start === null) start = 0
    if (end === null) end = Math.min(start + query.length, label.length)
    return [start, end]
  }

  // Arama sorgusuna göre filtrelenmiş gruplar + eşleşen metni vurgulama
  const query = normalizeSearch(navQuery.trim())
  const visibleGroups = query
    ? groups
        .map(g => ({ ...g, tabs: g.tabs.filter(tab => normalizeSearch(tab.label).includes(query)) }))
        .filter(g => g.tabs.length > 0)
    : groups

  function highlightLabel(label: string) {
    if (!query) return label
    const range = findHighlightRange(label, query)
    if (!range) return label
    const [start, end] = range
    return (
      <>
        {label.slice(0, start)}
        <mark className="tab-nav-mark">{label.slice(start, end)}</mark>
        {label.slice(end)}
      </>
    )
  }

  // Sekme içeriğini üretir. Keep-alive sayesinde her ziyaret edilen sekme
  // DOM'da kalır; bu fonksiyon yalnızca hangi içeriğin render edileceğini seçer.
  function renderTabContent(key: Tab) {
    switch (key) {
      case 'brands': return <BrandManagement workspaceId={workspaceId} />
      case 'audit': return <AuditPanel workspaceId={workspaceId} brands={brands} />
      case 'reports': return <ReportsPanel workspaceId={workspaceId} />
      case 'notifications': return <NotificationSettings workspaceId={workspaceId} />
      case 'recommendations': return <RecommendationsPanel workspaceId={workspaceId} brands={brands} />
      case 'monitoring': return <MonitoringPanel workspaceId={workspaceId} />
      case 'cost': return <CostPanel workspaceId={workspaceId} />
      case 'usage': return <UsagePanel workspaceId={workspaceId} />
      case 'optimization': return <OptimizationPanel workspaceId={workspaceId} />
      case 'version': return <VersionPanel workspaceId={workspaceId} />
      case 'incident': return <IncidentPanel workspaceId={workspaceId} />
      case 'guardrails': return <GuardrailsPanel workspaceId={workspaceId} />
      case 'agenttracing': return <AgentTracePanel workspaceId={workspaceId} />
      case 'registry': return <RegistryPanel workspaceId={workspaceId} />
      case 'policy': return <PolicyPacksPanel workspaceId={workspaceId} />
      case 'bias': return <BiasPanel workspaceId={workspaceId} />
      case 'explain': return <ExplainPanel workspaceId={workspaceId} />
      case 'discovery': return <DiscoveryPanel workspaceId={workspaceId} />
      case 'gate': return <GatePanel workspaceId={workspaceId} />
      case 'redteam': return <RedTeamPanel workspaceId={workspaceId} />
      case 'drift': return <DriftPanel workspaceId={workspaceId} />
      case 'promptaudit': return <PromptAuditPanel workspaceId={workspaceId} />
      case 'benchmark': return <BenchmarkPanel workspaceId={workspaceId} />
      case 'sentiment': return <SentimentPanel workspaceId={workspaceId} />
      case 'hallucination': return <HallucinationPanel workspaceId={workspaceId} />
      case 'alertrules': return <AlertRulesPanel workspaceId={workspaceId} brands={brands} />
      case 'tenant': return <TenantSettingsPanel />
      case 'compliance': return <CompliancePanel />
      case 'prompts': return <PromptSetsPanel workspaceId={workspaceId} />
      case 'billing': return <BillingPanel workspaceId={workspaceId} />
      case 'replay': return <ReplayPanel workspaceId={workspaceId} brands={brands} />
      case 'archive': return <ArchivePanel workspaceId={workspaceId} brands={brands} />
      case 'technicalgeo': return <TechnicalGeoPanel workspaceId={workspaceId} brands={brands} />
      case 'contentgeo': return <ContentGeoPanel workspaceId={workspaceId} brands={brands} />
      case 'competitive': return <CompetitiveGapPanel workspaceId={workspaceId} brands={brands} />
      case 'scores':
        // 'scores' sekmesi: benchmark widget'ı + filtreler + skor ızgarası
        return (
          <>
            <BenchmarkWidget workspaceId={workspaceId} />

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
                <div className="empty-icon" aria-hidden="true">📡</div>
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
        )
    }
    // Bütün sekme anahtarları yukarıda açıkça kapsanmış olmalıdır.
    // Yeni bir sekme eklendiğinde TS burada hata verir (never kontrolü) —
    // böylece yeni sekmenin içerik üreticisi unutulamaz.
    const _exhaustive: never = key
    return _exhaustive
  }

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>{t('dashboard.title')}</h2>
        <button className="refresh-btn" onClick={loadAll}>
          {t('dashboard.refresh')}
        </button>
      </div>

      {/* Pin'lenen sekmeler — hızlı erişim çipleri */}
      {pinned.length > 0 && (
        <div className="tab-pins" aria-label={t('dashboard.pinned_tabs')}>
          {pinned.map(key => {
            const pinnedTab = tabsMap.get(key)
            if (!pinnedTab) return null
            return (
              <div
                key={key}
                className={`tab-pin-chip ${activeTab === key ? 'active' : ''}`}
              >
                <button
                  className="tab-pin-chip-main"
                  onClick={() => selectTab(key)}
                  title={pinnedTab.label}
                >
                  📌 {pinnedTab.label}
                </button>
                <button
                  className="tab-pin-chip-x"
                  aria-label={t('dashboard.unpin_tab', { name: pinnedTab.label })}
                  title={t('dashboard.unpin_tab', { name: pinnedTab.label })}
                  onClick={() => togglePin(key)}
                >
                  ✕
                </button>
              </div>
            )
          })}
        </div>
      )}

      {/* Gruplu navigasyon */}
      <div className="tab-nav" ref={navRef}>
        <button
          className={`tab-nav-trigger ${navOpen ? 'open' : ''}`}
          onClick={navOpen ? closeNav : openNav}
          aria-haspopup="menu"
          aria-expanded={navOpen}
          title={t('dashboard.nav_menu')}
        >
          <span className="tab-nav-trigger-label">☰ {activeTabLabel}</span>
          <span className={`tab-nav-caret ${navOpen ? 'open' : ''}`}>▾</span>
        </button>
        {navOpen && (
          <div className="tab-nav-menu" role="menu" aria-label={t('dashboard.nav_menu')} onKeyDown={handleMenuKeyDown}>
            {/* Canlı arama: yazdıkça eşleşen sekmeler filtrelenir */}
            <div className="tab-nav-search">
              <input
                ref={navSearchRef}
                className="tab-nav-search-input"
                placeholder={t('dashboard.nav_search')}
                value={navQuery}
                onChange={e => setNavQuery(e.target.value)}
                onKeyDown={handleSearchKeyDown}
                aria-label={t('dashboard.nav_search')}
              />
              {navQuery && (
                <button className="tab-nav-search-clear" onClick={() => setNavQuery('')} aria-label={t('dashboard.nav_clear')}>
                  ✕
                </button>
              )}
            </div>

            {visibleGroups.length === 0 ? (
              <div className="tab-nav-no-results">{t('dashboard.nav_no_results', { query: navQuery })}</div>
            ) : (
              visibleGroups.map(group => {
                const collapsed = !query && collapsedGroups.has(group.key)
                const header = (
                  <>
                    <span>{group.label}</span>
                    <span className="tab-group-count">{group.tabs.length}</span>
                    {!query && <span className={`tab-group-caret ${collapsed ? '' : 'open'}`}>▸</span>}
                  </>
                )
                return (
                  <div
                    key={group.key}
                    className="tab-group"
                    role={query ? 'group' : undefined}
                    aria-label={query ? group.label : undefined}
                  >
                    {query ? (
                      <div className="tab-group-header">{header}</div>
                    ) : (
                      <button className="tab-group-header" onClick={() => toggleGroup(group.key)} aria-expanded={!collapsed}>
                        {header}
                      </button>
                    )}
                    {!collapsed && (
                      <div className="tab-group-items">
                        {group.tabs.map(tab => (
                          <div key={tab.key} className="tab-group-item-row">
                            <button
                              ref={activeTab === tab.key ? activeItemRef : undefined}
                              className={`tab-group-item ${activeTab === tab.key ? 'active' : ''}`}
                              role="menuitem"
                              onClick={() => selectTab(tab.key)}
                            >
                              {query ? highlightLabel(tab.label) : tab.label}
                            </button>
                            <button
                              className={`tab-pin-btn ${pinned.includes(tab.key) ? 'pinned' : ''}`}
                              role="menuitem"
                              aria-pressed={pinned.includes(tab.key)}
                              aria-label={t('dashboard.pin_tab', { name: tab.label })}
                              title={t(pinned.includes(tab.key) ? 'dashboard.unpin_tab' : 'dashboard.pin_tab', { name: tab.label })}
                              onClick={() => togglePin(tab.key)}
                            >
                              📌
                            </button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )
              })
            )}
          </div>
        )}
      </div>

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        {/* Keep-alive: bir kez yüklenen sekme DOM'da kalır (yalnızca gizlenir),
            böylece sekme değişiminde veriler yeniden yüklenmez, geçişler anlıktır */}
        {Array.from(visitedTabs).map(key => (
          <div key={key} className="tab-panel" hidden={key !== activeTab}>
            {renderTabContent(key)}
          </div>
        ))}
      </Suspense>
    </div>
  )
}
