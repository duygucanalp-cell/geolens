import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
import { listArchiveEntries, getArchiveEntry, archiveResponse, getArchiveVersionHistory } from '../api/client'
import type { Brand, ArchiveEntry, ArchiveEntryDetail, ArchiveVersion } from '../types'
import { ENGINE_NAMES } from '../types'

interface Props {
  workspaceId: string
  brands: Brand[]
}

export function ArchivePanel({ workspaceId: ws, brands }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [brandId, setBrandId] = useState(brands[0]?.id ?? '')
  const [entries, setEntries] = useState<ArchiveEntry[]>([])
  const [versions, setVersions] = useState<ArchiveVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Archive form
  const [engineName, setEngineName] = useState('')
  const [promptText, setPromptText] = useState('')
  const [response, setResponse] = useState('')
  const [archiving, setArchiving] = useState(false)

  // Detail modal
  const [detail, setDetail] = useState<ArchiveEntryDetail | null>(null)

  const engineKeys = Object.keys(ENGINE_NAMES)

  useEffect(() => {
    if (brands.length > 0 && !brands.some(b => b.id === brandId)) {
      setBrandId(brands[0].id)
    }
  }, [brands, brandId])

  useEffect(() => {
    if (brandId) loadAll()
  }, [brandId])

  async function loadAll() {
    try {
      setLoading(true)
      setError(null)
      const [entriesData, versionsData] = await Promise.all([
        listArchiveEntries(ws, brandId),
        getArchiveVersionHistory(ws, brandId),
      ])
      setEntries(entriesData)
      setVersions(versionsData)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('archive.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleArchive(e: React.FormEvent) {
    e.preventDefault()
    if (!brandId || !response.trim()) return
    setArchiving(true)
    setError(null)
    try {
      await archiveResponse(ws, {
        brand_id: brandId,
        engine_name: engineName,
        prompt_text: promptText,
        response: response.trim(),
      })
      setResponse('')
      setPromptText('')
      loadAll()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('archive.archive_error'))
    } finally {
      setArchiving(false)
    }
  }

  async function handleView(id: string) {
    try {
      setDetail(await getArchiveEntry(ws, id))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('archive.load_error'))
    }
  }

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>🗄 {t('archive.title')}</h3>
        <p className="rec-desc">{t('archive.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <select className="filter-select" value={brandId} onChange={e => setBrandId(e.target.value)}>
          {brands.map(b => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadAll}>{t('common.refresh')}</button>
      </div>

      {/* Archive form */}
      <form onSubmit={handleArchive} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <select className="filter-select" value={engineName} onChange={e => setEngineName(e.target.value)}>
              <option value="">{t('archive.engine_placeholder')}</option>
              {engineKeys.map(k => (
                <option key={k} value={k}>{t(ENGINE_NAMES[k]) || k}</option>
              ))}
            </select>
          </div>
          <input
            className="notif-input"
            placeholder={t('archive.prompt_placeholder')}
            value={promptText}
            onChange={e => setPromptText(e.target.value)}
          />
          <textarea
            className="notif-input"
            rows={4}
            placeholder={t('archive.response_placeholder')}
            value={response}
            onChange={e => setResponse(e.target.value)}
            style={{ fontFamily: 'inherit', fontSize: '0.85rem', resize: 'vertical' }}
          />
          <button type="submit" className="audit-btn" disabled={archiving || !response.trim()}>
            {archiving ? t('archive.archiving') : t('archive.archive')}
          </button>
        </div>
      </form>

      {/* Version history */}
      {versions.length > 0 && (
        <div style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <strong style={{ fontSize: '0.9rem' }}>{t('archive.versions_title')}</strong>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
            {versions.map(v => (
              <span key={v.entry_id} className="rec-status-badge" style={{ background: 'var(--accent-soft)', color: 'var(--accent-hover)', cursor: 'pointer' }} onClick={() => handleView(v.entry_id)}>
                v{v.version} · {new Date(v.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}
              </span>
            ))}
          </div>
        </div>
      )}

      {loading ? (
        <PanelSkeleton message={t('archive.loading')} />
      ) : entries.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🗄️</div>
          <h4>{t('archive.empty_title')}</h4>
          <p>{t('archive.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {entries.map(e => (
            <div key={e.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: '#0ea5e9' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{t(ENGINE_NAMES[e.engine_name]) || e.engine_name}</span>
                  <span className="rec-status-badge" style={{ background: 'var(--accent-soft)', color: 'var(--accent-hover)' }}>v{e.version}</span>
                  <span className="rec-date">
                    {new Date(e.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
                <p className="rec-title" style={{ fontSize: '0.85rem', fontWeight: 600 }}>{e.prompt_text || '—'}</p>
                <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{e.response_preview}</p>
              </div>
              <div className="rec-card-actions">
                <button className="rec-apply-btn" title={t('archive.view')} onClick={() => handleView(e.id)}>👁</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Detail modal */}
      {detail && (
        <div className="modal-overlay" onClick={() => setDetail(null)}>
          <div className="modal-box" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>🗄 {t('archive.detail_title')} · v{detail.version}</h3>
              <button className="modal-close" onClick={() => setDetail(null)}>✕</button>
            </div>
            <p className="rec-category-badge" style={{ display: 'inline-block', marginBottom: '0.5rem' }}>
              {t(ENGINE_NAMES[detail.engine_name]) || detail.engine_name}
            </p>
            {detail.prompt_text && <p style={{ fontWeight: 600, fontSize: '0.9rem' }}>{detail.prompt_text}</p>}
            <pre className="modal-pre">{detail.response_full}</pre>
            <p className="rec-meta" style={{ marginTop: '0.5rem' }}>
              <span className="rec-date">{new Date(detail.created_at).toLocaleString(dateLocale)}</span>
              <span className="rec-confidence-label" style={{ fontFamily: 'monospace' }}>{detail.content_hash.slice(0, 16)}…</span>
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
