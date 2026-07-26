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
      setError(err instanceof Error ? err.message : 'İncident verileri yüklenemedi')
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
      setError(err instanceof Error ? err.message : 'İncident oluşturulamadı')
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

  if (loading) return <div className="dashboard-loading">İncident verileri yükleniyor...</div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>🚨 Incident Yönetimi</h3>
        <p className="rec-desc">AI güvenlik, performans ve uyumluluk olaylarının takibi.</p>
      </div>

      {error && <div className="audit-error">{error}</div>}

      {/* Summary Cards */}
      {data && (
        <div className="rec-summary">
          <div className="rec-summary-card" style={{ background: '#fef2f2' }}>
            <span className="rec-summary-value" style={{ color: '#ef4444' }}>{data.open_count}</span>
            <span className="rec-summary-label">Açık Incident</span>
          </div>
          <div className="rec-summary-card" style={{ background: '#fff7ed' }}>
            <span className="rec-summary-value" style={{ color: '#f97316' }}>{data.critical_count}</span>
            <span className="rec-summary-label">Kritik/Yüksek</span>
          </div>
          <div className="rec-summary-card" style={{ background: '#eef2ff' }}>
            <span className="rec-summary-value" style={{ color: '#6366f1' }}>{data.count}</span>
            <span className="rec-summary-label">Toplam</span>
          </div>
        </div>
      )}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>
          {showCreate ? t('guardrails.cancel') : 'Yeni Incident'}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: '#f8fafc', padding: '1.25rem', borderRadius: '10px', marginBottom: '1.5rem' }}>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '1rem' }}>Yeni Incident Oluştur</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <input className="notif-input" placeholder={t('incident.create_form_title')} value={newTitle} onChange={(e) => setNewTitle(e.target.value)} required />
            <textarea className="notif-input" placeholder={t('incident.create_form_desc')} value={newDesc} onChange={(e) => setNewDesc(e.target.value)} rows={3} />
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <select value={newSeverity} onChange={(e) => setNewSeverity(e.target.value)} className="filter-select">
                <option value="critical">Kritik</option>
                <option value="high">Yüksek</option>
                <option value="medium">Orta</option>
                <option value="low">Düşük</option>
                <option value="info">Bilgi</option>
              </select>
              <select value={newCategory} onChange={(e) => setNewCategory(e.target.value)} className="filter-select">
                <option value="outage">Kesinti</option>
                <option value="degradation">Performans Düşüşü</option>
                <option value="bias">Bias/Adillik</option>
                <option value="injection">Prompt Injection</option>
                <option value="data_leak">Veri Sızıntısı</option>
                <option value="policy_violation">Politika İhlali</option>
                <option value="other">Diğer</option>
              </select>
            </div>
            <button type="submit" className="audit-btn">Oluştur</button>
          </div>
        </form>
      )}

      {data && data.incidents.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🚨</div>
          <h4>Hiç incident yok</h4>
          <p>Tüm sistemler çalışıyor gibi görünüyor.</p>
        </div>
      ) : (
        <div className="rec-list">
          {data?.incidents.map((inc) => (
            <div key={inc.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: SEVERITY_COLORS[inc.severity] || '#94a3b8' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-severity-badge" style={{
                    color: SEVERITY_COLORS[inc.severity] || '#94a3b8',
                    borderColor: SEVERITY_COLORS[inc.severity] || '#94a3b8',
                  }}>
                    {t(SEVERITY_LABELS[inc.severity]) || inc.severity}
                  </span>
                  <span className="rec-category-badge">{inc.category}</span>
                  <span className="rec-status-badge" style={{
                    background: STATUS_COLORS[inc.status] + '15' || '#f8fafc',
                    color: STATUS_COLORS[inc.status] || '#64748b',
                  }}>
                    {STATUS_LABELS[inc.status] || inc.status}
                  </span>
                  {inc.source && <span className="rec-category-badge">Kaynak: {inc.source}</span>}
                </div>
                <h4 className="rec-title">{inc.title}</h4>
                {inc.assigned_to && <p className="rec-detail">Atanan: {inc.assigned_to}</p>}
                <div className="rec-meta">
                  <span className="rec-date">
                    {new Date(inc.occurred_at).toLocaleDateString(dateLocale, {
                      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
                    })}
                  </span>
                  {inc.severity_score > 0 && (
                    <span className="rec-confidence-label">Skor: {inc.severity_score.toFixed(1)}</span>
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
