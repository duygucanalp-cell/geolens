import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { triggerDigest, getScores } from '../api/client'
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

export function ReportsPanel({ workspaceId }: Props) {
  const { t } = useTranslation()
  const [generating, setGenerating] = useState(false)
  const [exportingCSV, setExportingCSV] = useState(false)
  const [exportingXLSX, setExportingXLSX] = useState(false)
  const [status, setStatus] = useState<string | null>(null)

  async function handleGenerateDigest() {
    try {
      setGenerating(true)
      setStatus(t('reports.generating_status'))
      const blob = await triggerDigest(workspaceId)
      downloadBlob(blob, `weekly-digest-${new Date().toISOString().slice(0, 10)}.pdf`)
      setStatus('Rapor indiriliyor...')
      setTimeout(() => setStatus(null), 3000)
    } catch (err) {
      setStatus(`Hata: ${err instanceof Error ? err.message : t('reports.generate_failed')}`)
      setTimeout(() => setStatus(null), 5000)
    } finally {
      setGenerating(false)
    }
  }

  async function handleExportCSV() {
    try {
      setExportingCSV(true)
      setStatus('CSV oluşturuluyor...')
      const scores = await getScores(workspaceId)
      const rows = scores.map(s => ({
        brand: s.brand_name,
        score: s.value,
        ci_low: s.ci_low,
        ci_high: s.ci_high,
        fidelity: s.fidelity_label,
        engine: s.engine_breakdown ? Object.keys(s.engine_breakdown).join(';') : '',
        updated: s.freshness_at,
      }))
      const csv = toCSV(rows)
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
      downloadBlob(blob, `visibility-scores-${new Date().toISOString().slice(0, 10)}.csv`)
      setStatus('CSV indiriliyor...')
      setTimeout(() => setStatus(null), 3000)
    } catch (err) {
      setStatus(`Hata: ${err instanceof Error ? err.message : t('reports.generate_failed')}`)
      setTimeout(() => setStatus(null), 5000)
    } finally {
      setExportingCSV(false)
    }
  }

  async function handleExportXLSX() {
    try {
      setExportingXLSX(true)
      setStatus('Excel oluşturuluyor...')
      const scores = await getScores(workspaceId)
      // Generate TSV (tab-separated) as a lightweight XLSX alternative — opens in Excel
      const rows = scores.map(s => [
        s.brand_name,
        String(s.value),
        String(s.ci_low),
        String(s.ci_high),
        s.fidelity_label,
        s.engine_breakdown ? Object.keys(s.engine_breakdown).join('; ') : '',
        s.freshness_at,
      ].join('\t'))
      const header = ['Brand', 'Score', 'CI Low', 'CI High', 'Fidelity', 'Engines', 'Last Updated'].join('\t')
      const tsv = [header, ...rows].join('\n')
      const blob = new Blob([tsv], { type: 'text/tab-separated-values;charset=utf-8' })
      downloadBlob(blob, `visibility-scores-${new Date().toISOString().slice(0, 10)}.xlsx`)
      setStatus('Excel indiriliyor...')
      setTimeout(() => setStatus(null), 3000)
    } catch (err) {
      setStatus(`Hata: ${err instanceof Error ? err.message : t('reports.generate_failed')}`)
      setTimeout(() => setStatus(null), 5000)
    } finally {
      setExportingXLSX(false)
    }
  }

  return (
    <div className="reports-panel">
      <div className="reports-header">
        <h3>Raporlar</h3>
        <p className="reports-desc">
          Haftalık özet raporlarını oluşturun ve indirin. Raporlar, markalarınızın AI
          görünürlük skorlarını, trendlerini ve önerilerini içerir.
        </p>
      </div>

      <div className="reports-actions">
        {/* PDF Weekly Digest */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📊</div>
          <div className="reports-action-info">
            <h4>Haftalık Özet Raporu</h4>
            <p>Marka skorları, trendler ve öneriler içeren kapsamlı PDF raporu.</p>
          </div>
          <button
            className="reports-generate-btn"
            onClick={handleGenerateDigest}
            disabled={generating}
          >
            {generating ? 'Oluşturuluyor...' : 'PDF Oluştur & İndir'}
          </button>
        </div>

        {/* CSV Export */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📄</div>
          <div className="reports-action-info">
            <h4>CSV Dışa Aktarım</h4>
            <p>Görünürlük skorlarını CSV formatında dışa aktarın. Excel ve Google Sheets ile uyumlu.</p>
          </div>
          <button
            className="reports-generate-btn"
            onClick={handleExportCSV}
            disabled={exportingCSV}
          >
            {exportingCSV ? 'Oluşturuluyor...' : 'CSV İndir'}
          </button>
        </div>

        {/* Excel Export */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📗</div>
          <div className="reports-action-info">
            <h4>Excel Dışa Aktarım</h4>
            <p>Görünürlük skorlarını Excel uyumlu TSV formatında dışa aktarın.</p>
          </div>
          <button
            className="reports-generate-btn"
            onClick={handleExportXLSX}
            disabled={exportingXLSX}
          >
            {exportingXLSX ? 'Oluşturuluyor...' : 'Excel İndir'}
          </button>
        </div>

        {/* Coming Soon Cards */}
        <div className="reports-action-card disabled">
          <div className="reports-action-icon">📋</div>
          <div className="reports-action-info">
            <h4>Marka Skor Kartı</h4>
            <p>Tek bir markanın detaylı skor karnesi. (Çok yakında)</p>
          </div>
          <button className="reports-generate-btn" disabled>
            Çok Yakında
          </button>
        </div>
      </div>

      <SEODataPanel workspaceId={workspaceId} onStatus={setStatus} />

      <SiteAuditPanel workspaceId={workspaceId} onStatus={setStatus} />

      {status && (
        <div className={`reports-status ${status.startsWith('Hata') ? 'error' : 'success'}`}>
          {status}
        </div>
      )}
    </div>
  )
}
