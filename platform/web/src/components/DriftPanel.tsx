import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listDriftEntities, listDriftAlerts, analyzeDrift, recordDriftObservation, listDriftObservations } from '../api/client'
import type { DriftEntitySummary, DriftAlert, DriftAnalysis, DriftObservation } from '../types'

interface Props { workspaceId: string }

const SEVERITY_COLORS: Record<string, string> = { critical: '#ef4444', warning: '#f59e0b', info: '#22c55e', insufficient_data: '#94a3b8' }

export function DriftPanel({ workspaceId: _ws }: Props) {
  const { t } = useTranslation()
  const SEVERITY_LABELS: Record<string, string> = {
    critical: t('drift.sev_critical'), warning: t('drift.sev_warning'), info: t('drift.sev_info'), insufficient_data: t('drift.sev_insufficient'),
  }
  const [entities, setEntities] = useState<DriftEntitySummary[]>([])
  const [alerts, setAlerts] = useState<DriftAlert[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [analysis, setAnalysis] = useState<DriftAnalysis | null>(null)
  const [observations, setObservations] = useState<DriftObservation[]>([])
  const [selected, setSelected] = useState<string>('')
  const [showRecord, setShowRecord] = useState(false)
  const [recEntityId, setRecEntityId] = useState('')
  const [recEntityName, setRecEntityName] = useState('')
  const [recMetric, setRecMetric] = useState('')
  const [recValue, setRecValue] = useState('')

  useEffect(() => { loadAll() }, [])

  async function loadAll() {
    try {
      setLoading(true)
      const [e, a] = await Promise.all([listDriftEntities(), listDriftAlerts()])
      setEntities(e.entities)
      setAlerts(a.alerts)
    } catch (err) { setError(err instanceof Error ? err.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleAnalyze(entityId: string, metric: string) {
    setSelected(entityId)
    setError(null)
    try {
      const [an, ob] = await Promise.all([
        analyzeDrift(entityId, metric),
        listDriftObservations(entityId, metric, 50),
      ])
      setAnalysis(an)
      setObservations(ob.observations)
      loadAll()
    } catch (err) { setError(err instanceof Error ? err.message : t('drift.analyze_error')) }
  }

  async function handleRecord(e: React.FormEvent) {
    e.preventDefault()
    const value = parseFloat(recValue)
    if (!recEntityId.trim() || !recMetric.trim() || isNaN(value)) return
    try {
      await recordDriftObservation({ entity_id: recEntityId, entity_name: recEntityName || recEntityId, metric: recMetric, value })
      setShowRecord(false); setRecEntityId(''); setRecEntityName(''); setRecMetric(''); setRecValue('')
      loadAll()
    } catch (err) { setError(err instanceof Error ? err.message : t('drift.record_error')) }
  }

  if (loading) return <PanelSkeleton message={t('drift.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('drift.title')}</h3>
        <p className="rec-desc">{t('drift.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowRecord(!showRecord)}>{showRecord ? t('guardrails.cancel') : t('drift.record')}</button>
        <button className="refresh-btn" onClick={loadAll}>{t('guardrails.refresh')}</button>
      </div>

      {showRecord && (
        <form onSubmit={handleRecord} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input className="notif-input" style={{ flex: 1 }} placeholder={t('drift.entity_id')} value={recEntityId} onChange={e => setRecEntityId(e.target.value)} required />
              <input className="notif-input" style={{ flex: 1 }} placeholder={t('drift.entity_name')} value={recEntityName} onChange={e => setRecEntityName(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input className="notif-input" style={{ flex: 1 }} placeholder={t('drift.metric')} value={recMetric} onChange={e => setRecMetric(e.target.value)} required />
              <input className="notif-input" style={{ width: 150 }} placeholder={t('drift.value')} type="number" step="any" value={recValue} onChange={e => setRecValue(e.target.value)} required />
            </div>
            <button type="submit" className="audit-btn">{t('drift.save')}</button>
          </div>
        </form>
      )}

      {/* Analysis */}
      {analysis && (
        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{
            textAlign: 'center', padding: '1.5rem', borderRadius: '12px', marginBottom: '1rem',
            background: analysis.severity === 'critical' ? 'var(--danger-bg)' : analysis.severity === 'warning' ? 'var(--amber-bg)' : 'var(--success-bg)',
          }}>
            <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>
              {analysis.severity === 'critical' ? '📉' : analysis.severity === 'warning' ? '⚠️' : '✅'}
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 700, color: SEVERITY_COLORS[analysis.severity] || 'var(--text-muted)' }}>
              {analysis.drift_score} / 100
            </div>
            <div style={{ fontSize: '0.9rem', fontWeight: 600, marginTop: '0.25rem' }}>
              {SEVERITY_LABELS[analysis.severity] || analysis.severity}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
              {analysis.entity_id} · {analysis.metric}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
              {t('drift.reference_mean')}: {analysis.reference_mean.toFixed(2)} → {t('drift.current_mean')}: {analysis.current_mean.toFixed(2)} ({analysis.delta > 0 ? '+' : ''}{analysis.delta.toFixed(2)})
            </div>
          </div>

          {observations.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height: 80, marginBottom: '1rem', overflowX: 'auto' }}>
              {observations.map((o, i) => (
                <div key={o.id || i} title={`${o.value} (${o.window_start})`} style={{
                  width: 8, height: Math.max(4, (o.value / (Math.max(...observations.map(x => x.value)) || 1)) * 76),
                  background: SEVERITY_COLORS[analysis.severity] || '#3b82f6', borderRadius: '2px 2px 0 0', flexShrink: 0,
                }} />
              ))}
            </div>
          )}
        </div>
      )}

      {/* Entities */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
        {t('drift.entities')} ({entities.length})
      </h4>
      {entities.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">📈</div><h4>{t('drift.no_entities')}</h4><p>{t('drift.no_entities_desc')}</p></div>
      ) : (
        <div className="rec-list">
          {entities.map(e => (
            <div key={e.entity_id + e.metric} className="rec-card" style={{ cursor: 'pointer' }} onClick={() => handleAnalyze(e.entity_id, e.metric)}>
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: selected === e.entity_id ? '#3b82f6' : 'var(--text-faint)' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{e.entity_name || e.entity_id}</span>
                  <span className="rec-severity-badge">{e.metric}</span>
                </div>
                <div className="rec-meta">
                  <span className="rec-date">{e.observation_count} {t('drift.observations')}</span>
                  <span className="rec-date">{t('drift.mean')}: {e.mean_value.toFixed(2)}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Alerts */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, margin: '1rem 0 0.75rem', color: 'var(--text-strong)' }}>
        {t('drift.alerts')} ({alerts.length})
      </h4>
      {alerts.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">🔔</div><h4>{t('drift.no_alerts')}</h4></div>
      ) : (
        <div className="rec-list">
          {alerts.map(a => (
            <div key={a.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: SEVERITY_COLORS[a.severity] || 'var(--text-faint)' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-status-badge" style={{
                    background: (SEVERITY_COLORS[a.severity] || '#94a3b8') + '20',
                    color: SEVERITY_COLORS[a.severity] || 'var(--text-muted)',
                  }}>
                    {SEVERITY_LABELS[a.severity] || a.severity}
                  </span>
                  <span className="rec-severity-badge">{a.drift_score} / 100</span>
                </div>
                <h4 className="rec-title">{a.entity_name || a.entity_id} · {a.metric}</h4>
                <div className="rec-meta">
                  <span className="rec-date">{a.reference_mean.toFixed(2)} → {a.current_mean.toFixed(2)} ({a.delta > 0 ? '+' : ''}{a.delta.toFixed(2)})</span>
                  <span className="rec-date">{new Date(a.created_at).toLocaleString()}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
