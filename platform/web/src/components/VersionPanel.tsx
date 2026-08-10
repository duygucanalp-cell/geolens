import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useMemo, useState } from 'react'
import { getVersionEntries } from '../api/client'
import type { VersionEntry } from '../types'

interface Props {
  workspaceId: string
}

export function VersionPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const [entries, setEntries] = useState<VersionEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedEntry, setSelectedEntry] = useState<VersionEntry | null>(null)
  const [entityFilter, setEntityFilter] = useState('all')

  useEffect(() => { loadData() }, [])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const data = await getVersionEntries()
      setEntries(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('version.load_error'))
    } finally {
      setLoading(false)
    }
  }

  const entityTypes = useMemo(() => {
    const set = new Set(entries.map((e) => e.entity_type))
    return Array.from(set)
  }, [entries])

  const filtered = useMemo(() => {
    if (entityFilter === 'all') return entries
    return entries.filter((e) => e.entity_type === entityFilter)
  }, [entries, entityFilter])

  if (loading) return <PanelSkeleton message={t('version.loading')} />
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>{t('version.retry')}</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>{t('version.title')}</h3>
        <p className="monitoring-desc">{t('version.desc')}</p>
      </div>

      <div className="dashboard-filters">
        <select value={entityFilter} onChange={(e) => setEntityFilter(e.target.value)} className="filter-select">
          <option value="all">{t('version.filter_all')}</option>
          {entityTypes.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadData}>{t('common.refresh')}</button>
      </div>

      {selectedEntry ? (
        <div>
          <button className="link-btn" onClick={() => setSelectedEntry(null)} style={{ marginBottom: '1rem', display: 'block' }}>
            {t('version.back_list')}
          </button>
          <div style={{ background: 'var(--surface-2)', padding: '1.25rem', borderRadius: '10px' }}>
            <h4 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>
              {t('version.detail_title', { name: selectedEntry.entity_name || selectedEntry.entity_id })}
            </h4>
            <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '0.5rem', fontSize: '0.85rem' }}>
              <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{t('version.entity_type')}</span>
              <span>{selectedEntry.entity_type}</span>
              <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{t('version.old_version')}</span>
              <span style={{ fontFamily: 'monospace', background: 'var(--danger-bg)', padding: '0.1rem 0.4rem', borderRadius: '3px', display: 'inline-block', width: 'fit-content' }}>{selectedEntry.old_version || '-'}</span>
              <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{t('version.new_version')}</span>
              <span style={{ fontFamily: 'monospace', background: 'var(--success-bg)', padding: '0.1rem 0.4rem', borderRadius: '3px', display: 'inline-block', width: 'fit-content' }}>{selectedEntry.new_version || '-'}</span>
              <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{t('version.change_notes')}</span>
              <span>{selectedEntry.change_notes || '-'}</span>
              <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{t('version.changed_by')}</span>
              <span>{selectedEntry.changed_by || '-'}</span>
              <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{t('version.date')}</span>
              <span>{new Date(selectedEntry.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
            </div>
            {selectedEntry.old_version !== selectedEntry.new_version && (
              <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'var(--accent-soft)', borderRadius: '8px', fontSize: '0.85rem', color: 'var(--accent-strong)' }}>
                {t('version.change_detected', { old: selectedEntry.old_version, new: selectedEntry.new_version })}
              </div>
            )}
          </div>
        </div>
      ) : (
        <>
          {filtered.length === 0 ? (
            <div className="rec-empty">
              <div className="rec-empty-icon">🔖</div>
              <h4>{t('version.empty_title')}</h4>
              <p>{t('version.empty_desc')}</p>
            </div>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                  <th style={{ padding: '0.5rem' }}>{t('version.table_entity')}</th>
                  <th style={{ padding: '0.5rem' }}>{t('version.table_type')}</th>
                  <th style={{ padding: '0.5rem' }}>{t('version.table_old')}</th>
                  <th style={{ padding: '0.5rem' }}>{t('version.table_new')}</th>
                  <th style={{ padding: '0.5rem' }}>{t('version.table_date')}</th>
                  <th style={{ padding: '0.5rem' }}></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((e) => (
                  <tr key={e.id} style={{ borderBottom: '1px solid var(--border)', cursor: 'pointer' }}
                    onClick={() => setSelectedEntry(e)}>
                    <td style={{ padding: '0.5rem', fontWeight: 600 }}>{e.entity_name || e.entity_id}</td>
                    <td style={{ padding: '0.5rem', color: 'var(--text-muted)' }}>{e.entity_type}</td>
                    <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.8rem', color: '#ef4444' }}>{e.old_version || '-'}</td>
                    <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.8rem', color: '#22c55e' }}>{e.new_version || '-'}</td>
                    <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                      {new Date(e.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                    </td>
                    <td style={{ padding: '0.5rem', color: 'var(--accent)', fontSize: '0.8rem' }}>{t('version.table_detail')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  )
}
