import { useState } from 'react'
import { runGateCheck, getGateHistory } from '../api/client'
import type { GateCheckResult, GateHistoryEntry } from '../types'

interface Props { workspaceId: string }

const DECISION_COLORS: Record<string, string> = { approved: '#22c55e', flagged: '#f59e0b', blocked: '#ef4444' }
const DECISION_LABELS: Record<string, string> = { approved: '✅ Onaylandı', flagged: '⚠️ İşaretlendi', blocked: '🔴 Engellendi' }
export function GatePanel({ workspaceId: _ws }: Props) {
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
    } catch (err) { setError(err instanceof Error ? err.message : 'Gate check hatası') }
    finally { setLoading(false) }
  }

  async function loadHistory() {
    if (!entityId.trim()) return
    try {
      setHistory(await getGateHistory(entityId))
      setShowHistory(!showHistory)
    } catch (err) { setError(err instanceof Error ? err.message : 'Geçmiş yüklenemedi') }
  }

  const historyItems = history

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>🚧 CI/CD Governance Gate</h3>
        <p className="rec-desc">AI deployment öncesi governance kontrol noktası.</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <form onSubmit={handleCheck} style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input className="notif-input" style={{ flex: 1 }} placeholder="Entity ID veya adı" value={entityId} onChange={e => setEntityId(e.target.value)} required />
            <select value={entityType} onChange={e => setEntityType(e.target.value)} className="filter-select">
              <option value="model">Model</option><option value="agent">Agent</option><option value="application">Uygulama</option>
            </select>
            <select value={targetEnv} onChange={e => setTargetEnv(e.target.value)} className="filter-select">
              <option value="development">Development</option><option value="staging">Staging</option><option value="production">Production</option>
            </select>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button type="submit" className="audit-btn" disabled={loading}>
              {loading ? 'Kontrol Ediliyor...' : 'Gate Check'}
            </button>
            <button type="button" className="refresh-btn" onClick={loadHistory} disabled={!entityId.trim()}>
              {showHistory ? 'Geçmişi Gizle' : 'Geçmiş'}
            </button>
          </div>
        </div>
      </form>

      {/* Check result */}
      {checkResult && (
        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{
            textAlign: 'center', padding: '1.5rem', borderRadius: '12px', marginBottom: '1rem',
            background: checkResult.decision === 'approved' ? '#f0fdf4' : checkResult.decision === 'flagged' ? '#fefce8' : '#fef2f2',
          }}>
            <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>
              {checkResult.decision === 'approved' ? '✅' : checkResult.decision === 'flagged' ? '⚠️' : '🔴'}
            </div>
            <div style={{ fontSize: '1.1rem', fontWeight: 700, color: DECISION_COLORS[checkResult.decision] || '#64748b' }}>
              {DECISION_LABELS[checkResult.decision] || checkResult.decision}
            </div>
            <div style={{ fontSize: '0.85rem', color: '#64748b', marginTop: '0.25rem' }}>
              {checkResult.passed}/{checkResult.total} check geçti
            </div>
            <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.25rem' }}>
              {checkResult.entity_type} · {checkResult.target_env}
            </div>
          </div>

          {/* Individual checks */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {(checkResult.checks || []).map((c: any, i: number) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem',
                background: c.passed ? '#f0fdf4' : c.passed === false ? '#fef2f2' : '#f8fafc',
                borderRadius: '8px',
              }}>
                <span style={{ fontSize: '1.1rem' }}>{c.passed ? '✅' : '❌'}</span>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: '0.85rem' }}>{c.name}</strong>
                  <p style={{ fontSize: '0.78rem', color: '#64748b' }}>{c.details}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* History */}
      {showHistory && (
        <div>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: '#334155' }}>
            Gate Geçmişi ({historyItems.length})
          </h4>
          {historyItems.length === 0 ? (
            <div className="rec-empty"><div className="rec-empty-icon">📋</div><h4>Henüz geçmiş kaydı yok</h4></div>
          ) : (
            <div className="rec-list">
              {historyItems.map((item: any, i: number) => (
                <div key={item.id || i} className="rec-card">
                  <div className="rec-card-left">
                    <div className="rec-severity-bar" style={{ backgroundColor: DECISION_COLORS[item.decision] || '#94a3b8' }} />
                  </div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-status-badge" style={{
                        background: (DECISION_COLORS[item.decision] || '#94a3b8') + '20',
                        color: DECISION_COLORS[item.decision] || '#64748b',
                      }}>
                        {DECISION_LABELS[item.decision] || item.decision}
                      </span>
                      <span className="rec-category-badge">{item.entity_type}</span>
                    </div>
                    <h4 className="rec-title">{item.entity_id}</h4>
                    <div className="rec-meta">
                      <span className="rec-date">{item.target_env}</span>
                      <span className="rec-date">{item.passed_checks}/{item.total_checks} geçti</span>
                      {item.checked_at && (
                        <span className="rec-date">{new Date(item.checked_at).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</span>
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
          <h4>Henüz kontrol yapılmadı</h4>
          <p>AI varlığınızı production'a göndermeden önce governance kontrolünden geçirin.</p>
        </div>
      )}
    </div>
  )
}
