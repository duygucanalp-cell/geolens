import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listRegistryEntities, createRegistryEntity, deleteRegistryEntity } from '../api/client'
import type { RegistryEntity } from '../types'

interface Props { workspaceId: string }

const TYPE_COLORS: Record<string, string> = { model: '#6366f1', agent: '#22c55e', application: '#f59e0b', dataset: '#ef4444' }

export function RegistryPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const [entities, setEntities] = useState<RegistryEntity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newEntity, setNewEntity] = useState({ entity_type: 'model', name: '', description: '', version: '1.0.0', provider: '', risk_class: 'medium' })

  useEffect(() => { loadEntities() }, [])
  async function loadEntities() { try { setLoading(true); setError(null); const d = await listRegistryEntities(); setEntities(d.entities) } catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) } finally { setLoading(false) } }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try { await createRegistryEntity(newEntity); setShowCreate(false); setNewEntity({ entity_type: 'model', name: '', description: '', version: '1.0.0', provider: '', risk_class: 'medium' }); loadEntities() }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.create_error')) }
  }

  async function handleDelete(id: string) { try { await deleteRegistryEntity(id); loadEntities() } catch (e) { setError(e instanceof Error ? e.message : t('registry.delete_error')) } }

  if (loading) return <div className="dashboard-loading">Registry yükleniyor...</div>

  return (
    <div className="rec-panel">
      <div className="rec-header"><h3>📋 AI Registry</h3><p className="rec-desc">AI varlık envanteri ve risk sınıflandırması.</p></div>
      {error && <div className="audit-error">{error}</div>}
      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>{showCreate ? t('guardrails.cancel') : t('registry.add')}</button>
        <button className="refresh-btn" onClick={loadEntities}>Yenile</button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <select value={newEntity.entity_type} onChange={e => setNewEntity({ ...newEntity, entity_type: e.target.value })} className="filter-select">
                <option value="model">Model</option><option value="agent">Agent</option><option value="application">Uygulama</option><option value="dataset">Dataset</option>
              </select>
              <select value={newEntity.risk_class} onChange={e => setNewEntity({ ...newEntity, risk_class: e.target.value })} className="filter-select">
                <option value="low">Düşük Risk</option><option value="medium">Orta Risk</option><option value="high">Yüksek Risk</option><option value="critical">Kritik</option>
              </select>
            </div>
            <input className="notif-input" placeholder="Ad" value={newEntity.name} onChange={e => setNewEntity({ ...newEntity, name: e.target.value })} required />
            <input className="notif-input" placeholder="Açıklama" value={newEntity.description} onChange={e => setNewEntity({ ...newEntity, description: e.target.value })} />
            <input className="notif-input" placeholder="Sağlayıcı (opsiyonel)" value={newEntity.provider} onChange={e => setNewEntity({ ...newEntity, provider: e.target.value })} />
            <button type="submit" className="audit-btn">Oluştur</button>
          </div>
        </form>
      )}

      {entities.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">📋</div><h4>Henüz varlık kaydı yok</h4><p>AI varlıklarınızı ekleyerek envanter oluşturun.</p></div>
      ) : (
        <div className="rec-list">
          {entities.map(e => (
            <div key={e.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: TYPE_COLORS[e.entity_type] || '#94a3b8' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge" style={{ background: TYPE_COLORS[e.entity_type] + '20', color: TYPE_COLORS[e.entity_type], fontWeight: 600 }}>{e.entity_type}</span>
                  <span className="rec-severity-badge" style={{ color: e.risk_class === 'high' || e.risk_class === 'critical' ? '#ef4444' : '#22c55e', borderColor: e.risk_class === 'high' || e.risk_class === 'critical' ? '#ef4444' : '#22c55e' }}>{e.risk_class}</span>
                  <span className="rec-category-badge">{e.lifecycle_state}</span>
                </div>
                <h4 className="rec-title">{e.name}{e.version ? ` v${e.version}` : ''}</h4>
                <p className="rec-detail">{e.description || '-'}</p>
                <div className="rec-meta">
                  <span className="rec-date">{e.provider ? `${e.provider} · ` : ''}{e.owner ? `${e.owner} · ` : ''}{new Date(e.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                </div>
              </div>
              <div className="rec-card-actions">
                <button className="rec-dismiss-btn" onClick={() => handleDelete(e.id)} title="Sil">✕</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
