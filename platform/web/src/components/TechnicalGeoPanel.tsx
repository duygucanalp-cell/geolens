import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import {
  getTechnicalGEOScore, analyzeBotAccess, listBotAnalyses,
  analyzeSchema, listSchemaAnalyses,
} from '../api/client'
import { PanelSkeleton } from './PanelSkeleton'
import type { Brand, TechnicalGEOScore, BotAnalysis, SchemaAnalysis } from '../types'

interface Props {
  workspaceId: string
  brands: Brand[]
}

const GRADE_COLORS: Record<string, string> = {
  A: '#22c55e', B: '#84cc16', C: '#eab308', D: '#f97316', F: '#ef4444',
}

export function TechnicalGeoPanel({ workspaceId: ws, brands }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [brandId, setBrandId] = useState(brands[0]?.id ?? '')
  const [score, setScore] = useState<TechnicalGEOScore | null>(null)
  const [bots, setBots] = useState<BotAnalysis[]>([])
  const [schemas, setSchemas] = useState<SchemaAnalysis[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [analyzing, setAnalyzing] = useState<'bots' | 'schema' | null>(null)

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
      const [scoreData, botsData, schemasData] = await Promise.all([
        getTechnicalGEOScore(ws, brandId).catch(() => null),
        listBotAnalyses(ws, brandId),
        listSchemaAnalyses(ws, brandId),
      ])
      setScore(scoreData)
      setBots(botsData)
      setSchemas(schemasData)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('technical.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function runAnalyze(kind: 'bots' | 'schema') {
    setAnalyzing(kind)
    setError(null)
    try {
      if (kind === 'bots') {
        await analyzeBotAccess(ws, brandId)
      } else {
        await analyzeSchema(ws, brandId)
      }
      loadAll()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('technical.analyze_error'))
    } finally {
      setAnalyzing(null)
    }
  }

  const blockedBots = bots.filter(b => b.is_blocked).length

  if (loading) return <PanelSkeleton message={t('technical.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>⚙️ {t('technical.title')}</h3>
        <p className="rec-desc">{t('technical.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <select className="filter-select" value={brandId} onChange={e => setBrandId(e.target.value)}>
          {brands.map(b => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadAll}>{t('common.refresh')}</button>
      </div>

      {/* Score */}
      {score && (
        <div className="rec-summary">
          <div className="rec-summary-card total">
            <span className="rec-summary-value">{score.overall.toFixed(1)}</span>
            <span className="rec-summary-label">{t('technical.overall')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: 'var(--success-bg)' }}>
            <span className="rec-summary-value" style={{ color: '#22c55e' }}>{score.bot_score.toFixed(1)}</span>
            <span className="rec-summary-label">{t('technical.bot_score')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: 'var(--accent-soft)' }}>
            <span className="rec-summary-value" style={{ color: 'var(--accent)' }}>{score.schema_score.toFixed(1)}</span>
            <span className="rec-summary-label">{t('technical.schema_score')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: '#fffbeb' }}>
            <span className="rec-summary-value" style={{ color: GRADE_COLORS[score.grade] || 'var(--text-muted)' }}>{score.grade}</span>
            <span className="rec-summary-label">{t('technical.grade')}</span>
          </div>
        </div>
      )}

      {/* Bot analysis */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '1rem 0 0.5rem' }}>
        <h4 style={{ margin: 0 }}>🤖 {t('technical.bots_title')} <span style={{ color: blockedBots > 0 ? '#ef4444' : '#22c55e', fontSize: '0.8rem' }}>
          ({blockedBots}/{bots.length} {t('technical.blocked')})
        </span></h4>
        <button className="audit-btn" onClick={() => runAnalyze('bots')} disabled={analyzing !== null}>
          {analyzing === 'bots' ? t('technical.analyzing') : t('technical.analyze_bots')}
        </button>
      </div>

      {bots.length === 0 ? (
        <div className="rec-empty" style={{ padding: '1rem' }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-faint)' }}>{t('technical.bots_empty')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {bots.map(b => (
            <div key={b.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: b.is_blocked ? '#ef4444' : '#22c55e' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{b.bot_name}</span>
                  <span className="rec-status-badge" style={{
                    background: b.is_blocked ? 'var(--danger-bg)' : 'var(--success-bg)',
                    color: b.is_blocked ? '#ef4444' : '#22c55e',
                  }}>
                    {b.is_blocked ? t('technical.blocked') : t('technical.allowed')}
                  </span>
                  <span className="rec-date">{new Date(b.analyzed_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                </div>
                <div className="rec-meta">
                  <span className="rec-confidence-label">{t('technical.rule')}: {b.robots_txt_rule}</span>
                  <span className="rec-confidence-label">GES: {b.ges_score.toFixed(0)}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Schema analysis */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '1.5rem 0 0.5rem' }}>
        <h4 style={{ margin: 0 }}>🧩 {t('technical.schema_title')}</h4>
        <button className="audit-btn" onClick={() => runAnalyze('schema')} disabled={analyzing !== null}>
          {analyzing === 'schema' ? t('technical.analyzing') : t('technical.analyze_schema')}
        </button>
      </div>

      {schemas.length === 0 ? (
        <div className="rec-empty" style={{ padding: '1rem' }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-faint)' }}>{t('technical.schema_empty')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {schemas.map(s => (
            <div key={s.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: s.is_present ? '#22c55e' : '#f97316' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{s.schema_type}</span>
                  <span className="rec-status-badge" style={{
                    background: s.is_present ? 'var(--success-bg)' : '#fffbeb',
                    color: s.is_present ? '#22c55e' : '#d97706',
                  }}>
                    {s.is_present ? '✓' : '✕'}
                  </span>
                  <span className="rec-confidence-label">{s.schema_score.toFixed(0)}/100</span>
                </div>
                {s.recommendation && <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{s.recommendation}</p>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
