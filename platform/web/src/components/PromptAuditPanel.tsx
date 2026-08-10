import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listPromptAudits, runPromptAudit, getPromptAudit } from '../api/client'
import type { PromptAudit } from '../types'

interface Props { workspaceId: string }

export function PromptAuditPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [audits, setAudits] = useState<PromptAudit[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<PromptAudit | null>(null)

  const [showRun, setShowRun] = useState(false)
  const [promptText, setPromptText] = useState('')
  const [modelName, setModelName] = useState('')
  const [running, setRunning] = useState(false)

  const [detail, setDetail] = useState<PromptAudit | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => { loadAudits() }, [])

  async function loadAudits() {
    try { setLoading(true); setError(null); const res = await listPromptAudits(); setAudits(res.data) }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleRun(e: React.FormEvent) {
    e.preventDefault()
    if (!promptText.trim() || !modelName.trim()) return
    setRunning(true)
    try {
      const result = await runPromptAudit({ prompt_text: promptText, model: modelName })
      setPromptText(''); setModelName(''); setShowRun(false)
      setAudits(prev => [result, ...prev])
      setDetail(result)
      setSelected(result)
    } catch (err) { setError(err instanceof Error ? err.message : t('bias.eval_error')) }
    finally { setRunning(false) }
  }

  async function handleSelect(audit: PromptAudit) {
    setSelected(audit)
    setDetailLoading(true)
    try {
      const d = await getPromptAudit(audit.id)
      setDetail(d)
    } catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')); setDetail(null) }
    finally { setDetailLoading(false) }
  }

  const catColors: Record<string, string> = {
    safe: '#22c55e', suspicious: '#eab308', risky: '#f97316', critical: '#ef4444',
  }

  if (loading) return <PanelSkeleton message={t('promptaudit.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('promptaudit.title')}</h3>
        <p className="rec-desc">{t('promptaudit.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => { setShowRun(!showRun); setDetail(null) }}>
          {showRun ? t('guardrails.cancel') : t('promptaudit.run')}
        </button>
        <button className="refresh-btn" onClick={loadAudits}>{t('common.refresh')}</button>
      </div>

      {showRun && (
        <form onSubmit={handleRun} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <textarea
              className="notif-input"
              rows={3}
              placeholder={t('promptaudit.prompt_placeholder')}
              value={promptText}
              onChange={e => setPromptText(e.target.value)}
              required
              style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}
            />
            <input
              className="notif-input"
              placeholder={t('promptaudit.model_placeholder')}
              value={modelName}
              onChange={e => setModelName(e.target.value)}
              required
            />
            <button type="submit" className="audit-btn" disabled={running}>
              {running ? t('bias.evaluating') : t('promptaudit.run')}
            </button>
          </div>
        </form>
      )}

      {detail && (
        <div style={{ background: '#f0f9ff', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          {detailLoading && <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>{t('common.loading')}</p>}
          {!detailLoading && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <strong style={{ fontSize: '1rem' }}>{t('promptaudit.detail_title')}</strong>
                  <span className="rec-category-badge" style={{ marginLeft: '0.5rem', background: catColors[detail.category] || 'var(--text-faint)', color: '#fff' }}>
                    {detail.category}
                  </span>
                </div>
                <button className="rec-dismiss-btn" onClick={() => { setDetail(null); setSelected(null) }}>✕</button>
              </div>
              <p style={{ marginTop: '0.75rem', fontSize: '0.9rem', background: 'var(--surface)', padding: '0.5rem', borderRadius: '6px', border: '1px solid var(--border)', whiteSpace: 'pre-wrap' }}>
                {detail.prompt_text}
              </p>
              <div className="rec-meta" style={{ marginTop: '0.5rem' }}>
                <span className="rec-confidence-label">{t('promptaudit.model')}: {detail.model}</span>
                <span className="rec-date">{t('promptaudit.risk')}: {(detail.risk_score * 100).toFixed(0)}%</span>
                <span className="rec-date">{new Date(detail.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', year: 'numeric' })}</span>
              </div>
              {detail.findings.length > 0 && (
                <div style={{ marginTop: '0.75rem' }}>
                  <strong style={{ fontSize: '0.85rem' }}>{t('promptaudit.findings')}</strong>
                  <ul style={{ marginTop: '0.25rem', padding: '0 0 0 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                    {detail.findings.map((f, i) => <li key={i}>{f}</li>)}
                  </ul>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {audits.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🔍</div>
          <h4>{t('promptaudit.empty')}</h4>
          <p>{t('promptaudit.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {audits.map(a => (
            <div
              key={a.id}
              className="rec-card"
              style={{ cursor: 'pointer', ...(selected?.id === a.id ? { border: '2px solid #3b82f6' } : {}) }}
              onClick={() => handleSelect(a)}
            >
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: a.risk_score > 0.7 ? '#ef4444' : a.risk_score > 0.4 ? '#f97316' : '#22c55e' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{a.category}</span>
                  <span className="rec-status-badge" style={{
                    background: a.risk_score > 0.7 ? 'var(--danger-bg)' : a.risk_score > 0.4 ? '#fffbeb' : 'var(--success-bg)',
                    color: a.risk_score > 0.7 ? '#ef4444' : a.risk_score > 0.4 ? '#eab308' : '#22c55e',
                  }}>
                    {t('promptaudit.risk')}: {(a.risk_score * 100).toFixed(0)}%
                  </span>
                </div>
                <p className="rec-title" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>
                  {a.prompt_text}
                </p>
                <div className="rec-meta">
                  <span className="rec-confidence-label">{t('promptaudit.model')}: {a.model}</span>
                  <span className="rec-date">{new Date(a.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default PromptAuditPanel
