import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { triggerDigest } from '../api/client'

interface Props {
  workspaceId: string
}

export function ReportsPanel({ workspaceId }: Props) {
  const { t } = useTranslation()
  const [generating, setGenerating] = useState(false)
  const [status, setStatus] = useState<string | null>(null)

  async function handleGenerateDigest() {
    try {
      setGenerating(true)
      setStatus(t('reports.generating_status'))

      const blob = await triggerDigest(workspaceId)

      // Download the PDF
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `weekly-digest-${new Date().toISOString().slice(0, 10)}.pdf`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      setStatus('✅ Rapor indiriliyor...')
      setTimeout(() => setStatus(null), 3000)
    } catch (err) {
      setStatus(`❌ ${err instanceof Error ? err.message : t('reports.generate_failed')}`)
      setTimeout(() => setStatus(null), 5000)
    } finally {
      setGenerating(false)
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
            {generating ? t('optimization.generating') : 'Oluştur & İndir'}
          </button>
        </div>

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

        <div className="reports-action-card disabled">
          <div className="reports-action-icon">🔍</div>
          <div className="reports-action-info">
            <h4>Site Denetim Raporu</h4>
            <p>AI bot erişilebilirlik ve site denetim sonuçları. (Çok yakında)</p>
          </div>
          <button className="reports-generate-btn" disabled>
            Çok Yakında
          </button>
        </div>
      </div>

      {status && (
        <div className={`reports-status ${status.startsWith('✅') ? 'success' : status.startsWith('❌') ? 'error' : ''}`}>
          {status}
        </div>
      )}
    </div>
  )
}
