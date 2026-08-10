import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import {
  listReplaySnapshots, getReplaySnapshot, captureReplaySnapshot,
  compareReplaySnapshots, deleteReplaySnapshot,
} from '../api/client'
import type { Brand, ReplaySnapshot, ReplaySnapshotDetail, ReplayDiff } from '../types'
import { ENGINE_NAMES } from '../types'

interface Props {
  workspaceId: string
  brands: Brand[]
}

export function ReplayPanel({ workspaceId: ws, brands }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [brandId, setBrandId] = useState(brands[0]?.id ?? '')
  const [snapshots, setSnapshots] = useState<ReplaySnapshot[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Capture form
  const [prompt, setPrompt] = useState('')
  const [capturing, setCapturing] = useState(false)

  // Detail modal
  const [detail, setDetail] = useState<ReplaySnapshotDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  // Compare
  const [compareA, setCompareA] = useState('')
  const [compareB, setCompareB] = useState('')
  const [diff, setDiff] = useState<ReplayDiff | null>(null)
  const [comparing, setComparing] = useState(false)

  useEffect(() => {
    if (brands.length > 0 && !brands.some(b => b.id === brandId)) {
      setBrandId(brands[0].id)
    }
  }, [brands, brandId])

  useEffect(() => {
    if (brandId) loadSnapshots()
  }, [brandId])

  async function loadSnapshots() {
    try {
      setLoading(true)
      setError(null)
      const data = await listReplaySnapshots(ws, brandId)
      setSnapshots(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('replay.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleCapture(e: React.FormEvent) {
    e.preventDefault()
    if (!brandId || !prompt.trim()) return
    setCapturing(true)
    setError(null)
    try {
      await captureReplaySnapshot(ws, brandId, prompt.trim())
      setPrompt('')
      loadSnapshots()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('replay.capture_error'))
    } finally {
      setCapturing(false)
    }
  }

  async function handleView(id: string) {
    setDetailLoading(true)
    setDetail(null)
    try {
      setDetail(await getReplaySnapshot(ws, id))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('replay.load_error'))
    } finally {
      setDetailLoading(false)
    }
  }

  async function handleCompare() {
    if (!compareA || !compareB || compareA === compareB) return
    setComparing(true)
    setDiff(null)
    try {
      setDiff(await compareReplaySnapshots(ws, compareA, compareB))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('replay.compare_error'))
    } finally {
      setComparing(false)
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm(t('replay.delete_confirm'))) return
    try {
      await deleteReplaySnapshot(ws, id)
      setSnapshots(snapshots.filter(s => s.id !== id))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('replay.delete_error'))
    }
  }

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>▶ {t('replay.title')}</h3>
        <p className="rec-desc">{t('replay.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      {/* Brand selector */}
      <div className="dashboard-filters">
        <select className="filter-select" value={brandId} onChange={e => { setBrandId(e.target.value); setDiff(null) }}>
          {brands.map(b => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadSnapshots}>{t('common.refresh')}</button>
      </div>

      {/* Capture form */}
      <form onSubmit={handleCapture} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <textarea
            className="notif-input"
            rows={2}
            placeholder={t('replay.prompt_placeholder')}
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            style={{ fontFamily: 'inherit', fontSize: '0.85rem', resize: 'vertical' }}
          />
          <button type="submit" className="audit-btn" disabled={capturing || !prompt.trim()}>
            {capturing ? t('replay.capturing') : t('replay.capture')}
          </button>
        </div>
      </form>

      {/* Compare section */}
      {snapshots.length >= 2 && (
        <div style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <strong style={{ fontSize: '0.9rem' }}>{t('replay.compare_title')}</strong>
          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <select className="filter-select" value={compareA} onChange={e => setCompareA(e.target.value)}>
              <option value="">{t('replay.snapshot_a')}</option>
              {snapshots.map(s => (
                <option key={s.id} value={s.id}>
                  {s.engine_name} · {new Date(s.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}
                </option>
              ))}
            </select>
            <select className="filter-select" value={compareB} onChange={e => setCompareB(e.target.value)}>
              <option value="">{t('replay.snapshot_b')}</option>
              {snapshots.map(s => (
                <option key={s.id} value={s.id}>
                  {s.engine_name} · {new Date(s.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}
                </option>
              ))}
            </select>
            <button className="audit-btn" onClick={handleCompare} disabled={comparing || !compareA || !compareB || compareA === compareB}>
              {comparing ? t('replay.comparing') : t('replay.compare')}
            </button>
          </div>
          {diff && (
            <div style={{ marginTop: '0.75rem', padding: '0.75rem', borderRadius: '8px', background: diff.has_changed ? 'var(--danger-bg)' : 'var(--success-bg)', fontSize: '0.85rem' }}>
              <strong style={{ color: diff.has_changed ? '#ef4444' : '#22c55e' }}>
                {diff.has_changed ? t('replay.changed') : t('replay.unchanged')}
              </strong>
              {diff.changes && <p style={{ marginTop: '0.3rem', color: 'var(--text-secondary)' }}>{diff.changes}</p>}
              <p style={{ marginTop: '0.3rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                {t('replay.analyzed_at')}: {new Date(diff.analyzed_at).toLocaleString(dateLocale)}
              </p>
            </div>
          )}
        </div>
      )}

      {loading ? (
        <div className="dashboard-loading">{t('replay.loading')}</div>
      ) : snapshots.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">▶️</div>
          <h4>{t('replay.empty_title')}</h4>
          <p>{t('replay.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {snapshots.map(s => (
            <div key={s.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: 'var(--accent)' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{t(ENGINE_NAMES[s.engine_name]) || s.engine_name}</span>
                  <span className="rec-date">
                    {new Date(s.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
                <p className="rec-title" style={{ fontSize: '0.85rem', fontWeight: 600 }}>{s.prompt_text}</p>
                <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{s.response_preview}</p>
                <div className="rec-meta">
                  <span className="rec-confidence-label" style={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>{s.content_hash.slice(0, 12)}…</span>
                </div>
              </div>
              <div className="rec-card-actions">
                <button className="rec-apply-btn" title={t('replay.view')} onClick={() => handleView(s.id)}>👁</button>
                <button className="rec-apply-btn" title={t('replay.delete')} onClick={() => handleDelete(s.id)} style={{ background: 'var(--danger-bg)', color: '#ef4444' }}>🗑</button>
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
              <h3>▶ {t('replay.detail_title')}</h3>
              <button className="modal-close" onClick={() => setDetail(null)}>✕</button>
            </div>
            {detailLoading ? (
              <div className="dashboard-loading">{t('replay.loading')}</div>
            ) : (
              <>
                <p className="rec-category-badge" style={{ display: 'inline-block', marginBottom: '0.5rem' }}>
                  {t(ENGINE_NAMES[detail.engine_name]) || detail.engine_name}
                </p>
                <p style={{ fontWeight: 600, fontSize: '0.9rem' }}>{detail.prompt_text}</p>
                <pre className="modal-pre">{detail.response_full}</pre>
                <p className="rec-meta" style={{ marginTop: '0.5rem' }}>
                  <span className="rec-date">{new Date(detail.created_at).toLocaleString(dateLocale)}</span>
                  <span className="rec-confidence-label" style={{ fontFamily: 'monospace' }}>{detail.content_hash.slice(0, 16)}…</span>
                </p>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
