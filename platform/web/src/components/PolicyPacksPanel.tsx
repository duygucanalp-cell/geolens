import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listPolicyPacks, listPolicyControls, updatePolicyControl } from '../api/client'
import type { PolicyPack, PolicyControl } from '../types'

interface Props { workspaceId: string }

const STATUS_COLORS: Record<string, string> = { pending: '#eab308', passed: '#22c55e', failed: '#ef4444', not_applicable: '#94a3b8' }

export function PolicyPacksPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const FRAMEWORK_LABELS: Record<string, string> = { eu_ai_act: t('policy.framework_eu_ai_act'), nist_ai_rmf: t('policy.framework_nist_ai_rmf'), kvkk: t('policy.framework_kvkk'), iso_42001: t('policy.framework_iso_42001'), custom: t('policy.framework_custom') }
  const STATUS_LABELS: Record<string, string> = { pending: t('policy.status_pending'), passed: t('policy.status_passed'), failed: t('policy.status_failed'), not_applicable: t('policy.status_not_applicable') }
  const [packs, setPacks] = useState<PolicyPack[]>([])
  const [controls, setControls] = useState<PolicyControl[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedPack, setSelectedPack] = useState<PolicyPack | null>(null)

  useEffect(() => { loadPacks() }, [])
  async function loadPacks() { try { setLoading(true); setError(null); const d = await listPolicyPacks(); setPacks(d.packs) } catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) } finally { setLoading(false) } }

  async function handleSelectPack(pack: PolicyPack) {
    setSelectedPack(pack)
    try { const d = await listPolicyControls(pack.id); setControls(d.controls) } catch (e) { setError(e instanceof Error ? e.message : t('policy.controls_error')) }
  }

  async function handleUpdateStatus(controlId: string, status: string) {
    try { await updatePolicyControl(controlId, { status }); if (selectedPack) { const d = await listPolicyControls(selectedPack.id); setControls(d.controls) } }
    catch (e) { setError(e instanceof Error ? e.message : t('policy.update_error')) }
  }

  if (loading) return <div className="dashboard-loading">{t('policy.loading')}</div>

  return (
    <div className="rec-panel">
      <div className="rec-header"><h3>{t('policy.title')}</h3><p className="rec-desc">{t('policy.desc')}</p></div>
      {error && <div className="audit-error">{error}</div>}

      {selectedPack ? (
        <div>
          <button className="link-btn" onClick={() => { setSelectedPack(null); setControls([]) }} style={{ marginBottom: '1rem' }}>{t('policy.back')}</button>
          <div style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
            <h4 style={{ fontWeight: 600 }}>{selectedPack.name}</h4>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>{selectedPack.description} · v{selectedPack.version}</p>
          </div>
          {controls.length === 0 ? (
            <div className="rec-empty"><h4>{t('policy.empty_controls')}</h4></div>
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
            <div className="rec-empty"><div className="rec-empty-icon">📜</div><h4>{t('policy.empty_title')}</h4></div>
          ) : (
            <div className="rec-list">
              {packs.map(p => (
                <div key={p.id} className="rec-card" style={{ cursor: 'pointer' }} onClick={() => handleSelectPack(p)}>
                  <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: p.enabled ? 'var(--accent)' : 'var(--text-faint)' }} /></div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-category-badge" style={{ fontWeight: 600 }}>{FRAMEWORK_LABELS[p.framework] || p.framework}</span>
                      <span className="rec-status-badge" style={{ background: p.enabled ? 'var(--success-soft)' : 'var(--surface-hover)', color: p.enabled ? '#22c55e' : 'var(--text-faint)' }}>{p.enabled ? t('guardrails.enabled') : t('guardrails.disabled')}</span>
                    </div>
                    <h4 className="rec-title">{p.name}</h4>
                    <p className="rec-detail">{p.description}</p>
                    <div className="rec-meta">
                      <span className="rec-date">v{p.version}</span>
                      {p.applied_at && <span className="rec-date">{new Date(p.applied_at).toLocaleDateString(dateLocale)}</span>}
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
