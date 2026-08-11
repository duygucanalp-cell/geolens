import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import {
  getContentHubScore, analyzeContentGap, listContentGaps, listTopicClusters,
} from '../api/client'
import { PanelSkeleton } from './PanelSkeleton'
import type { Brand, ContentHubScore, ContentGap, TopicCluster } from '../types'

interface Props {
  workspaceId: string
  brands: Brand[]
  // Birleşik sayfada dışarıdan kontrol edilir: marka + yenileme durumu
  // ortak başlıktan gelir, panel kendi seçicisini gizler.
  embedded?: boolean
  brandId?: string
  refreshTick?: number
}

const PRIORITY_COLORS: Record<string, string> = {
  high: '#ef4444', medium: '#f97316', low: '#22c55e',
}

const GAP_TYPE_LABELS: Record<string, string> = {
  blog: '📝 Blog/Makale', product: '🛍 Ürün sayfası', faq: '❓ FAQ/SSS',
  news: '📰 Haber/Basın', category: '🗂 Kategori sayfası', general: '📊 Genel',
}

export function ContentGeoPanel({
  workspaceId: ws,
  brands,
  embedded,
  brandId: controlledBrandId,
  refreshTick = 0,
}: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [internalBrandId, setInternalBrandId] = useState(brands[0]?.id ?? '')
  const brandId = controlledBrandId ?? internalBrandId
  const [hub, setHub] = useState<ContentHubScore | null>(null)
  const [gaps, setGaps] = useState<ContentGap[]>([])
  const [topics, setTopics] = useState<TopicCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [analyzing, setAnalyzing] = useState(false)

  useEffect(() => {
    // Kontrollü modda marka düzeltmesini üst katman (MergedGeoTab) yapar;
    // bağımsız kullanımda iç durum güncellenir.
    if (brands.length > 0 && !brands.some(b => b.id === brandId)) {
      if (!embedded) setInternalBrandId(brands[0].id)
    }
  }, [brands, brandId, embedded])

  useEffect(() => {
    if (brandId) loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [brandId, refreshTick])

  async function loadAll() {
    try {
      setLoading(true)
      setError(null)
      const [hubData, gapsData, topicsData] = await Promise.all([
        getContentHubScore(ws, brandId).catch(() => null),
        listContentGaps(ws, brandId),
        listTopicClusters(ws, brandId),
      ])
      setHub(hubData)
      setGaps(gapsData)
      setTopics(topicsData)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('contentgeo.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function runGapAnalysis() {
    setAnalyzing(true)
    setError(null)
    try {
      await analyzeContentGap(ws, brandId)
      loadAll()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('contentgeo.analyze_error'))
    } finally {
      setAnalyzing(false)
    }
  }

  if (loading) return <PanelSkeleton message={t('contentgeo.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>📚 {t('contentgeo.title')}</h3>
        <p className="rec-desc">{t('contentgeo.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      {/* Marka + yenileme birleşik sayfada ortak başlıkta; analiz butonu panele özeldir */}
      <div className="dashboard-filters">
        {!embedded && (
          <select className="filter-select" value={brandId} onChange={e => setInternalBrandId(e.target.value)}>
            {brands.map(b => (
              <option key={b.id} value={b.id}>{b.name}</option>
            ))}
          </select>
        )}
        {!embedded && (
          <button className="refresh-btn" onClick={loadAll}>{t('common.refresh')}</button>
        )}
        <button className="audit-btn" onClick={runGapAnalysis} disabled={analyzing}>
          {analyzing ? t('contentgeo.analyzing') : t('contentgeo.analyze')}
        </button>
      </div>

      {/* Hub score */}
      {hub && (
        <>
          <div className="rec-summary">
            <div className="rec-summary-card total">
              <span className="rec-summary-value">{hub.overall.toFixed(1)}</span>
              <span className="rec-summary-label">{t('contentgeo.hub_score')}</span>
            </div>
            <div className="rec-summary-card" style={{ background: 'var(--success-bg)' }}>
              <span className="rec-summary-value" style={{ color: '#22c55e' }}>{hub.topic_coverage.toFixed(0)}%</span>
              <span className="rec-summary-label">{t('contentgeo.topic_coverage')}</span>
            </div>
            <div className="rec-summary-card" style={{ background: 'var(--accent-soft)' }}>
              <span className="rec-summary-value" style={{ color: 'var(--accent)' }}>{hub.source_diversity.toFixed(0)}%</span>
              <span className="rec-summary-label">{t('contentgeo.source_diversity')}</span>
            </div>
            <div className="rec-summary-card" style={{ background: '#fffbeb' }}>
              <span className="rec-summary-value" style={{ color: '#d97706' }}>{hub.opportunity_gap.toFixed(1)}</span>
              <span className="rec-summary-label">{t('contentgeo.opportunity_gap')}</span>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
            <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>{t('contentgeo.grade')}:</span>
            <span style={{
              background: 'var(--accent-soft)', color: 'var(--accent-hover)', padding: '0.15rem 0.6rem',
              borderRadius: '6px', fontWeight: 700, fontSize: '1rem',
            }}>{hub.grade}</span>
          </div>
        </>
      )}

      {/* Gaps */}
      <h4 style={{ margin: '0 0 0.5rem' }}>🔍 {t('contentgeo.gaps_title')}</h4>
      {gaps.length === 0 ? (
        <div className="rec-empty" style={{ padding: '1rem' }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-faint)' }}>{t('contentgeo.gaps_empty')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {gaps.map(g => (
            <div key={g.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: PRIORITY_COLORS[g.priority] || '#f97316' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{GAP_TYPE_LABELS[g.gap_type] || g.gap_type}</span>
                  <span className="rec-status-badge" style={{
                    background: g.priority === 'high' ? 'var(--danger-bg)' : '#fffbeb',
                    color: PRIORITY_COLORS[g.priority] || '#d97706',
                  }}>{g.priority}</span>
                </div>
                <div style={{ margin: '0.4rem 0' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    <span>{t('contentgeo.gap_score')}</span>
                    <span>{(g.gap_score * 100).toFixed(0)}%</span>
                  </div>
                  <div style={{ background: 'var(--surface-hover)', borderRadius: '6px', height: '6px', marginTop: '0.2rem' }}>
                    <div style={{ width: `${Math.min(g.gap_score * 100, 100)}%`, height: '100%', borderRadius: '6px', background: g.gap_score > 0.7 ? '#ef4444' : g.gap_score > 0.4 ? '#f97316' : '#eab308' }} />
                  </div>
                </div>
                <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{g.description}</p>
                {g.recommendation && (
                  <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--accent-hover)' }}>💡 {g.recommendation}</p>
                )}
                <span className="rec-date">{new Date(g.analyzed_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Topic clusters */}
      <h4 style={{ margin: '1.5rem 0 0.5rem' }}>🧭 {t('contentgeo.topics_title')}</h4>
      {topics.length === 0 ? (
        <div className="rec-empty" style={{ padding: '1rem' }}>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-faint)' }}>{t('contentgeo.topics_empty')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {topics.map(topic => (
            <div key={topic.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: topic.opportunity_score > 70 ? '#ef4444' : topic.opportunity_score > 40 ? '#f97316' : '#22c55e' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{topic.topic_name}</span>
                  <span className="rec-confidence-label">{t('contentgeo.opportunity')}: {topic.opportunity_score.toFixed(0)}</span>
                </div>
                {topic.relevance && <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{topic.relevance}</p>}
                {topic.recommendation && (
                  <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--accent-hover)' }}>💡 {topic.recommendation}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
