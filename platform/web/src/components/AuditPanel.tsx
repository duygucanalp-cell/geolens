import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import type { AuditResult, Brand } from '../types'
import { triggerAudit } from '../api/client'

interface AuditPanelProps {
  workspaceId: string
  brands: Brand[]
}

const severityColors: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#22c55e',
  info: '#6366f1',
}

export function AuditPanel({ workspaceId, brands }: AuditPanelProps) {
  const { t } = useTranslation()
  const [selectedBrand, setSelectedBrand] = useState('')
  const [result, setResult] = useState<AuditResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleAudit() {
    if (!selectedBrand) return
    const brand = brands.find((b) => b.id === selectedBrand)
    if (!brand) return

    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const data = await triggerAudit(workspaceId, brand.id, brand.name, brand.website_url)
      setResult(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('audit.failed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="audit-panel">
      <h3>{t('audit.title')}</h3>
      <div className="audit-controls">
        <select
          value={selectedBrand}
          onChange={(e) => setSelectedBrand(e.target.value)}
          className="audit-select"
        >
          <option value="">Marka seçin</option>
          {brands.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
        <button
          onClick={handleAudit}
          disabled={!selectedBrand || loading}
          className="audit-btn"
        >
          {loading ? t('audit.running') : t('audit.start')}
        </button>
      </div>

      {error && <div className="audit-error">{error}</div>}

      {result && (
        <div className="audit-result">
          <div className="audit-score">
            <span className="audit-score-value">{Math.round(result.overall_score)}</span>
            <span className="audit-score-label">{t('audit.score')}</span>
          </div>

          <div className="audit-checks">
            <div className="audit-check-card">
              <span className="check-status">
                {result.robots_txt?.allows_ai_bots ? '✅' : '❌'}
              </span>
              <div>
                <strong>{t('audit.robots_txt')}</strong>
                <p>
                  {result.robots_txt?.exists
                    ? result.robots_txt.disallowed_all
                      ? t('audit.robots_blocked')
                      : t('audit.robots_ai_allowed')
                    : t('audit.robots_not_found')}
                </p>
              </div>
            </div>

            <div className="audit-check-card">
              <span className="check-status">
                {result.bot_access?.accessible ? '✅' : '❌'}
              </span>
              <div>
                <strong>{t('audit.bot_access')}</strong>
                <p>
                  {result.bot_access?.accessible
                    ? t('audit.bot_accessible', { code: result.bot_access.status_code, time: result.bot_access.response_time_ms })
                    : t('audit.bot_inaccessible')}
                </p>
              </div>
            </div>

            <div className="audit-check-card">
              <span className="check-status">
                {result.ssr?.has_meta_tags || result.ssr?.has_structured_data ? '✅' : '⚠️'}
              </span>
              <div>
                <strong>{t('audit.ssr_signals')}</strong>
                <p>
                  {[
                    result.ssr?.has_meta_tags ? 'Meta' : null,
                    result.ssr?.has_og_tags ? 'OG' : null,
                    result.ssr?.has_structured_data ? 'LD+JSON' : null,
                  ]
                    .filter(Boolean)
                    .join(', ') || t('audit.ssr_none')}
                </p>
              </div>
            </div>

            <div className="audit-check-card">
              <span className="check-status">
                {result.ssrf?.has_cloudflare || result.ssrf?.csp_present ? '✅' : '⚠️'}
              </span>
              <div>
                <strong>{t('audit.security')}</strong>
                <p>
                  {[
                    result.ssrf?.has_cloudflare ? 'Cloudflare' : null,
                    result.ssrf?.csp_present ? 'CSP' : null,
                    result.ssrf?.has_aws_security_headers ? 'AWS' : null,
                  ]
                    .filter(Boolean)
                    .join(', ') || t('audit.security_none')}
                </p>
              </div>
            </div>
          </div>

          {result.issues.length > 0 && (
            <div className="audit-issues">
              <h4>{t('audit.findings', { count: result.issues.length })}</h4>
              {result.issues.map((issue, i) => (
                <div key={i} className="audit-issue" style={{ borderLeftColor: severityColors[issue.severity] || '#94a3b8' }}>
                  <div className="issue-header">
                    <span className="issue-severity" style={{ color: severityColors[issue.severity] }}>
                      {issue.severity.toUpperCase()}
                    </span>
                    <span className="issue-category">{issue.category}</span>
                  </div>
                  <p className="issue-title">{issue.title}</p>
                  <p className="issue-detail">{issue.detail}</p>
                  {issue.recommendation && (
                    <p className="issue-recommendation">💡 {issue.recommendation}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
