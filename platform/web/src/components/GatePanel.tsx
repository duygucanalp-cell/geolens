import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { runGateCheck, getGateHistory } from '../api/client'
import type { GateCheckResult, GateHistoryEntry } from '../types'

interface Props { workspaceId: string }

const DECISION_COLORS: Record<string, string> = { approved: '#22c55e', flagged: '#f59e0b', blocked: '#ef4444' }
export function GatePanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const DECISION_LABELS: Record<string, string> = { approved: t('gate.decision_approved'), flagged: t('gate.decision_flagged'), blocked: t('gate.decision_blocked') }
  const [entityId, setEntityId] = useState('')
  const [entityType, setEntityType] = useState('model')
  const [targetEnv, setTargetEnv] = useState('production')
  const [checkResult, setCheckResult] = useState<GateCheckResult | null>(null)
  const [history, setHistory] = useState<GateHistoryEntry[]>([])
  const [showHistory, setShowHistory] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleCheck(e: React.FormEvent) {
    e.preventDefault()
    if (!entityId.trim()) return
    setLoading(true)
    setError(null)
    setCheckResult(null)
    try {
      const result = await runGateCheck(entityId)
      setCheckResult(result)
      // Also load history
      try { setHistory(await getGateHistory(entityId)) } catch {}
    } catch (err) { setError(err instanceof Error ? err.message : t('gate.check_error')) }
    finally { setLoading(false) }
  }

  async function loadHistory() {
    if (!entityId.trim()) return
    try {
      setHistory(await getGateHistory(entityId))
      setShowHistory(!showHistory)
    } catch (err) { setError(err instanceof Error ? err.message : t('gate.history_error')) }
  }

  const historyItems = history

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('gate.title')}</h3>
        <p className="rec-desc">{t('gate.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <form onSubmit={handleCheck} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input className="notif-input" style={{ flex: 1 }} placeholder={t('gate.entity_placeholder')} value={entityId} onChange={e => setEntityId(e.target.value)} required />
            <select value={entityType} onChange={e => setEntityType(e.target.value)} className="filter-select">
              <option value="model">{t('gate.type_model')}</option><option value="agent">{t('gate.type_agent')}</option><option value="application">{t('gate.type_application')}</option>
            </select>
            <select value={targetEnv} onChange={e => setTargetEnv(e.target.value)} className="filter-select">
              <option value="development">{t('gate.env_development')}</option><option value="staging">{t('gate.env_staging')}</option><option value="production">{t('gate.env_production')}</option>
            </select>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button type="submit" className="audit-btn" disabled={loading}>
              {loading ? t('gate.checking') : t('gate.check')}
            </button>
            <button type="button" className="refresh-btn" onClick={loadHistory} disabled={!entityId.trim()}>
              {showHistory ? t('gate.hide_history') : t('gate.show_history')}
            </button>
          </div>
        </div>
      </form>

      {/* Check result */}
      {checkResult && (
        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{
            textAlign: 'center', padding: '1.5rem', borderRadius: '12px', marginBottom: '1rem',
            background: checkResult.decision === 'approved' ? 'var(--success-bg)' : checkResult.decision === 'flagged' ? 'var(--amber-bg)' : 'var(--danger-bg)',
          }}>
            <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>
              {checkResult.decision === 'approved' ? '✅' : checkResult.decision === 'flagged' ? '⚠️' : '🔴'}
            </div>
            <div style={{ fontSize: '1.1rem', fontWeight: 700, color: DECISION_COLORS[checkResult.decision] || 'var(--text-muted)' }}>
              {DECISION_LABELS[checkResult.decision] || checkResult.decision}
            </div>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
              {t('gate.checks_passed', { passed: checkResult.passed, total: checkResult.total })}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-faint)', marginTop: '0.25rem' }}>
              {checkResult.entity_type} · {checkResult.target_env}
            </div>
          </div>

          {/* Individual checks */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {(checkResult.checks || []).map((c: any, i: number) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem',
                background: c.passed ? 'var(--success-bg)' : c.passed === false ? 'var(--danger-bg)' : 'var(--surface-2)',
                borderRadius: '8px',
              }}>
                <span style={{ fontSize: '1.1rem' }}>{c.passed ? '✅' : '❌'}</span>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: '0.85rem' }}>{c.name}</strong>
                  <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{c.details}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* History */}
      {showHistory && (
        <div>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
            {t('gate.history_title')} ({historyItems.length})
          </h4>
          {historyItems.length === 0 ? (
            <div className="rec-empty"><div className="rec-empty-icon">📋</div><h4>{t('gate.empty_history')}</h4></div>
          ) : (
            <div className="rec-list">
              {historyItems.map((item: any, i: number) => (
                <div key={item.id || i} className="rec-card">
                  <div className="rec-card-left">
                    <div className="rec-severity-bar" style={{ backgroundColor: DECISION_COLORS[item.decision] || 'var(--text-faint)' }} />
                  </div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-status-badge" style={{
                        background: (DECISION_COLORS[item.decision] || '#94a3b8') + '20',
                        color: DECISION_COLORS[item.decision] || 'var(--text-muted)',
                      }}>
                        {DECISION_LABELS[item.decision] || item.decision}
                      </span>
                      <span className="rec-category-badge">{item.entity_type}</span>
                    </div>
                    <h4 className="rec-title">{item.entity_id}</h4>
                    <div className="rec-meta">
                      <span className="rec-date">{item.target_env}</span>
                      <span className="rec-date">{t('gate.checks_passed', { passed: item.passed_checks, total: item.total_checks })}</span>
                      {item.checked_at && (
                        <span className="rec-date">{new Date(item.checked_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {!checkResult && !showHistory && (
        <div className="rec-empty">
          <div className="rec-empty-icon">🚧</div>
          <h4>{t('gate.empty_title')}</h4>
          <p>{t('gate.empty_desc')}</p>
        </div>
      )}
    </div>
  )
}
