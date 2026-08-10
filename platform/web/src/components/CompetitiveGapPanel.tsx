import { useTranslation } from 'react-i18next'
import { useEffect, useMemo, useState } from 'react'
import { RadarChart, PolarGrid, PolarAngleAxis, Radar, Legend, Tooltip, ResponsiveContainer } from 'recharts'
import {
  getCompetitiveGapOverview, analyzeCompetitiveGap, getCompetitiveRecommendations,
} from '../api/client'
import type { Brand, CompetitiveGapOverview, GapSnapshot, CompetitiveRecommendation, GapDetail } from '../types'

interface Props {
  workspaceId: string
  brands: Brand[]
}

const GAP_COLUMNS: { key: keyof GapSnapshot; labelKey: string; overviewKey: keyof CompetitiveGapOverview }[] = [
  { key: 'visibility_gap', labelKey: 'competitive.visibility_gap', overviewKey: 'visibility_gap' },
  { key: 'citation_gap', labelKey: 'competitive.citation_gap', overviewKey: 'citation_gap' },
  { key: 'content_gap', labelKey: 'competitive.content_gap', overviewKey: 'content_gap' },
  { key: 'topic_gap', labelKey: 'competitive.topic_gap', overviewKey: 'topic_gap' },
  { key: 'prompt_gap', labelKey: 'competitive.prompt_gap', overviewKey: 'prompt_gap' },
]

const PRIORITY_COLORS: Record<string, string> = {
  critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#22c55e',
}

function directionIcon(dir: string | undefined): string {
  if (dir === 'brand_ahead') return '▲'
  if (dir === 'competitor_ahead') return '▼'
  return '▬'
}

function directionColor(dir: string | undefined): string {
  if (dir === 'brand_ahead') return '#22c55e'
  if (dir === 'competitor_ahead') return '#ef4444'
  return 'var(--text-faint)'
}

export function CompetitiveGapPanel({ workspaceId: ws, brands }: Props) {
  const { t } = useTranslation()

  const [brandId, setBrandId] = useState(brands[0]?.id ?? '')
  const [overview, setOverview] = useState<CompetitiveGapOverview[]>([])
  const [lastAnalysis, setLastAnalysis] = useState<GapSnapshot[]>([])
  const [recs, setRecs] = useState<CompetitiveRecommendation[]>([])
  const [radarCompetitor, setRadarCompetitor] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [analyzing, setAnalyzing] = useState(false)

  useEffect(() => {
    if (brands.length > 0 && !brands.some(b => b.id === brandId)) {
      setBrandId(brands[0].id)
    }
  }, [brands, brandId])

  useEffect(() => {
    if (brandId) loadAll()
  }, [brandId])

  async function loadAll() {
    try {
      setLoading(true)
      setError(null)
      const [overviewData, recsData] = await Promise.all([
        getCompetitiveGapOverview(ws, brandId),
        getCompetitiveRecommendations(ws, brandId),
      ])
      setOverview(overviewData)
      setRecs(recsData)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('competitive.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function runAnalysis() {
    setAnalyzing(true)
    setError(null)
    try {
      const snapshots = await analyzeCompetitiveGap(ws, brandId)
      setLastAnalysis(snapshots || [])
      // İlk rakibi radar için varsayılan seç
      setRadarCompetitor(prev => (snapshots?.length ? (snapshots.some(s => s.competitor_id === prev) ? prev : snapshots[0].competitor_id) : prev))
      loadAll()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('competitive.analyze_error'))
    } finally {
      setAnalyzing(false)
    }
  }

  // Radar verisi: seçilen rakibe karşı markanın her boyuttaki göreli payı (0-100).
  // brand_value/competitor_value farklı birimlerde olabileceği için toplam paya
  // normalize edilir — böylece eksenler karşılaştırılabilir olur.
  const radarData = useMemo(() => {
    if (lastAnalysis.length === 0) return []
    const snap = radarCompetitor
      ? lastAnalysis.find(s => s.competitor_id === radarCompetitor) ?? lastAnalysis[0]
      : lastAnalysis[0]
    const brandLabel = t('competitive.brand')
    const compLabel = snap.competitor_name
    return GAP_COLUMNS.map(col => {
      const g = snap[col.key] as GapDetail | undefined
      const brandV = g?.brand_value ?? 0
      const compV = g?.competitor_value ?? 0
      const total = brandV + compV
      const brandPct = total > 0 ? Math.min(100, Math.max(0, Math.round((brandV / total) * 100))) : 50
      return { axis: t(col.labelKey), [brandLabel]: brandPct, [compLabel]: 100 - brandPct }
    })
  }, [lastAnalysis, radarCompetitor, t])

  const radarCompetitorName = radarData.length > 0
    ? Object.keys(radarData[0]).find(k => k !== 'axis' && k !== t('competitive.brand')) ?? ''
    : ''

  if (loading) return <div className="dashboard-loading">{t('competitive.loading')}</div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>🥊 {t('competitive.title')}</h3>
        <p className="rec-desc">{t('competitive.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <select className="filter-select" value={brandId} onChange={e => setBrandId(e.target.value)}>
          {brands.map(b => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadAll}>{t('common.refresh')}</button>
        <button className="audit-btn" onClick={runAnalysis} disabled={analyzing}>
          {analyzing ? t('competitive.analyzing') : t('competitive.analyze')}
        </button>
      </div>

      {/* Latest analysis result (full gap details) */}
      {lastAnalysis.length > 0 && (
        <div style={{ marginBottom: '1.5rem' }}>
          <h4 style={{ margin: '0 0 0.5rem' }}>🧪 {t('competitive.latest_analysis')}</h4>
          {lastAnalysis.map(snap => (
            <div key={snap.competitor_id} className="rec-card" style={{ marginBottom: '0.75rem', padding: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <strong>{snap.competitor_name}</strong>
                <span className="rec-status-badge" style={{ background: 'var(--accent-soft)', color: 'var(--accent-hover)' }}>
                  {t('competitive.score')}: {snap.competitive_score.toFixed(1)}
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '0.5rem' }}>
                {GAP_COLUMNS.map(col => {
                  const gap = snap[col.key] as GapDetail | undefined
                  return (
                    <div key={col.key as string} style={{ background: 'var(--surface-2)', padding: '0.5rem 0.75rem', borderRadius: '8px' }}>
                      <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>{t(col.labelKey)}</div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginTop: '0.2rem' }}>
                        <span style={{ color: directionColor(gap?.direction), fontWeight: 700 }}>{directionIcon(gap?.direction)}</span>
                        <strong>{gap ? gap.normalized.toFixed(0) : '—'}</strong>
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-faint)' }}>({gap ? gap.brand_value : '—'} / {gap ? gap.competitor_value : '—'})</span>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Radar karşılaştırma: marka vs rakip (analiz sonrası) */}
      {lastAnalysis.length > 0 && (
        <div className="rec-card" style={{ marginBottom: '1.5rem', padding: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <h4 style={{ margin: 0 }}>📡 {t('competitive.radar_title')}</h4>
            <select
              className="filter-select"
              value={radarCompetitor || lastAnalysis[0]?.competitor_id}
              onChange={e => setRadarCompetitor(e.target.value)}
              aria-label={t('competitive.radar_competitor')}
            >
              {lastAnalysis.map(s => (
                <option key={s.competitor_id} value={s.competitor_id}>{s.competitor_name}</option>
              ))}
            </select>
          </div>
          <p style={{ fontSize: '0.78rem', color: 'var(--text-faint)', marginBottom: '0.5rem' }}>{t('competitive.radar_hint')}</p>
          <ResponsiveContainer width="100%" height={320}>
            <RadarChart data={radarData} cx="50%" cy="50%" outerRadius="72%">
              <PolarGrid stroke="#94a3b8" strokeOpacity={0.35} />
              <PolarAngleAxis dataKey="axis" tick={{ fontSize: 11, fill: 'var(--text-faint)' }} />
              <Radar
                name={t('competitive.brand')}
                dataKey={t('competitive.brand')}
                stroke="#6366f1"
                fill="#6366f1"
                fillOpacity={0.25}
              />
              <Radar
                name={radarCompetitorName}
                dataKey={radarCompetitorName}
                stroke="#ef4444"
                fill="#ef4444"
                fillOpacity={0.12}
              />
              <Legend />
              <Tooltip />
            </RadarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Overview table */}
      <h4 style={{ margin: '0 0 0.5rem' }}>📊 {t('competitive.overview_title')}</h4>
      {overview.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🥊</div>
          <h4>{t('competitive.empty_title')}</h4>
          <p>{t('competitive.empty_desc')}</p>
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="gap-table">
            <thead>
              <tr>
                <th>{t('competitive.competitor')}</th>
                {GAP_COLUMNS.map(col => <th key={col.key as string}>{t(col.labelKey)}</th>)}
                <th>{t('competitive.score')}</th>
              </tr>
            </thead>
            <tbody>
              {overview.map(row => (
                <tr key={row.id}>
                  <td><strong>{row.competitor_name}</strong></td>
                  {GAP_COLUMNS.map(col => {
                    const val = row[col.overviewKey] as number | undefined
                    return (
                      <td key={col.key as string} style={{
                        color: val !== undefined && val > 0 ? '#22c55e' : val !== undefined && val < 0 ? '#ef4444' : 'var(--text-muted)',
                      }}>
                        {val !== undefined ? `${val > 0 ? '+' : ''}${val}` : '—'}
                      </td>
                    )
                  })}
                  <td><strong style={{ color: 'var(--accent-hover)' }}>{row.competitive_score.toFixed(1)}</strong></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Recommendations */}
      <h4 style={{ margin: '1.5rem 0 0.5rem' }}>💡 {t('competitive.recs_title')}</h4>
      {recs.length === 0 ? (
        <div className="rec-empty" style={{ padding: '1rem' }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-faint)' }}>{t('competitive.recs_empty')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {recs.map(r => (
            <div key={r.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: PRIORITY_COLORS[r.priority] || '#f97316' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{r.gap_type}</span>
                  <span className="rec-status-badge" style={{
                    background: r.priority === 'critical' || r.priority === 'high' ? 'var(--danger-bg)' : '#fffbeb',
                    color: PRIORITY_COLORS[r.priority] || '#d97706',
                  }}>{r.priority}</span>
                  <span className="rec-date">{r.kanit_derecesi || ''}</span>
                </div>
                <p className="rec-detail" style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{r.description}</p>
                {r.impact && (
                  <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--accent-hover)' }}>🎯 {r.impact}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
