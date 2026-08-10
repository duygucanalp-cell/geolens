import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listAlertRules, createAlertRule, updateAlertRule, deleteAlertRule } from '../api/client'
import type { AlertRule } from '../types'

const METRICS = ['visibility_score', 'response_time', 'error_rate', 'uptime', 'coverage']
const CONDITIONS = ['gt', 'lt', 'gte', 'lte', 'eq']
const CHANNELS = ['email', 'slack', 'webhook', 'sms']

export function AlertRulesPanel({ workspaceId, brands }: { workspaceId: string; brands: { id: string; name: string }[] }) {
  const { t } = useTranslation()
  const [rules, setRules] = useState<AlertRule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newBrandId, setNewBrandId] = useState('')
  const [newMetric, setNewMetric] = useState('visibility_score')
  const [newCondition, setNewCondition] = useState('lt')
  const [newThreshold, setNewThreshold] = useState('')
  const [newChannel, setNewChannel] = useState('email')

  useEffect(() => { loadRules() }, [workspaceId])

  async function loadRules() {
    try {
      setLoading(true)
      setError(null)
      const data = await listAlertRules(workspaceId)
      setRules(data.rules)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('alertrules.error_load'))
    } finally {
      setLoading(false)
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try {
      await createAlertRule(workspaceId, {
        brand_id: newBrandId,
        name: newName,
        metric: newMetric,
        condition: newCondition,
        threshold: Number(newThreshold),
        channel: newChannel,
      })
      setShowCreate(false)
      setNewName('')
      setNewBrandId('')
      setNewMetric('visibility_score')
      setNewCondition('lt')
      setNewThreshold('')
      setNewChannel('email')
      loadRules()
    } catch (e) {
      setError(e instanceof Error ? e.message : t('alertrules.error_create'))
    }
  }

  async function handleToggle(id: string, enabled: boolean) {
    try {
      await updateAlertRule(workspaceId, id, { enabled: !enabled })
      loadRules()
    } catch (e) {
      setError(e instanceof Error ? e.message : t('alertrules.error_toggle'))
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteAlertRule(workspaceId, id)
      loadRules()
    } catch (e) {
      setError(e instanceof Error ? e.message : t('alertrules.error_delete'))
    }
  }

  const brandMap = new Map(brands.map(b => [b.id, b.name]))

  if (loading) return <PanelSkeleton message={t('alertrules.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('alertrules.title')}</h3>
        <p className="rec-desc">{t('alertrules.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>
          {showCreate ? t('alertrules.cancel') : t('alertrules.add')}
        </button>
        <button className="refresh-btn" onClick={loadRules}>{t('alertrules.refresh')}</button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <input className="notif-input" placeholder={t('alertrules.name')} value={newName} onChange={e => setNewName(e.target.value)} required />
            <select value={newBrandId} onChange={e => setNewBrandId(e.target.value)} className="filter-select" required>
              <option value="">{t('alertrules.select_brand')}</option>
              {brands.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
            <select value={newMetric} onChange={e => setNewMetric(e.target.value)} className="filter-select">
              {METRICS.map(m => <option key={m} value={m}>{t(`alertrules.metric_${m}`)}</option>)}
            </select>
            <select value={newCondition} onChange={e => setNewCondition(e.target.value)} className="filter-select">
              {CONDITIONS.map(c => <option key={c} value={c}>{t(`alertrules.condition_${c}`)}</option>)}
            </select>
            <input className="notif-input" type="number" step="0.01" placeholder={t('alertrules.threshold')} value={newThreshold} onChange={e => setNewThreshold(e.target.value)} required />
            <select value={newChannel} onChange={e => setNewChannel(e.target.value)} className="filter-select">
              {CHANNELS.map(c => <option key={c} value={c}>{t(`alertrules.channel_${c}`)}</option>)}
            </select>
            <button type="submit" className="audit-btn">{t('alertrules.create')}</button>
          </div>
        </form>
      )}

      {rules.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🔔</div>
          <h4>{t('alertrules.empty_title')}</h4>
          <p>{t('alertrules.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {rules.map(r => (
            <div key={r.id} className="rec-card">
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{brandMap.get(r.brand_id) || r.brand_id}</span>
                  <span className="rec-status-badge" style={{ background: r.enabled ? 'var(--success-soft)' : 'var(--danger-bg)', color: r.enabled ? '#22c55e' : '#ef4444' }}>
                    {r.enabled ? t('alertrules.enabled') : t('alertrules.disabled')}
                  </span>
                </div>
                <h4 className="rec-title">{r.name}</h4>
                <p className="rec-detail">
                  {t(`alertrules.metric_${r.metric}`)} {r.condition} {r.threshold} &middot; {t(`alertrules.channel_${r.channel}`)}
                </p>
              </div>
              <div className="rec-card-actions">
                <button className="rec-apply-btn" onClick={() => handleToggle(r.id, r.enabled)} title={r.enabled ? t('alertrules.disable') : t('alertrules.enable')}>
                  {r.enabled ? '⏸' : '▶'}
                </button>
                <button className="rec-dismiss-btn" onClick={() => handleDelete(r.id)} title={t('alertrules.delete')}>✕</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default AlertRulesPanel