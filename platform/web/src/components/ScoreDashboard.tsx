import { useEffect, useMemo, useState } from 'react'
import { ScoreCard } from './ScoreCard'
import { TrendChart } from './TrendChart'
import { EngineComparison } from './EngineComparison'
import { AuditPanel } from './AuditPanel'
import { NotificationSettings } from './NotificationSettings'
import { ReportsPanel } from './ReportsPanel'
import { RecommendationsPanel } from './RecommendationsPanel'
import { MonitoringPanel } from './MonitoringPanel'
import { getScores, getBrands, getPanels } from '../api/client'
import type { Score, Brand, Panel } from '../types'
import { ENGINE_NAMES } from '../types'

interface ScoreDashboardProps {
  workspaceId: string
}

type Tab = 'scores' | 'audit' | 'notifications' | 'reports' | 'recommendations' | 'monitoring'

export function ScoreDashboard({ workspaceId }: ScoreDashboardProps) {
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
      setError(err instanceof Error ? err.message : 'Veriler yüklenemedi')
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

  // Filtrelenmiş skorlar
  const filteredScores = useMemo(() => {
    let result = scores
    if (filterEngine !== 'all') {
      result = result.filter((s) => {
        if (!s.engine_breakdown) return false
        return s.engine_breakdown[filterEngine] !== undefined
      })
    }
    return result
  }, [scores, filterEngine])

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
    return <div className="dashboard-loading">Yükleniyor...</div>
  }

  if (error) {
    return (
      <div className="dashboard-error">
        <p>{error}</p>
        <button onClick={loadAll}>Tekrar Dene</button>
      </div>
    )
  }

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h2>Görünürlük Panosu</h2>
        <button className="refresh-btn" onClick={loadAll}>
          Yenile
        </button>
      </div>

      {/* Tabs */}
      <div className="dashboard-tabs">
        <button
          className={`tab-btn ${activeTab === 'scores' ? 'active' : ''}`}
          onClick={() => setActiveTab('scores')}
        >
          Skorlar
        </button>
        <button
          className={`tab-btn ${activeTab === 'audit' ? 'active' : ''}`}
          onClick={() => setActiveTab('audit')}
        >
          Site Denetim
        </button>
        <button
          className={`tab-btn ${activeTab === 'reports' ? 'active' : ''}`}
          onClick={() => setActiveTab('reports')}
        >
          Raporlar
        </button>
        <button
          className={`tab-btn ${activeTab === 'notifications' ? 'active' : ''}`}
          onClick={() => setActiveTab('notifications')}
        >
          Bildirimler
        </button>
        <button
          className={`tab-btn ${activeTab === 'recommendations' ? 'active' : ''}`}
          onClick={() => setActiveTab('recommendations')}
        >
          Öneriler
        </button>
        <button
          className={`tab-btn ${activeTab === 'monitoring' ? 'active' : ''}`}
          onClick={() => setActiveTab('monitoring')}
        >
          İzleme
        </button>
      </div>

      {activeTab === 'audit' ? (
        <AuditPanel workspaceId={workspaceId} brands={brands} />
      ) : activeTab === 'reports' ? (
        <ReportsPanel workspaceId={workspaceId} />
      ) : activeTab === 'notifications' ? (
        <NotificationSettings workspaceId={workspaceId} />
      ) : activeTab === 'recommendations' ? (
        <RecommendationsPanel workspaceId={workspaceId} brands={brands} />
      ) : activeTab === 'monitoring' ? (
        <MonitoringPanel />
      ) : (
        <>
          {/* Filters */}
          <div className="dashboard-filters">
            {panels.length > 0 && (
              <select
                value={selectedPanel}
                onChange={(e) => setSelectedPanel(e.target.value)}
                className="filter-select"
              >
                <option value="all">Tüm Paneller</option>
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
              <option value="all">Tüm Motorlar</option>
              {availableEngines.map((e) => (
                <option key={e} value={e}>
                  {ENGINE_NAMES[e] || e}
                </option>
              ))}
            </select>
          </div>

          {filteredScores.length === 0 ? (
            <div className="dashboard-empty">
              <h2>Henüz skor yok</h2>
              <p>Bir marka ekleyip ölçüm başlatarak görünürlük skorunuzu görebilirsiniz.</p>
            </div>
          ) : (
            <>
              {/* Brand sections with trend + engine comparison */}
              {Array.from(scoresByBrand.entries()).map(([brandName, brandScores]) => (
                <div key={brandName} className="brand-section">
                  <TrendChart scores={brandScores} brandName={brandName} />
                  <EngineComparison scores={brandScores} brandName={brandName} />
                </div>
              ))}

              {/* Score cards grid */}
              <div className="scores-grid">
                {filteredScores.map((score) => (
                  <ScoreCard key={score.id} score={score} />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
