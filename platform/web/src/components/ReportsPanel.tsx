import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { triggerDigest, getScores, getBrands } from '../api/client'
import type { Brand, Score } from '../types'
import { SEODataPanel } from './SEODataPanel'
import { SiteAuditPanel } from './SiteAuditPanel'

interface Props {
  workspaceId: string
}

/** Convert an array of objects to CSV string */
function toCSV(data: Record<string, unknown>[]): string {
  if (data.length === 0) return ''
  const headers = Object.keys(data[0])
  const esc = (v: unknown) => {
    const s = String(v ?? '')
    return s.includes(',') || s.includes('"') || s.includes('\n') ? `"${s.replace(/"/g, '""')}"` : s
  }
  return [headers.join(','), ...data.map(row => headers.map(h => esc(row[h])).join(','))].join('\n')
}

/** Trigger a download of the given blob */
function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

interface Status { kind: 'success' | 'error'; text: string }

/** Skorları CSV satırlarına dönüştürür (opsiyonel marka filtresi ile) */
function scoreRows(scores: Score[], brandName?: string): Record<string, unknown>[] {
  return scores
    .filter(s => !brandName || s.brand_name === brandName)
    .map(s => ({
      brand: s.brand_name,
      score: s.value,
      ci_low: s.ci_low,
      ci_high: s.ci_high,
      fidelity: s.fidelity_label,
      engine: s.engine_breakdown ? Object.keys(s.engine_breakdown).join(';') : '',
      updated: s.freshness_at,
    }))
}

export function ReportsPanel({ workspaceId }: Props) {
  const { t } = useTranslation()
  const [generating, setGenerating] = useState(false)
  const [exportingCSV, setExportingCSV] = useState(false)
  const [exportingXLSX, setExportingXLSX] = useState(false)
  const [exportingBrand, setExportingBrand] = useState(false)
  const [status, setStatus] = useState<Status | null>(null)
  const [brands, setBrands] = useState<Brand[]>([])
  const [selectedBrand, setSelectedBrand] = useState('')

  useEffect(() => {
    getBrands(workspaceId)
      .then(b => {
        setBrands(b)
        if (b.length > 0) setSelectedBrand(b[0].id)
      })
      .catch(() => { /* marka listesi yüklenemezse seçici boş kalır */ })
  }, [workspaceId])

  const selectedBrandName = brands.find(b => b.id === selectedBrand)?.name

  function showStatus(kind: Status['kind'], text: string, persistMs = 3000) {
    setStatus({ kind, text })
    setTimeout(() => setStatus(null), persistMs)
  }

  function showError(err: unknown) {
    const message = err instanceof Error ? err.message : ''
    showStatus('error', message || t('reports.generate_failed'), 5000)
  }

  // SEO/SiteAudit panelleri ham string gönderir (hata mesajları 'Hata:'/'Error:' ile başlar)
  function flashStringStatus(msg: string | null) {
    if (msg === null) {
      setStatus(null)
      return
    }
    const isError = msg.startsWith('Hata') || msg.startsWith('Error') || msg.includes('❌')
    setStatus({ kind: isError ? 'error' : 'success', text: msg })
  }

  async function handleGenerateDigest() {
    try {
      setGenerating(true)
      showStatus('success', t('reports.generating_status'))
      const blob = await triggerDigest(workspaceId)
      downloadBlob(blob, `weekly-digest-${new Date().toISOString().slice(0, 10)}.pdf`)
      showStatus('success', t('reports.downloading'))
    } catch (err) {
      showError(err)
    } finally {
      setGenerating(false)
    }
  }

  async function handleExportCSV() {
    try {
      setExportingCSV(true)
      showStatus('success', t('reports.creating'))
      const scores = await getScores(workspaceId)
      const csv = toCSV(scoreRows(scores))
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
      downloadBlob(blob, `visibility-scores-${new Date().toISOString().slice(0, 10)}.csv`)
      showStatus('success', t('reports.csv_downloaded'))
    } catch (err) {
      showError(err)
    } finally {
      setExportingCSV(false)
    }
  }

  async function handleExportXLSX() {
    try {
      setExportingXLSX(true)
      showStatus('success', t('reports.creating'))
      const scores = await getScores(workspaceId)
      // Excel uyumlu TSV (tab-separated) — Excel ile açılabilir
      const rows = scoreRows(scores)
      const header = ['Brand', 'Score', 'CI Low', 'CI High', 'Fidelity', 'Engines', 'Last Updated']
      const tsv = [header.join('\t'), ...rows.map(r => header.map(h => String(r[h] ?? '')).join('\t'))].join('\n')
      const blob = new Blob([tsv], { type: 'text/tab-separated-values;charset=utf-8' })
      downloadBlob(blob, `visibility-scores-${new Date().toISOString().slice(0, 10)}.xlsx`)
      showStatus('success', t('reports.xlsx_downloaded'))
    } catch (err) {
      showError(err)
    } finally {
      setExportingXLSX(false)
    }
  }

  async function handleExportBrandCard() {
    if (!selectedBrand) {
      showStatus('error', t('reports.brand_select_required'), 4000)
      return
    }
    try {
      setExportingBrand(true)
      showStatus('success', t('reports.creating'))
      const scores = await getScores(workspaceId)
      const csv = toCSV(scoreRows(scores, selectedBrandName))
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
      const slug = (selectedBrandName || 'brand').toLowerCase().replace(/[^a-z0-9]+/g, '-')
      downloadBlob(blob, `brand-scorecard-${slug}-${new Date().toISOString().slice(0, 10)}.csv`)
      showStatus('success', t('reports.brand_downloaded'))
    } catch (err) {
      showError(err)
    } finally {
      setExportingBrand(false)
    }
  }

  return (
    <div className="reports-panel">
      <div className="reports-header">
        <h3>{t('reports.title')}</h3>
        <p className="reports-desc">{t('reports.desc')}</p>
      </div>

      <div className="reports-actions">
        {/* PDF Weekly Digest */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📊</div>
          <div className="reports-action-info">
            <h4>{t('reports.weekly_title')}</h4>
            <p>{t('reports.weekly_desc')}</p>
          </div>
          <button
            className="reports-generate-btn"
            onClick={handleGenerateDigest}
            disabled={generating}
          >
            {generating ? t('reports.generating') : `${t('reports.generate_download')} (PDF)`}
          </button>
        </div>

        {/* CSV Export */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📄</div>
          <div className="reports-action-info">
            <h4>{t('reports.csv_title')}</h4>
            <p>{t('reports.csv_desc')}</p>
          </div>
          <button
            className="reports-generate-btn"
            onClick={handleExportCSV}
            disabled={exportingCSV}
          >
            {exportingCSV ? t('reports.generating') : t('reports.csv_download')}
          </button>
        </div>

        {/* Excel Export */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📗</div>
          <div className="reports-action-info">
            <h4>{t('reports.xlsx_title')}</h4>
            <p>{t('reports.xlsx_desc')}</p>
          </div>
          <button
            className="reports-generate-btn"
            onClick={handleExportXLSX}
            disabled={exportingXLSX}
          >
            {exportingXLSX ? t('reports.generating') : t('reports.xlsx_download')}
          </button>
        </div>

        {/* Brand Score Card — tek marka karnesi (per-brand CSV) */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📋</div>
          <div className="reports-action-info">
            <h4>{t('reports.scorecard_title')}</h4>
            <p>{t('reports.scorecard_desc')}</p>
            <div className="brand-mgmt-competitor-add" style={{ marginTop: '0.5rem', marginBottom: 0 }}>
              <select
                className="filter-select"
                style={{ flex: 1 }}
                value={selectedBrand}
                onChange={e => setSelectedBrand(e.target.value)}
                aria-label={t('reports.brand_select')}
              >
                {brands.length === 0 && <option value="">{t('reports.brand_select')}</option>}
                {brands.map(b => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
              <button
                className="reports-generate-btn"
                onClick={handleExportBrandCard}
                disabled={exportingBrand || brands.length === 0}
              >
                {exportingBrand ? t('reports.generating') : t('reports.brand_download')}
              </button>
            </div>
          </div>
        </div>
      </div>

      <SEODataPanel workspaceId={workspaceId} onStatus={flashStringStatus} />

      <SiteAuditPanel workspaceId={workspaceId} onStatus={flashStringStatus} />

      {status && (
        <div className={`reports-status ${status.kind === 'error' ? 'error' : 'success'}`} role="status">
          {status.text}
        </div>
      )}
    </div>
  )
}
