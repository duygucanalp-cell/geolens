import { useEffect, useState } from 'react'
import { listPolicyPacks, listPolicyControls, updatePolicyControl } from '../api/client'
import type { PolicyPack, PolicyControl } from '../types'

interface Props { workspaceId: string }

const FRAMEWORK_LABELS: Record<string, string> = { eu_ai_act: 'EU AI Act', nist_ai_rmf: 'NIST AI RMF', kvkk: 'KVKK', iso_42001: 'ISO 42001', custom: 'Özel' }
const STATUS_LABELS: Record<string, string> = { pending: 'Bekliyor', passed: 'Geçti', failed: 'Başarısız', not_applicable: 'Uygun Değil' }
const STATUS_COLORS: Record<string, string> = { pending: '#eab308', passed: '#22c55e', failed: '#ef4444', not_applicable: '#94a3b8' }

export function PolicyPacksPanel({ workspaceId: _ws }: Props) {
  const [packs, setPacks] = useState<PolicyPack[]>([])
  const [controls, setControls] = useState<PolicyControl[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedPack, setSelectedPack] = useState<PolicyPack | null>(null)

  useEffect(() => { loadPacks() }, [])
  async function loadPacks() { try { setLoading(true); setError(null); const d = await listPolicyPacks(); setPacks(d.packs) } catch (e) { setError(e instanceof Error ? e.message : 'Yüklenemedi') } finally { setLoading(false) } }

  async function handleSelectPack(pack: PolicyPack) {
    setSelectedPack(pack)
    try { const d = await listPolicyControls(pack.id); setControls(d.controls) } catch (e) { setError(e instanceof Error ? e.message : 'Kontroller yüklenemedi') }
  }

  async function handleUpdateStatus(controlId: string, status: string) {
    try { await updatePolicyControl(controlId, { status }); if (selectedPack) { const d = await listPolicyControls(selectedPack.id); setControls(d.controls) } }
    catch (e) { setError(e instanceof Error ? e.message : 'Güncellenemedi') }
  }

  if (loading) return <div className="dashboard-loading">Policy paketleri yükleniyor...</div>

  return (
    <div className="rec-panel">
      <div className="rec-header"><h3>📜 Policy Packs</h3><p className="rec-desc">Uyumluluk çerçeveleri ve kontrol listeleri.</p></div>
      {error && <div className="audit-error">{error}</div>}

      {selectedPack ? (
        <div>
          <button className="link-btn" onClick={() => { setSelectedPack(null); setControls([]) }} style={{ marginBottom: '1rem' }}>← Paket Listesi</button>
          <div style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
            <h4 style={{ fontWeight: 600 }}>{selectedPack.name}</h4>
            <p style={{ color: '#64748b', fontSize: '0.85rem' }}>{selectedPack.description} · v{selectedPack.version}</p>
          </div>
          {controls.length === 0 ? (
            <div className="rec-empty"><h4>Henüz kontrol yok</h4></div>
          ) : (
            <div className="rec-list">
              {controls.map(c => (
                <div key={c.id} className="rec-card">
                  <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: STATUS_COLORS[c.status] }} /></div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-category-badge">{c.control_id}</span>
                      <span className="rec-category-badge">{c.category}</span>
                      <span className="rec-status-badge" style={{ background: STATUS_COLORS[c.status] + '20', color: STATUS_COLORS[c.status] }}>{STATUS_LABELS[c.status]}</span>
                    </div>
                    <h4 className="rec-title">{c.title}</h4>
                    <p className="rec-detail">{c.description}</p>
                  </div>
                  <div className="rec-card-actions" style={{ flexDirection: 'row', gap: '0.25rem' }}>
                    {['passed', 'failed', 'not_applicable'].map(s => (
                      <button key={s} className={s === 'passed' ? 'rec-apply-btn' : 'rec-dismiss-btn'} style={{ width: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.7rem' }} onClick={() => handleUpdateStatus(c.id, s)}>{s === 'passed' ? '✓' : s === 'failed' ? '✗' : '—'}</button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <>
          {packs.length === 0 ? (
            <div className="rec-empty"><div className="rec-empty-icon">📜</div><h4>Henüz policy paketi yok</h4></div>
          ) : (
            <div className="rec-list">
              {packs.map(p => (
                <div key={p.id} className="rec-card" style={{ cursor: 'pointer' }} onClick={() => handleSelectPack(p)}>
                  <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: p.enabled ? '#6366f1' : '#94a3b8' }} /></div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-category-badge" style={{ fontWeight: 600 }}>{FRAMEWORK_LABELS[p.framework] || p.framework}</span>
                      <span className="rec-status-badge" style={{ background: p.enabled ? '#dcfce7' : '#f1f5f9', color: p.enabled ? '#22c55e' : '#94a3b8' }}>{p.enabled ? 'Aktif' : 'Pasif'}</span>
                    </div>
                    <h4 className="rec-title">{p.name}</h4>
                    <p className="rec-detail">{p.description}</p>
                    <div className="rec-meta">
                      <span className="rec-date">v{p.version}</span>
                      {p.applied_at && <span className="rec-date">{new Date(p.applied_at).toLocaleDateString('tr-TR')}</span>}
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
