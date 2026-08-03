import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getComplianceReport, getComplianceSOC2, getComplianceEvidence } from '../api/client'

interface EvidenceItem {
  id: string
  framework: string
  name: string
  status: string
  updated_at: string
}

const statusColors: Record<string, string> = {
  compliant: '#22c55e',
  non_compliant: '#ef4444',
  in_progress: '#eab308',
  not_applicable: '#94a3b8',
}

const statusLabels: Record<string, string> = {
  compliant: 'Uyumlu',
  non_compliant: 'Uyumsuz',
  in_progress: 'Devam Ediyor',
  not_applicable: 'Uygulanamaz',
}

export function CompliancePanel() {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [report, setReport] = useState<Record<string, unknown> | null>(null)
  const [frameworks, setFrameworks] = useState<string[]>([])
  const [soc2, setSoc2] = useState<Record<string, unknown> | null>(null)
  const [evidence, setEvidence] = useState<EvidenceItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    loadData()
  }, [])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const [reportData, soc2Data, evidenceData] = await Promise.all([
        getComplianceReport(),
        getComplianceSOC2(),
        getComplianceEvidence(),
      ])
      setReport(reportData.report)
      setFrameworks(reportData.frameworks)
      setSoc2(soc2Data)
      setEvidence(evidenceData.evidence)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('compliance.load_error'))
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <div className="dashboard-loading">{t('compliance.loading')}</div>
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>{t('dashboard.retry')}</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>{t('compliance.title')}</h3>
        <p className="monitoring-desc">{t('compliance.desc')}</p>
      </div>

      {/* Report Overview */}
      {report && Object.keys(report).length > 0 && (
        <div className="monitoring-section">
          <h4>{t('compliance.report_overview')}</h4>
          <div className="monitoring-quick-stats" style={{ marginBottom: '1rem' }}>
            {Object.entries(report).map(([key, value]) => (
              <div key={key} className="quick-stat">
                <span className="quick-stat-label" style={{ textTransform: 'capitalize' }}>
                  {key.replace(/_/g, ' ')}
                </span>
                <span style={{ fontSize: '1rem', fontWeight: 600 }}>
                  {typeof value === 'string' || typeof value === 'number' ? String(value) : JSON.stringify(value)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Framework Badges */}
      {frameworks.length > 0 && (
        <div className="monitoring-quick-stats" style={{ marginBottom: '1.5rem' }}>
          <div className="quick-stat">
            <span className="quick-stat-label">{t('compliance.frameworks')}</span>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginTop: '0.5rem' }}>
              {frameworks.map((fw) => (
                <span
                  key={fw}
                  style={{
                    display: 'inline-block',
                    padding: '0.25rem 0.75rem',
                    borderRadius: '999px',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                    background: '#eef2ff',
                    color: '#6366f1',
                    border: '1px solid #c7d2fe',
                  }}
                >
                  {fw}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* SOC2 Status */}
      {soc2 && (
        <div className="monitoring-section">
          <h4>{t('compliance.soc2')}</h4>
          <div className="monitoring-quick-stats" style={{ marginBottom: '1rem' }}>
            {Object.entries(soc2).map(([key, value]) => (
              <div key={key} className="quick-stat">
                <span className="quick-stat-label" style={{ textTransform: 'capitalize' }}>
                  {key.replace(/_/g, ' ')}
                </span>
                <span style={{ fontSize: '1rem', fontWeight: 600 }}>
                  {typeof value === 'string' ? value : JSON.stringify(value)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Evidence Table */}
      <div className="monitoring-section">
        <h4>{t('compliance.evidence')}</h4>
        {evidence.length === 0 ? (
          <div className="rec-empty">
            <div className="rec-empty-icon">📋</div>
            <h4>{t('compliance.evidence_empty')}</h4>
            <p>{t('compliance.evidence_empty_desc')}</p>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #e2e8f0', textAlign: 'left' }}>
                <th style={{ padding: '0.5rem' }}>{t('compliance.evidence_name')}</th>
                <th style={{ padding: '0.5rem' }}>{t('compliance.evidence_framework')}</th>
                <th style={{ padding: '0.5rem' }}>{t('compliance.evidence_status')}</th>
                <th style={{ padding: '0.5rem' }}>{t('compliance.evidence_updated')}</th>
              </tr>
            </thead>
            <tbody>
              {evidence.map((item) => (
                <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '0.5rem', fontWeight: 600 }}>{item.name}</td>
                  <td style={{ padding: '0.5rem', color: '#64748b' }}>{item.framework}</td>
                  <td style={{ padding: '0.5rem' }}>
                    <span
                      style={{
                        display: 'inline-block',
                        padding: '0.15rem 0.5rem',
                        borderRadius: '999px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        background: statusColors[item.status] ? `${statusColors[item.status]}20` : '#f1f5f9',
                        color: statusColors[item.status] || '#64748b',
                      }}
                    >
                      {statusLabels[item.status] || item.status}
                    </span>
                  </td>
                  <td style={{ padding: '0.5rem', color: '#94a3b8', fontSize: '0.8rem' }}>
                    {new Date(item.updated_at).toLocaleDateString(dateLocale, {
                      day: 'numeric',
                      month: 'short',
                      year: 'numeric',
                    })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default CompliancePanel
