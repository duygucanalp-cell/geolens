import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { startDiscoveryScan, getScanResults } from '../api/client'
import type { ScanResult } from '../types'

interface Props { workspaceId: string }

const RISK_COLORS: Record<string, string> = { critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#22c55e' }
const RESOURCE_ICONS: Record<string, string> = { lambda: '⚡', sagemaker: '🤖', vertex_ai: '🧠', endpoint: '🔗', storage: '💾', api: '🔌' }

export function DiscoveryPanel({ workspaceId: _ws }: Props) {
  const { t } = useTranslation()
  const RISK_LABELS: Record<string, string> = { critical: t('severity.critical'), high: t('severity.high'), medium: t('severity.medium'), low: t('severity.low') }
  const [scanResult, setScanResult] = useState<ScanResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [scanType, setScanType] = useState('api')

  async function handleStartScan() {
    setLoading(true)
    setError(null)
    setScanResult(null)
    try {
      const { scan_id } = await startDiscoveryScan(scanType)
      // Poll for results after a delay to let the scan complete
      setTimeout(async () => {
        try {
          const result = await getScanResults(scan_id)
          setScanResult(result)
        } catch (err) {
          // If scan still running, try once more
          setTimeout(async () => {
            try {
              const result = await getScanResults(scan_id)
              setScanResult(result)
            } catch (e) {
              setError(e instanceof Error ? e.message : t('discovery.load_error'))
            }
          }, 5000)
        }
        setLoading(false)
      }, 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('discovery.start_error'))
      setLoading(false)
    }
  }

  const findings = scanResult?.findings || []
  const criticalCount = findings.filter(f => f.risk_level === 'critical').length
  const highCount = findings.filter(f => f.risk_level === 'high').length

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>🕵️ Shadow AI Discovery</h3>
        <p className="rec-desc">Kurum genelinde kayıt dışı AI kullanımını tespit eder.</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <select value={scanType} onChange={e => setScanType(e.target.value)} className="filter-select">
          <option value="api">API Taraması</option>
          <option value="full">Tam Tarama</option>
          <option value="cloud">Cloud Kaynakları</option>
        </select>
        <button className="audit-btn" onClick={handleStartScan} disabled={loading}>
          {loading ? t('discovery.scanning') : t('discovery.start')}
        </button>
      </div>

      {loading && !scanResult && (
        <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
          <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🔍</div>
          <p>Shadow AI taraması yapılıyor...</p>
        </div>
      )}

      {scanResult && (
        <>
          {/* Summary */}
          <div className="rec-summary">
            <div className="rec-summary-card total">
              <span className="rec-summary-value">{findings.length}</span>
              <span className="rec-summary-label">Bulunan Kaynak</span>
            </div>
            <div className="rec-summary-card" style={{ background: criticalCount > 0 ? '#fef2f2' : '#f0fdf4' }}>
              <span className="rec-summary-value" style={{ color: '#ef4444' }}>{criticalCount}</span>
              <span className="rec-summary-label">Kritik</span>
            </div>
            <div className="rec-summary-card" style={{ background: highCount > 0 ? '#fff7ed' : '#f0fdf4' }}>
              <span className="rec-summary-value" style={{ color: '#f97316' }}>{highCount}</span>
              <span className="rec-summary-label">Yüksek Risk</span>
            </div>
            <div className="rec-summary-card" style={{ background: '#f8fafc' }}>
              <span className="rec-summary-value" style={{ color: '#6366f1' }}>{scanResult.status}</span>
              <span className="rec-summary-label">Durum</span>
            </div>
          </div>

          {/* Scan meta */}
          <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginBottom: '1rem' }}>
            Scan ID: {scanResult.scan_id} · Tip: {scanResult.scan_type}
          </div>

          {findings.length === 0 ? (
            <div className="rec-empty"><div className="rec-empty-icon">✅</div><h4>Shadow AI bulunamadı</h4></div>
          ) : (
            <div className="rec-list">
              {findings.map((f, i) => (
                <div key={i} className="rec-card">
                  <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: RISK_COLORS[f.risk_level] || '#94a3b8' }} /></div>
                  <div className="rec-card-content">
                    <div className="rec-card-header">
                      <span className="rec-category-badge">{RESOURCE_ICONS[f.resource_type] || '📦'} {f.resource_type}</span>
                      <span className="rec-severity-badge" style={{
                        color: RISK_COLORS[f.risk_level],
                        borderColor: RISK_COLORS[f.risk_level],
                      }}>
                        {RISK_LABELS[f.risk_level] || f.risk_level}
                      </span>
                      <span className="rec-category-badge">{f.provider}</span>
                    </div>
                    <h4 className="rec-title">{f.resource_name}</h4>
                    <p className="rec-detail">Bölge: {f.region}</p>
                    <div className="rec-meta">
                      <span className="rec-date">{f.resource_id}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {!scanResult && !loading && (
        <div className="rec-empty">
          <div className="rec-empty-icon">🕵️</div>
          <h4>Henüz tarama yapılmadı</h4>
          <p>Kurumunuzdaki kayıt dışı AI kullanımını tespit etmek için tarama başlatın.</p>
        </div>
      )}
    </div>
  )
}
