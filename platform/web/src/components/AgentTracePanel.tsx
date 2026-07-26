import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listTraces, getTrace, startTrace } from '../api/client'
import type { Trace, TraceDetail } from '../types'

interface Props { workspaceId: string }

const STATUS_COLORS: Record<string, string> = { running: '#6366f1', completed: '#22c55e', failed: '#ef4444', cancelled: '#94a3b8' }

export function AgentTracePanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const STATUS_LABELS: Record<string, string> = { running: t('agenttrace.status_running'), completed: t('agenttrace.status_completed'), failed: t('agenttrace.status_failed'), cancelled: t('guardrails.cancel') }
  const [traces, setTraces] = useState<Trace[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedTrace, setSelectedTrace] = useState<TraceDetail | null>(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [showStart, setShowStart] = useState(false)
  const [agentName, setAgentName] = useState('')
  const [workflowName, setWorkflowName] = useState('')

  useEffect(() => { loadTraces() }, [statusFilter])

  async function loadTraces() {
    try { setLoading(true); setError(null); const d = await listTraces(statusFilter || undefined); setTraces(d.traces); setTotal(d.total) }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleSelect(traceId: string) {
    try { setSelectedTrace(await getTrace(traceId)) }
    catch (e) { setError(e instanceof Error ? e.message : t('agenttrace.load_error')) }
  }

  async function handleStart(e: React.FormEvent) {
    e.preventDefault(); if (!agentName.trim()) return
    try { await startTrace(agentName, workflowName); setShowStart(false); setAgentName(''); setWorkflowName(''); loadTraces() }
    catch (e) { setError(e instanceof Error ? e.message : t('agenttrace.start_error')) }
  }

  if (loading) return <div className="dashboard-loading">Trace'ler yükleniyor...</div>

  return (
    <div className="rec-panel">
      <div className="rec-header"><h3>🔍 Agent Tracing</h3><p className="rec-desc">Multi-step agent iş akışı takibi.</p></div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value); setSelectedTrace(null) }} className="filter-select">
          <option value="">Tüm Durumlar</option>
          <option value="running">Çalışıyor</option>
          <option value="completed">Tamamlandı</option>
          <option value="failed">Başarısız</option>
        </select>
        <button className="refresh-btn" onClick={() => setShowStart(!showStart)}>{showStart ? 'İptal' : 'Yeni Trace'}</button>
      </div>

      {showStart && (
        <form onSubmit={handleStart} style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <input className="notif-input" placeholder="Agent adı" value={agentName} onChange={e => setAgentName(e.target.value)} required />
            <input className="notif-input" placeholder="Workflow adı (opsiyonel)" value={workflowName} onChange={e => setWorkflowName(e.target.value)} />
            <button type="submit" className="audit-btn">Başlat</button>
          </div>
        </form>
      )}

      {selectedTrace ? (
        <div>
          <button className="link-btn" onClick={() => setSelectedTrace(null)} style={{ marginBottom: '1rem' }}>← Listeye Dön</button>
          <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
              <div><strong>Agent:</strong> {selectedTrace.agent_name}</div>
              <div><strong>Workflow:</strong> {selectedTrace.workflow_name || '-'}</div>
              <div><strong>Durum:</strong> <span style={{ color: STATUS_COLORS[selectedTrace.status], fontWeight: 600 }}>{STATUS_LABELS[selectedTrace.status]}</span></div>
              <div><strong>Adımlar:</strong> {selectedTrace.completed_steps}/{selectedTrace.total_steps}</div>
              <div><strong>Süre:</strong> {selectedTrace.total_duration_ms}ms</div>
            </div>
          </div>
          {selectedTrace.steps.map(s => (
            <div key={s.step_id} className="rec-card" style={{ marginBottom: '0.5rem' }}>
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: STATUS_COLORS[s.status] }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{s.agent}</span>
                  <span className="rec-status-badge" style={{ background: STATUS_COLORS[s.status] + '20', color: STATUS_COLORS[s.status] }}>{STATUS_LABELS[s.status] || s.status}</span>
                </div>
                <h4 className="rec-title">{s.step_name}</h4>
                <p className="rec-detail"><strong>Input:</strong> {s.input}<br /><strong>Output:</strong> {s.output}</p>
                <div className="rec-meta"><span className="rec-date">{s.duration_ms}ms</span></div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <>
          <div style={{ marginBottom: '0.5rem', color: '#64748b', fontSize: '0.85rem' }}>Toplam: {total} trace</div>
          {traces.length === 0 ? (
            <div className="rec-empty"><div className="rec-empty-icon">🔍</div><h4>Henüz trace yok</h4></div>
          ) : (
            <div className="rec-list">
              {traces.map(t => (
                <div key={t.trace_id} className="rec-card" style={{ cursor: 'pointer' }} onClick={() => handleSelect(t.trace_id)}>
                  <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: STATUS_COLORS[t.status] }} /></div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-category-badge">{t.agent_name}</span>
                      <span className="rec-status-badge" style={{ background: STATUS_COLORS[t.status] + '20', color: STATUS_COLORS[t.status] }}>{STATUS_LABELS[t.status]}</span>
                    </div>
                    <h4 className="rec-title">{t.workflow_name || t.agent_name}</h4>
                    <div className="rec-meta">
                      <span className="rec-date">{t.completed_steps}/{t.total_steps} adım</span>
                      <span className="rec-date">{t.total_duration_ms}ms</span>
                      <span className="rec-date">{new Date(t.started_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
