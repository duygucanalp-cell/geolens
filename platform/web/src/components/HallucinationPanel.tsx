import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { detectHallucinations, listHallucinations, verifyHallucination } from '../api/client'
import type { HallucinationFlag } from '../types'

interface Props { workspaceId: string }

export function HallucinationPanel({ workspaceId }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [flags, setFlags] = useState<HallucinationFlag[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [detectText, setDetectText] = useState('')
  const [detectResult, setDetectResult] = useState<HallucinationFlag | null>(null)
  const [detectLoading, setDetectLoading] = useState(false)

  useEffect(() => { loadFlags() }, [])

  async function loadFlags() {
    try {
      setLoading(true)
      setError(null)
      const res = await listHallucinations(workspaceId)
      setFlags(res.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('hallucination.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleDetect(e: React.FormEvent) {
    e.preventDefault()
    if (!detectText.trim()) return
    setDetectLoading(true)
    setDetectResult(null)
    try {
      const result = await detectHallucinations(workspaceId, detectText)
      setDetectResult(result)
      loadFlags()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('hallucination.detect_error'))
    } finally {
      setDetectLoading(false)
    }
  }

  async function handleVerify(flagId: string) {
    try {
      await verifyHallucination(workspaceId, flagId)
      loadFlags()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('hallucination.verify_error'))
    }
  }

  const totalFlags = flags.length
  const verifiedCount = flags.filter(f => f.is_verified).length

  if (loading) return <PanelSkeleton message={t('hallucination.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('hallucination.title')}</h3>
        <p className="rec-desc">{t('hallucination.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="rec-summary">
        <div className="rec-summary-card total">
          <span className="rec-summary-value">{totalFlags}</span>
          <span className="rec-summary-label">{t('hallucination.total')}</span>
        </div>
        <div className="rec-summary-card" style={{ background: verifiedCount > 0 ? 'var(--success-bg)' : 'var(--surface-2)' }}>
          <span className="rec-summary-value" style={{ color: '#22c55e' }}>{verifiedCount}</span>
          <span className="rec-summary-label">{t('hallucination.verified')}</span>
        </div>
        <div className="rec-summary-card" style={{ background: totalFlags - verifiedCount > 0 ? 'var(--danger-bg)' : 'var(--success-bg)' }}>
          <span className="rec-summary-value" style={{ color: totalFlags - verifiedCount > 0 ? '#ef4444' : '#22c55e' }}>
            {totalFlags - verifiedCount}
          </span>
          <span className="rec-summary-label">{t('hallucination.unverified')}</span>
        </div>
      </div>

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={loadFlags}>{t('hallucination.refresh')}</button>
      </div>

      <form onSubmit={handleDetect} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <textarea
            className="notif-input"
            rows={3}
            placeholder={t('hallucination.text_placeholder')}
            value={detectText}
            onChange={e => setDetectText(e.target.value)}
            style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}
          />
          <button type="submit" className="audit-btn" disabled={detectLoading}>
            {detectLoading ? t('hallucination.detecting') : t('hallucination.detect')}
          </button>
        </div>
      </form>

      {detectResult && (
        <div style={{ background: detectResult.confidence > 0.7 ? 'var(--danger-bg)' : 'var(--success-bg)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '1.5rem' }}>{detectResult.confidence > 0.7 ? '🔴' : '✅'}</span>
            <div>
              <strong>{detectResult.confidence > 0.7 ? t('hallucination.result_detected') : t('hallucination.result_clean')}</strong>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>{t('hallucination.confidence')}: {(detectResult.confidence * 100).toFixed(0)}%</p>
            </div>
          </div>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{detectResult.detail}</p>
        </div>
      )}

      {flags.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🧠</div>
          <h4>{t('hallucination.empty')}</h4>
          <p>{t('hallucination.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {flags.map(flag => (
            <div key={flag.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: flag.confidence > 0.7 ? '#ef4444' : '#f97316' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{flag.flag_type}</span>
                  <span className="rec-status-badge" style={{
                    background: flag.is_verified ? 'var(--success-soft)' : 'var(--danger-bg)',
                    color: flag.is_verified ? '#22c55e' : '#ef4444',
                  }}>
                    {flag.is_verified ? t('hallucination.tag_verified') : t('hallucination.tag_unverified')}
                  </span>
                </div>
                <h4 className="rec-title">{t('hallucination.model')}: {flag.model}</h4>
                <p className="rec-detail" style={{ maxHeight: '3rem', overflow: 'hidden', textOverflow: 'ellipsis' }}>{flag.text}</p>
                <div className="rec-meta">
                  <span className="rec-confidence-label">{t('hallucination.confidence')}: {(flag.confidence * 100).toFixed(0)}%</span>
                  <span className="rec-date">{new Date(flag.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                </div>
                {flag.detail && <p className="rec-detail" style={{ marginTop: '0.3rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>{flag.detail}</p>}
              </div>
              <div className="rec-card-actions">
                {!flag.is_verified && (
                  <button className="rec-apply-btn" onClick={() => handleVerify(flag.id)} title={t('hallucination.verify')}>
                    ✓
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
