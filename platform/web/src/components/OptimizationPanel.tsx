import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getOptimizationRecommendations, generateOptimizationRecommendations, updateOptimizationStatus } from '../api/client'
import type { OptimizationRec } from '../types'

interface Props {
  workspaceId: string
}

const IMPACT_COLORS: Record<string, string> = {
  high: '#ef4444',
  medium: '#eab308',
  low: '#22c55e',
}

export function OptimizationPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const IMPACT_LABELS: Record<string, string> = {
    high: t('impact.high'),
    medium: t('impact.medium'),
    low: t('impact.low'),
  }
  const STATUS_LABELS: Record<string, string> = {
    pending: t('optimization.status_pending'),
    implemented: t('optimization.status_implemented'),
    dismissed: t('optimization.status_dismissed'),
  }
  const [recs, setRecs] = useState<OptimizationRec[]>([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  useEffect(() => { loadRecs() }, [])

  async function loadRecs() {
    try {
      setLoading(true)
      setError(null)
      const data = await getOptimizationRecommendations()
      setRecs(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('optimization.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleGenerate() {
    try {
      setGenerating(true)
      setError(null)
      const result = await generateOptimizationRecommendations(true)
      setRecs((prev) => [...result.recommendations, ...prev])
      setSuccessMsg(`${result.count} yeni optimizasyon önerisi oluşturuldu.`)
      setTimeout(() => setSuccessMsg(null), 5000)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('optimization.generate_error'))
    } finally {
      setGenerating(false)
    }
  }

  async function handleUpdateStatus(recId: string, status: string) {
    try {
      await updateOptimizationStatus(recId, status)
      setRecs((prev) => prev.map((r) => r.id === recId ? { ...r, status } : r))
      setSuccessMsg(`Öneri ${STATUS_LABELS[status] || status} olarak işaretlendi.`)
      setTimeout(() => setSuccessMsg(null), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('optimization.status_update_error'))
    }
  }

  if (loading) return <div className="dashboard-loading">Optimizasyon önerileri yükleniyor...</div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>⚡ Optimizasyon Önerileri</h3>
        <p className="rec-desc">AI görünürlük performansınızı artıracak eyleme dönüştürülebilir öneriler.</p>
      </div>

      {error && <div className="audit-error">{error}</div>}
      {successMsg && <div className="notif-settings-success-msg">{successMsg}</div>}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={handleGenerate} disabled={generating}>
          {generating ? t('optimization.generating') : t('optimization.generate')}
        </button>
      </div>

      {recs.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">⚡</div>
          <h4>Henüz optimizasyon önerisi yok</h4>
          <p>"Öneri Oluştur" butonuna tıklayarak AI görünürlük skorunuzu artıracak öneriler alın.</p>
        </div>
      ) : (
        <div className="rec-list">
          {recs.map((rec) => (
            <div key={rec.id} className={`rec-card ${rec.status !== 'pending' ? 'applied' : ''}`}>
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: IMPACT_COLORS[rec.impact] || '#94a3b8' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-severity-badge" style={{
                    color: IMPACT_COLORS[rec.impact] || '#94a3b8',
                    borderColor: IMPACT_COLORS[rec.impact] || '#94a3b8',
                  }}>
                    {IMPACT_LABELS[rec.impact] || rec.impact}
                  </span>
                  <span className="rec-category-badge">{rec.category}</span>
                  <span className="rec-status-badge" style={{
                    background: rec.status === 'implemented' ? '#f0fdf4' : rec.status === 'dismissed' ? '#fef2f2' : '#f8fafc',
                    color: rec.status === 'implemented' ? '#22c55e' : rec.status === 'dismissed' ? '#ef4444' : '#64748b',
                  }}>
                    {STATUS_LABELS[rec.status] || rec.status}
                  </span>
                </div>
                <h4 className="rec-title">{rec.title}</h4>
                <p className="rec-detail">{rec.description}</p>
                <div className="rec-meta">
                  <div className="rec-confidence">
                    <div className="rec-confidence-bar">
                      <div className="rec-confidence-fill" style={{
                        width: `${rec.score_potential * 5}%`,
                        backgroundColor: rec.score_potential > 15 ? '#22c55e' : rec.score_potential > 8 ? '#eab308' : '#94a3b8',
                      }} />
                    </div>
                    <span className="rec-confidence-label">+{rec.score_potential} puan potansiyeli</span>
                  </div>
                  <span className="rec-date">
                    {new Date(rec.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}
                  </span>
                </div>
              </div>
              {rec.status === 'pending' && (
                <div className="rec-card-actions">
                  <button className="rec-apply-btn" onClick={() => handleUpdateStatus(rec.id, 'implemented')} title="Uygulandı">✓</button>
                  <button className="rec-dismiss-btn" onClick={() => handleUpdateStatus(rec.id, 'dismissed')} title="Reddet">✕</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
