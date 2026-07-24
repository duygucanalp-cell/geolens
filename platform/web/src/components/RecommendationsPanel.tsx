import { useEffect, useMemo, useState } from 'react'
import { getRecommendations, markRecommendationApplied, markRecommendationDismissed } from '../api/client'
import type { Recommendation, Brand } from '../types'
import { SEVERITY_LABELS, CATEGORY_LABELS, SEVERITY_COLORS, ENGINE_COLORS } from '../types'

interface Props {
  workspaceId: string
  brands: Brand[]
}

export function RecommendationsPanel({ workspaceId, brands }: Props) {
  const [recommendations, setRecommendations] = useState<Recommendation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filterBrand, setFilterBrand] = useState<string>('all')
  const [actionInProgress, setActionInProgress] = useState<string | null>(null)

  useEffect(() => {
    loadRecommendations()
  }, [workspaceId])

  async function loadRecommendations() {
    try {
      setLoading(true)
      setError(null)
      const data = await getRecommendations(workspaceId)
      setRecommendations(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Öneriler yüklenemedi')
    } finally {
      setLoading(false)
    }
  }

  async function handleApply(recId: string) {
    try {
      setActionInProgress(recId)
      await markRecommendationApplied(workspaceId, recId)
      setRecommendations((prev) =>
        prev.map((r) => (r.id === recId ? { ...r, applied: true } : r))
      )
    } catch (err) {
      console.error('Öneri uygulama hatası:', err)
    } finally {
      setActionInProgress(null)
    }
  }

  async function handleDismiss(recId: string) {
    try {
      setActionInProgress(recId)
      await markRecommendationDismissed(workspaceId, recId)
      setRecommendations((prev) =>
        prev.map((r) => (r.id === recId ? { ...r, dismissed: true } : r))
      )
    } catch (err) {
      console.error('Öneri gizleme hatası:', err)
    } finally {
      setActionInProgress(null)
    }
  }

  const brandMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const b of brands) {
      map.set(b.id, b.name)
    }
    return map
  }, [brands])

  // Count by severity for summary
  const summary = useMemo(() => {
    const active = recommendations.filter((r) => !r.applied && !r.dismissed)
    const critical = active.filter((r) => r.severity === 'critical')
    const high = active.filter((r) => r.severity === 'high')
    return { total: active.length, critical: critical.length, high: high.length }
  }, [recommendations])

  // Filtered list
  const filtered = useMemo(() => {
    let result = recommendations.filter((r) => !r.dismissed)
    if (filterBrand !== 'all') {
      result = result.filter((r) => r.brand_id === filterBrand)
    }
    return result
  }, [recommendations, filterBrand])

  if (loading) {
    return <div className="rec-loading">Öneriler yükleniyor...</div>
  }

  if (error) {
    return (
      <div className="rec-error">
        <p>{error}</p>
        <button className="rec-retry-btn" onClick={loadRecommendations}>Tekrar Dene</button>
      </div>
    )
  }

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>AI Önerileri</h3>
        <p className="rec-desc">
          Markalarınızın AI görünürlük performansına göre oluşturulmuş eyleme dönüştürülebilir öneriler.
        </p>
      </div>

      {/* Summary cards */}
      {summary.total > 0 && (
        <div className="rec-summary">
          <div className="rec-summary-card critical">
            <span className="rec-summary-value">{summary.critical}</span>
            <span className="rec-summary-label">Kritik</span>
          </div>
          <div className="rec-summary-card high">
            <span className="rec-summary-value">{summary.high}</span>
            <span className="rec-summary-label">Yüksek</span>
          </div>
          <div className="rec-summary-card total">
            <span className="rec-summary-value">{summary.total}</span>
            <span className="rec-summary-label">Toplam</span>
          </div>
        </div>
      )}

      {/* Brand filter */}
      {brands.length > 0 && (
        <div className="rec-filters">
          <select
            value={filterBrand}
            onChange={(e) => setFilterBrand(e.target.value)}
            className="filter-select"
          >
            <option value="all">Tüm Markalar</option>
            {brands.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Empty state */}
      {filtered.length === 0 && (
        <div className="rec-empty">
          <div className="rec-empty-icon">💡</div>
          <h4>Henüz öneri yok</h4>
          <p>
            Skorlar oluştukça ve denetimler tamamlandıkça burada AI görünürlüğünüzü
            iyileştirecek öneriler görünecek.
          </p>
        </div>
      )}

      {/* Recommendation cards */}
      <div className="rec-list">
        {filtered.map((rec) => (
          <div
            key={rec.id}
            className={`rec-card ${rec.applied ? 'applied' : ''}`}
          >
            <div className="rec-card-left">
              {/* Severity indicator */}
              <div
                className="rec-severity-bar"
                style={{ backgroundColor: SEVERITY_COLORS[rec.severity] || '#94a3b8' }}
              />
            </div>
            <div className="rec-card-content">
              <div className="rec-card-header">
                <span
                  className="rec-severity-badge"
                  style={{
                    color: SEVERITY_COLORS[rec.severity] || '#94a3b8',
                    borderColor: SEVERITY_COLORS[rec.severity] || '#94a3b8',
                  }}
                >
                  {SEVERITY_LABELS[rec.severity] || rec.severity}
                </span>
                <span className="rec-category-badge">
                  {CATEGORY_LABELS[rec.category] || rec.category}
                </span>
                {rec.brand_id && brandMap.has(rec.brand_id) && (
                  <span className="rec-brand-badge" style={{ backgroundColor: '#eef2ff', color: '#6366f1' }}>
                    {brandMap.get(rec.brand_id)}
                  </span>
                )}
                {rec.applied && <span className="rec-status-badge applied">Uygulandı</span>}
              </div>
              <h4 className="rec-title">{rec.title}</h4>
              <p className="rec-detail">{rec.detail}</p>
              <div className="rec-meta">
                <div className="rec-confidence">
                  <div className="rec-confidence-bar">
                    <div
                      className="rec-confidence-fill"
                      style={{
                        width: `${rec.score}%`,
                        backgroundColor: rec.score > 80 ? '#22c55e' : rec.score > 50 ? '#eab308' : '#94a3b8',
                      }}
                    />
                  </div>
                  <span className="rec-confidence-label">%{Math.round(rec.score)} güven</span>
                </div>
                <span className="rec-date">
                  {new Date(rec.created_at).toLocaleDateString('tr-TR', {
                    day: 'numeric',
                    month: 'short',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            </div>
            {!rec.applied && (
              <div className="rec-card-actions">
                <button
                  className="rec-apply-btn"
                  onClick={() => handleApply(rec.id)}
                  disabled={actionInProgress === rec.id}
                  title="Uygulandı olarak işaretle"
                >
                  ✓
                </button>
                <button
                  className="rec-dismiss-btn"
                  onClick={() => handleDismiss(rec.id)}
                  disabled={actionInProgress === rec.id}
                  title="Öneriyi gizle"
                >
                  ✕
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
