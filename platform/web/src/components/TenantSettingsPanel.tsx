import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
import { getTenant, listMembers, inviteMember, listApiKeys, createApiKey, deleteApiKey, getSubscription } from '../api/client'
import type { TenantMember, ApiKey } from '../types'

type Tab = 'members' | 'apikeys' | 'subscription'

export function TenantSettingsPanel() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<Tab>('members')

  const [tenant, setTenant] = useState<{ id: string; name: string; slug: string; tier: string; created_at: string } | null>(null)
  const [tenantLoading, setTenantLoading] = useState(true)

  const [members, setMembers] = useState<TenantMember[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('viewer')
  const [inviteWsId, setInviteWsId] = useState('')
  const [inviting, setInviting] = useState(false)
  const [inviteResult, setInviteResult] = useState<string | null>(null)

  const [apiKeys, setApiKeys] = useState<ApiKey[]>([])
  const [keysLoading, setKeysLoading] = useState(false)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newKeyName, setNewKeyName] = useState('')
  const [newKeyRole, setNewKeyRole] = useState('admin')
  const [createdKey, setCreatedKey] = useState<{ id: string; api_key: string; key_prefix: string; warning: string } | null>(null)
  const [creatingKey, setCreatingKey] = useState(false)

  const [subscription, setSubscription] = useState<{ tenant_id: string; tier: string; updated_at: string } | null>(null)
  const [subLoading, setSubLoading] = useState(false)

  const [error, setError] = useState<string | null>(null)

  useEffect(() => { loadTenant() }, [])

  async function loadTenant() {
    try {
      setTenantLoading(true)
      setError(null)
      setTenant(await getTenant())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('tenant.load_error'))
    } finally {
      setTenantLoading(false)
    }
  }

  async function loadMembers() {
    try {
      setMembersLoading(true)
      setError(null)
      const data = await listMembers()
      setMembers(data.members)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('tenant.load_error'))
    } finally {
      setMembersLoading(false)
    }
  }

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault()
    if (!inviteEmail.trim() || !inviteWsId.trim()) return
    try {
      setInviting(true)
      setInviteResult(null)
      setError(null)
      const result = await inviteMember(inviteEmail.trim(), inviteWsId.trim(), inviteRole)
      setInviteResult(`✅ ${result.email} adresine davet gönderildi.`)
      setInviteEmail('')
      loadMembers()
    } catch (err) {
      setInviteResult(`❌ ${err instanceof Error ? err.message : t('tenant.invite_error')}`)
    } finally {
      setInviting(false)
    }
  }

  async function loadApiKeys() {
    try {
      setKeysLoading(true)
      setError(null)
      const data = await listApiKeys()
      setApiKeys(data.keys)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('tenant.load_error'))
    } finally {
      setKeysLoading(false)
    }
  }

  async function handleCreateKey(e: React.FormEvent) {
    e.preventDefault()
    if (!newKeyName.trim()) return
    try {
      setCreatingKey(true)
      setError(null)
      const result = await createApiKey({ name: newKeyName.trim(), role: newKeyRole })
      setCreatedKey(result)
      setNewKeyName('')
      setShowCreateForm(false)
      loadApiKeys()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('tenant.key_create_error'))
    } finally {
      setCreatingKey(false)
    }
  }

  async function handleDeleteKey(keyId: string) {
    if (!window.confirm(t('tenant.key_delete_confirm'))) return
    try {
      setError(null)
      await deleteApiKey(keyId)
      setApiKeys(prev => prev.filter(k => k.id !== keyId))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('tenant.key_delete_error'))
    }
  }

  async function loadSubscription() {
    try {
      setSubLoading(true)
      setError(null)
      setSubscription(await getSubscription())
    } catch (err) {
      setError(err instanceof Error ? err.message : t('tenant.load_error'))
    } finally {
      setSubLoading(false)
    }
  }

  useEffect(() => {
    switch (activeTab) {
      case 'members': loadMembers(); break
      case 'apikeys': loadApiKeys(); break
      case 'subscription': loadSubscription(); break
    }
  }, [activeTab])

  if (tenantLoading) return <PanelSkeleton message={t('tenant.loading')} />
  if (error && !tenant) return <div className="dashboard-error"><p>{error}</p><button onClick={loadTenant}>{t('common.retry')}</button></div>

  const TIER_LABELS: Record<string, string> = {
    free: t('tenant.tier_free'),
    starter: t('tenant.tier_starter'),
    professional: t('tenant.tier_professional'),
    enterprise: t('tenant.tier_enterprise'),
  }

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('tenant.title')}</h3>
        <p className="rec-desc">{t('tenant.desc')}</p>
      </div>

      {error && <div className="audit-error">{error}</div>}

      {tenant && (
        <div style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <div>
              <strong style={{ color: 'var(--text-strong)' }}>{tenant.name}</strong>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>{tenant.slug}</p>
            </div>
            <span className="rec-category-badge">{TIER_LABELS[tenant.tier] || tenant.tier}</span>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>
              {t('tenant.created')}: {new Date(tenant.created_at).toLocaleDateString()}
            </div>
          </div>
        </div>
      )}

      <div className="dashboard-filters" style={{ borderBottom: '2px solid var(--border)', padding: 0, gap: 0, marginBottom: '1rem' }}>
        {(['members', 'apikeys', 'subscription'] as Tab[]).map(tab => (
          <button
            key={tab}
            onClick={() => { setActiveTab(tab); setError(null) }}
            style={{
              padding: '0.6rem 1.2rem',
              border: 'none',
              background: activeTab === tab ? 'var(--accent)' : 'transparent',
              color: activeTab === tab ? '#fff' : 'var(--text-muted)',
              fontWeight: activeTab === tab ? 600 : 400,
              cursor: 'pointer',
              borderRadius: '8px 8px 0 0',
              fontSize: '0.85rem',
            }}
          >
            {tab === 'members' ? t('tenant.members') : tab === 'apikeys' ? t('tenant.api_keys') : t('tenant.subscription')}
          </button>
        ))}
      </div>

      {activeTab === 'members' && (
        <div>
          <form onSubmit={handleInvite} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
            <h4 style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>{t('tenant.invite_title')}</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                <input className="notif-input" style={{ flex: 1 }} placeholder={t('tenant.invite_email_placeholder')} value={inviteEmail} onChange={e => setInviteEmail(e.target.value)} required />
                <select value={inviteRole} onChange={e => setInviteRole(e.target.value)} className="filter-select">
                  <option value="viewer">{t('tenant.role_viewer')}</option>
                  <option value="editor">{t('tenant.role_editor')}</option>
                  <option value="admin">{t('tenant.role_admin')}</option>
                </select>
                <input className="notif-input" style={{ width: '200px' }} placeholder={t('tenant.workspace_id_placeholder')} value={inviteWsId} onChange={e => setInviteWsId(e.target.value)} required />
              </div>
              <button type="submit" className="audit-btn" disabled={inviting} style={{ alignSelf: 'flex-start' }}>
                {inviting ? t('tenant.inviting') : t('tenant.invite')}
              </button>
            </div>
            {inviteResult && (
              <p style={{ fontSize: '0.85rem', marginTop: '0.5rem', color: inviteResult.startsWith('❌') ? '#ef4444' : '#22c55e' }}>{inviteResult}</p>
            )}
          </form>

          {membersLoading ? (
            <PanelSkeleton compact message={t('tenant.loading_members')} />
          ) : members.length === 0 ? (
            <div className="rec-empty">
              <div className="rec-empty-icon">👥</div>
              <h4>{t('tenant.no_members')}</h4>
            </div>
          ) : (
            <div>
              <h4 style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
                {t('tenant.member_list')} ({members.length})
              </h4>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.member_name')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.member_email')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.member_role')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.member_workspace')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.member_since')}</th>
                  </tr>
                </thead>
                <tbody>
                  {members.map(m => (
                    <tr key={m.user_id} style={{ borderBottom: '1px solid var(--border)' }}>
                      <td style={{ padding: '0.5rem', fontWeight: 600 }}>{m.full_name}</td>
                      <td style={{ padding: '0.5rem', color: 'var(--text-muted)' }}>{m.email}</td>
                      <td style={{ padding: '0.5rem' }}><span className="rec-category-badge">{m.workspace_role}</span></td>
                      <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--text-faint)' }}>{m.workspace_id}</td>
                      <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>{new Date(m.created_at).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeTab === 'apikeys' && (
        <div>
          {showCreateForm ? (
            <form onSubmit={handleCreateKey} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
              <h4 style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>{t('tenant.key_create')}</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <input className="notif-input" style={{ flex: 1 }} placeholder={t('tenant.key_name')} value={newKeyName} onChange={e => setNewKeyName(e.target.value)} required />
                  <select value={newKeyRole} onChange={e => setNewKeyRole(e.target.value)} className="filter-select">
                    <option value="admin">{t('tenant.role_admin')}</option>
                    <option value="editor">{t('tenant.role_editor')}</option>
                    <option value="viewer">{t('tenant.role_viewer')}</option>
                  </select>
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button type="submit" className="audit-btn" disabled={creatingKey}>
                    {creatingKey ? t('tenant.key_creating') : t('tenant.key_create_btn')}
                  </button>
                  <button type="button" className="refresh-btn" onClick={() => setShowCreateForm(false)}>{t('common.cancel')}</button>
                </div>
              </div>
            </form>
          ) : (
            <button className="audit-btn" onClick={() => setShowCreateForm(true)} style={{ marginBottom: '1rem' }}>
              + {t('tenant.key_new')}
            </button>
          )}

          {createdKey && (
            <div style={{ background: 'var(--amber-bg)', border: '1px solid var(--medium)', borderRadius: '10px', padding: '1rem', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
                <span style={{ fontSize: '1.2rem' }}>⚠️</span>
                <div style={{ flex: 1 }}>
                  <h4 style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--warn-text)', marginBottom: '0.25rem' }}>{t('tenant.key_warning_title')}</h4>
                  <p style={{ fontSize: '0.85rem', color: '#a16207', marginBottom: '0.5rem' }}>{createdKey.warning}</p>
                  <div style={{ background: 'var(--surface)', padding: '0.75rem', borderRadius: '6px', fontFamily: 'monospace', fontSize: '0.8rem', wordBreak: 'break-all' }}>
                    {createdKey.api_key}
                  </div>
                  <p style={{ fontSize: '0.78rem', color: '#a16207', marginTop: '0.5rem' }}>{t('tenant.key_warning_save')}</p>
                  <button className="link-btn" onClick={() => setCreatedKey(null)} style={{ marginTop: '0.5rem' }}>{t('common.dismiss')}</button>
                </div>
              </div>
            </div>
          )}

          {keysLoading ? (
            <PanelSkeleton compact message={t('tenant.loading_keys')} />
          ) : apiKeys.length === 0 ? (
            <div className="rec-empty">
              <div className="rec-empty-icon">🔑</div>
              <h4>{t('tenant.no_keys')}</h4>
            </div>
          ) : (
            <div>
              <h4 style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
                {t('tenant.keys_list')} ({apiKeys.length})
              </h4>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.key_name_col')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.key_prefix_col')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.key_role_col')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.key_status')}</th>
                    <th style={{ padding: '0.5rem' }}>{t('tenant.key_created')}</th>
                    <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('tenant.key_actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {apiKeys.map(k => (
                    <tr key={k.id} style={{ borderBottom: '1px solid var(--border)' }}>
                      <td style={{ padding: '0.5rem', fontWeight: 600 }}>{k.name}</td>
                      <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.8rem' }}>{k.key_prefix}...</td>
                      <td style={{ padding: '0.5rem' }}><span className="rec-category-badge">{k.role}</span></td>
                      <td style={{ padding: '0.5rem' }}>
                        <span style={{ color: k.is_active ? '#22c55e' : '#ef4444', fontWeight: 600 }}>
                          {k.is_active ? t('tenant.key_active') : t('tenant.key_inactive')}
                        </span>
                      </td>
                      <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                        {new Date(k.created_at).toLocaleDateString()}
                      </td>
                      <td style={{ padding: '0.5rem', textAlign: 'right' }}>
                        <button className="link-btn" style={{ color: '#ef4444' }} onClick={() => handleDeleteKey(k.id)}>
                          {t('tenant.key_delete')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {activeTab === 'subscription' && (
        <div>
          {subLoading ? (
            <PanelSkeleton compact message={t('tenant.loading_subscription')} />
          ) : !subscription ? (
            <div className="rec-empty">
              <div className="rec-empty-icon">💳</div>
              <h4>{t('tenant.no_subscription')}</h4>
            </div>
          ) : (
            <div style={{ background: 'var(--surface-2)', padding: '1.5rem', borderRadius: '10px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>{t('tenant.sub_plan')}</span>
                  <div style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--text-strong)' }}>
                    {TIER_LABELS[subscription.tier] || subscription.tier}
                  </div>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>{t('tenant.sub_tenant_id')}</span>
                  <div style={{ fontFamily: 'monospace', fontSize: '0.85rem', color: 'var(--text-muted)' }}>{subscription.tenant_id}</div>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>{t('tenant.sub_updated')}</span>
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                    {new Date(subscription.updated_at).toLocaleDateString()}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default TenantSettingsPanel
