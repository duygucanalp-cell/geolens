import { useTranslation } from 'react-i18next'
import { useEffect, useMemo, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
import { Highlight } from './Highlight'
import { listRegistryEntities, createRegistryEntity, deleteRegistryEntity } from '../api/client'
import { normalizeSearch } from '../utils/search'
import type { RegistryEntity } from '../types'

interface Props {
  workspaceId: string
  // Birleşik sayfada ortak arama + yenileme dışarıdan gelir
  embedded?: boolean
  searchQuery?: string
  refreshTick?: number
}

const TYPE_COLORS: Record<string, string> = { model: '#6366f1', agent: '#22c55e', application: '#f59e0b', dataset: '#ef4444' }

export function RegistryPanel({ workspaceId: _ws, embedded, searchQuery = '', refreshTick = 0 }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const [entities, setEntities] = useState<RegistryEntity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newEntity, setNewEntity] = useState({ entity_type: 'model', name: '', description: '', version: '1.0.0', provider: '', risk_class: 'medium' })

  useEffect(() => { loadEntities() }, [refreshTick])
  async function loadEntities() { try { setLoading(true); setError(null); const d = await listRegistryEntities(); setEntities(d.entities) } catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) } finally { setLoading(false) } }

  // Ortak arama: ad / açıklama / sağlayıcı / türe göre istemci tarafında filtrele
  const filteredEntities = useMemo(() => {
    const q = normalizeSearch(searchQuery.trim())
    if (!q) return entities
    return entities.filter(e =>
      normalizeSearch(e.name).includes(q) ||
      normalizeSearch(e.description || '').includes(q) ||
      normalizeSearch(e.provider || '').includes(q) ||
      normalizeSearch(e.entity_type).includes(q)
    )
  }, [entities, searchQuery])

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try { await createRegistryEntity(newEntity); setShowCreate(false); setNewEntity({ entity_type: 'model', name: '', description: '', version: '1.0.0', provider: '', risk_class: 'medium' }); loadEntities() }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.create_error')) }
  }

  async function handleDelete(id: string) { try { await deleteRegistryEntity(id); loadEntities() } catch (e) { setError(e instanceof Error ? e.message : t('registry.delete_error')) } }

  if (loading) return <PanelSkeleton message={t('registry.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header"><h3>{t('registry.title')}</h3><p className="rec-desc">{t('registry.desc')}</p></div>
      {error && <div className="audit-error">{error}</div>}
      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>{showCreate ? t('guardrails.cancel') : t('registry.add')}</button>
        {!embedded && <button className="refresh-btn" onClick={loadEntities}>{t('registry.refresh')}</button>}
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <select value={newEntity.entity_type} onChange={e => setNewEntity({ ...newEntity, entity_type: e.target.value })} className="filter-select">
                <option value="model">{t('registry.type_model')}</option><option value="agent">{t('registry.type_agent')}</option><option value="application">{t('registry.type_application')}</option><option value="dataset">{t('registry.type_dataset')}</option>
              </select>
              <select value={newEntity.risk_class} onChange={e => setNewEntity({ ...newEntity, risk_class: e.target.value })} className="filter-select">
                <option value="low">{t('registry.risk_low')}</option><option value="medium">{t('registry.risk_medium')}</option><option value="high">{t('registry.risk_high')}</option><option value="critical">{t('registry.risk_critical')}</option>
              </select>
            </div>
            <input className="notif-input" placeholder={t('registry.name_placeholder')} value={newEntity.name} onChange={e => setNewEntity({ ...newEntity, name: e.target.value })} required />
            <input className="notif-input" placeholder={t('registry.desc_placeholder')} value={newEntity.description} onChange={e => setNewEntity({ ...newEntity, description: e.target.value })} />
            <input className="notif-input" placeholder={t('registry.provider_placeholder')} value={newEntity.provider} onChange={e => setNewEntity({ ...newEntity, provider: e.target.value })} />
            <button type="submit" className="audit-btn">{t('registry.create')}</button>
          </div>
        </form>
      )}

      {filteredEntities.length === 0 ? (
        searchQuery ? (
          <div className="rec-empty"><div className="rec-empty-icon">🔍</div><h4>{t('merged.no_results')}</h4></div>
        ) : (
          <div className="rec-empty"><div className="rec-empty-icon">📋</div><h4>{t('registry.empty_title')}</h4><p>{t('registry.empty_desc')}</p></div>
        )
      ) : (
        <div className="rec-list">
          {filteredEntities.map(e => (
            <div key={e.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: TYPE_COLORS[e.entity_type] || 'var(--text-faint)' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge" style={{ background: TYPE_COLORS[e.entity_type] + '20', color: TYPE_COLORS[e.entity_type], fontWeight: 600 }}><Highlight text={e.entity_type} query={searchQuery} /></span>
                  <span className="rec-severity-badge" style={{ color: e.risk_class === 'high' || e.risk_class === 'critical' ? '#ef4444' : '#22c55e', borderColor: e.risk_class === 'high' || e.risk_class === 'critical' ? '#ef4444' : '#22c55e' }}>{e.risk_class}</span>
                  <span className="rec-category-badge">{e.lifecycle_state}</span>
                </div>
                <h4 className="rec-title"><Highlight text={e.name} query={searchQuery} />{e.version ? ` v${e.version}` : ''}</h4>
                <p className="rec-detail"><Highlight text={e.description || '-'} query={searchQuery} /></p>
                <div className="rec-meta">
                  <span className="rec-date">{e.provider ? `${e.provider} · ` : ''}{e.owner ? `${e.owner} · ` : ''}{new Date(e.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                </div>
              </div>
              <div className="rec-card-actions">
                <button className="rec-dismiss-btn" onClick={() => handleDelete(e.id)} title={t('registry.delete')}>✕</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
