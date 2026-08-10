import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getIncidents, createIncident, updateIncident } from '../api/client'
import type { IncidentListResponse } from '../types'
import { SEVERITY_COLORS, SEVERITY_LABELS } from '../types'

interface Props {
  workspaceId: string
}

const STATUS_COLORS: Record<string, string> = {
  open: '#ef4444',
  investigating: '#f97316',
  mitigated: '#eab308',
  resolved: '#22c55e',
  closed: '#94a3b8',
}

export function IncidentPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const STATUS_LABELS: Record<string, string> = {
    open: t('incident.status_open'),
    investigating: t('incident.status_investigating'),
    mitigated: t('incident.status_mitigated'),
    resolved: t('incident.status_resolved'),
    closed: t('incident.status_closed'),
  }
  const [data, setData] = useState<IncidentListResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)

  // Create form state
  const [newTitle, setNewTitle] = useState('')
  const [newSeverity, setNewSeverity] = useState('medium')
  const [newCategory, setNewCategory] = useState('other')
  const [newDesc, setNewDesc] = useState('')

  useEffect(() => { loadData() }, [])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      setData(await getIncidents())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('incident.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!newTitle.trim()) return
    try {
      await createIncident({
        severity: newSeverity,
        category: newCategory,
        title: newTitle,
        description: newDesc,
      })
      setShowCreate(false)
      setNewTitle('')
      setNewDesc('')
      loadData()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('incident.create_error'))
    }
  }

  async function handleResolve(incidentId: string) {
    try {
      await updateIncident(incidentId, { status: 'resolved' })
      loadData()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('optimization.status_update_error'))
    }
  }

  if (loading) return <div className="dashboard-loading">{t('incident.loading')}</div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('incident.title')}</h3>
        <p className="rec-desc">{t('incident.desc')}</p>
      </div>

      {error && <div className="audit-error">{error}</div>}

      {/* Summary Cards */}
      {data && (
        <div className="rec-summary">
          <div className="rec-summary-card" style={{ background: 'var(--danger-bg)' }}>
            <span className="rec-summary-value" style={{ color: '#ef4444' }}>{data.open_count}</span>
            <span className="rec-summary-label">{t('incident.summary_open')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: '#fff7ed' }}>
            <span className="rec-summary-value" style={{ color: '#f97316' }}>{data.critical_count}</span>
            <span className="rec-summary-label">{t('incident.summary_critical')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: 'var(--accent-soft)' }}>
            <span className="rec-summary-value" style={{ color: 'var(--accent)' }}>{data.count}</span>
            <span className="rec-summary-label">{t('incident.summary_total')}</span>
          </div>
        </div>
      )}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>
          {showCreate ? t('guardrails.cancel') : t('incident.create')}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: 'var(--surface-2)', padding: '1.25rem', borderRadius: '10px', marginBottom: '1.5rem' }}>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '1rem' }}>{t('incident.create_title')}</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <input className="notif-input" placeholder={t('incident.create_form_title')} value={newTitle} onChange={(e) => setNewTitle(e.target.value)} required />
            <textarea className="notif-input" placeholder={t('incident.create_form_desc')} value={newDesc} onChange={(e) => setNewDesc(e.target.value)} rows={3} />
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <select value={newSeverity} onChange={(e) => setNewSeverity(e.target.value)} className="filter-select">
                <option value="critical">{t('incident.severity_critical')}</option>
                <option value="high">{t('incident.severity_high')}</option>
                <option value="medium">{t('incident.severity_medium')}</option>
                <option value="low">{t('incident.severity_low')}</option>
                <option value="info">{t('incident.severity_info')}</option>
              </select>
              <select value={newCategory} onChange={(e) => setNewCategory(e.target.value)} className="filter-select">
                <option value="outage">{t('incident.category_outage')}</option>
                <option value="degradation">{t('incident.category_degradation')}</option>
                <option value="bias">{t('incident.category_bias')}</option>
                <option value="injection">{t('incident.category_injection')}</option>
                <option value="data_leak">{t('incident.category_data_leak')}</option>
                <option value="policy_violation">{t('incident.category_policy_violation')}</option>
                <option value="other">{t('incident.category_other')}</option>
              </select>
            </div>
            <button type="submit" className="audit-btn">{t('common.create')}</button>
          </div>
        </form>
      )}

      {data && data.incidents.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🚨</div>
          <h4>{t('incident.empty_title')}</h4>
          <p>{t('incident.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {data?.incidents.map((inc) => (
            <div key={inc.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: SEVERITY_COLORS[inc.severity] || 'var(--text-faint)' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-severity-badge" style={{
                    color: SEVERITY_COLORS[inc.severity] || 'var(--text-faint)',
                    borderColor: SEVERITY_COLORS[inc.severity] || 'var(--text-faint)',
                  }}>
                    {t(SEVERITY_LABELS[inc.severity]) || inc.severity}
                  </span>
                  <span className="rec-category-badge">{inc.category}</span>
                  <span className="rec-status-badge" style={{
                    background: STATUS_COLORS[inc.status] + '15' || 'var(--surface-2)',
                    color: STATUS_COLORS[inc.status] || 'var(--text-muted)',
                  }}>
                    {STATUS_LABELS[inc.status] || inc.status}
                  </span>
                  {inc.source && <span className="rec-category-badge">{t('incident.source_label', { source: inc.source })}</span>}
                </div>
                <h4 className="rec-title">{inc.title}</h4>
                {inc.assigned_to && <p className="rec-detail">{t('incident.assigned_label', { assignee: inc.assigned_to })}</p>}
                <div className="rec-meta">
                  <span className="rec-date">
                    {new Date(inc.occurred_at).toLocaleDateString(dateLocale, {
                      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
                    })}
                  </span>
                  {inc.severity_score > 0 && (
                    <span className="rec-confidence-label">{t('incident.score_label', { score: inc.severity_score.toFixed(1) })}</span>
                  )}
                </div>
              </div>
              {(inc.status === 'open' || inc.status === 'investigating') && (
                <div className="rec-card-actions">
                  <button className="rec-apply-btn" onClick={() => handleResolve(inc.id)} title={t('incident.mark_resolved')}>✓</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
