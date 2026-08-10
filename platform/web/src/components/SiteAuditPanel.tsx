import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getBrands, getAuditFindings, triggerAudit } from '../api/client'
import type { AuditFindingsCatalog } from '../api/client'
import type { Brand } from '../types'

interface Props {
  workspaceId: string
  onStatus?: (msg: string | null) => void
}

const SEVERITY_COLORS: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#22c55e',
}

const SEVERITY_BG: Record<string, string> = {
  critical: 'var(--danger-bg)',
  high: '#fff7ed',
  medium: '#fefce8',
  low: 'var(--success-bg)',
}

const CATEGORY_ICONS: Record<string, string> = {
  robots_txt: '🤖',
  bot_access: '🔍',
  ssr: '📄',
  ssrf: '🛡️',
}

const SEVERITY_KEYS: Record<string, string> = {
  critical: 'severity.critical',
  high: 'severity.high',
  medium: 'severity.medium',
  low: 'severity.low',
}

const CATEGORY_KEYS: Record<string, string> = {
  robots_txt: 'siteaudit.category_robots_txt',
  bot_access: 'siteaudit.category_bot_access',
  ssr: 'siteaudit.category_ssr',
  ssrf: 'siteaudit.category_ssrf',
}

function flashMsg(onStatus: ((msg: string | null) => void) | undefined, msg: string, duration = 3000) {
  if (onStatus) {
    onStatus(msg)
    setTimeout(() => onStatus(null), duration)
  }
}

export function SiteAuditPanel({ workspaceId, onStatus }: Props) {
  const { t } = useTranslation()
  const [brands, setBrands] = useState<Brand[]>([])
  const [selectedBrandId, setSelectedBrandId] = useState('')
  const [findings, setFindings] = useState<AuditFindingsCatalog | null>(null)
  const [loading, setLoading] = useState(false)
  const [auditing, setAuditing] = useState(false)

  useEffect(() => {
    getBrands(workspaceId).then(setBrands).catch(() => {})
  }, [workspaceId])

  useEffect(() => {
    if (!selectedBrandId) {
      setFindings(null)
      return
    }
    setLoading(true)
    getAuditFindings(workspaceId, selectedBrandId)
      .then(setFindings)
      .catch(() => setFindings(null))
      .finally(() => setLoading(false))
  }, [selectedBrandId, workspaceId])

  async function handleRunAudit() {
    const brand = brands.find(b => b.id === selectedBrandId)
    if (!brand) return

    setAuditing(true)
    try {
      await triggerAudit(workspaceId, brand.id, brand.name, brand.website_url)
      flashMsg(onStatus, t('siteaudit.completed'), 3000)
      // Reload findings
      const data = await getAuditFindings(workspaceId, selectedBrandId)
      setFindings(data)
    } catch (err) {
      flashMsg(onStatus, t('siteaudit.error', { error: err instanceof Error ? err.message : t('siteaudit.failed') }), 5000)
    } finally {
      setAuditing(false)
    }
  }

  const severityCount = (severity: 'critical' | 'high' | 'medium' | 'low') =>
    findings?.summary ? findings.summary[severity] || 0 : 0

  type AuditCategory = 'robots_txt' | 'bot_access' | 'ssr' | 'ssrf'
  const hasIssues = (cat: AuditCategory) =>
    findings?.catalog?.[cat] && findings.catalog[cat]!.length > 0

  return (
    <div className="site-audit-panel">
      <div className="reports-section-title" style={{ marginTop: '2rem', marginBottom: '0.75rem' }}>
        <h3 style={{ margin: 0 }}>{t('siteaudit.title')}</h3>
        <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '0.25rem 0 0' }}>
          {t('siteaudit.desc')}
        </p>
      </div>

      {/* Brand Selector */}
      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '1.25rem' }}>
        <select
          value={selectedBrandId}
          onChange={e => setSelectedBrandId(e.target.value)}
          style={{
            flex: 1,
            padding: '0.5rem 0.75rem',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            fontSize: '0.85rem',
            color: 'var(--text)',
            background: 'var(--surface)',
          }}
        >
          <option value="">{t('siteaudit.select_brand')}</option>
          {brands.map(b => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>

        <button
          onClick={handleRunAudit}
          disabled={!selectedBrandId || auditing}
          className="reports-generate-btn"
          style={{ whiteSpace: 'nowrap' }}
        >
          {auditing ? t('siteaudit.running') : t('siteaudit.run')}
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          {t('siteaudit.loading')}
        </div>
      )}

      {/* No findings (brand selected but no audit run yet) */}
      {!loading && selectedBrandId && !findings && (
        <div className="reports-action-card" style={{ background: 'var(--surface-2)' }}>
          <div className="reports-action-icon">📋</div>
          <div className="reports-action-info">
            <h4>{t('siteaudit.not_run_title')}</h4>
            <p>{t('siteaudit.not_run_desc')}</p>
          </div>
        </div>
      )}

      {/* Findings Display */}
      {!loading && findings && (
        <>
          {/* Score & Summary */}
          <div className="seo-data-grid" style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
            gap: '0.75rem',
            marginBottom: '1rem'
          }}>
            <div className="seo-metric-card" style={{
              background: findings.overall_score >= 80
                ? 'linear-gradient(135deg, #10b981 0%, #059669 100%)'
                : findings.overall_score >= 50
                  ? 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)'
                  : 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
              borderRadius: '12px',
              padding: '1rem',
              color: '#fff',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('siteaudit.overall_score')}</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                {findings.overall_score.toFixed(0)}
              </div>
            </div>
            {(['critical', 'high', 'medium', 'low'] as const).map(sev => (
              <div key={sev} className="seo-metric-card" style={{
                background: SEVERITY_COLORS[sev],
                borderRadius: '12px',
                padding: '1rem',
                color: '#fff',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>
                  {t(SEVERITY_KEYS[sev])}
                </div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                  {severityCount(sev)}
                </div>
              </div>
            ))}
          </div>

          {/* Category Cards */}
          {(['robots_txt', 'bot_access', 'ssr', 'ssrf'] as const).map(cat => (
            <div key={cat} className="reports-action-card" style={{
              marginBottom: '0.75rem',
              borderLeft: `4px solid ${hasIssues(cat) ? '#f59e0b' : '#22c55e'}`,
            }}>
              <div className="reports-action-icon">{CATEGORY_ICONS[cat]}</div>
              <div className="reports-action-info">
                <h4>{t(CATEGORY_KEYS[cat])}</h4>
                {hasIssues(cat) ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.4rem' }}>
                    {findings.catalog[cat]!.map((item, i) => (
                      <div key={i} style={{
                        background: SEVERITY_BG[item.severity] || 'var(--surface-2)',
                        borderRadius: '8px',
                        padding: '0.6rem 0.8rem',
                        border: `1px solid ${SEVERITY_COLORS[item.severity] || 'var(--border)'}33`,
                      }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.2rem' }}>
                          <span style={{
                            background: SEVERITY_COLORS[item.severity] || 'var(--text-muted)',
                            color: '#fff',
                            fontSize: '0.65rem',
                            padding: '0.1rem 0.4rem',
                            borderRadius: '4px',
                            fontWeight: 600,
                          }}>
                            {SEVERITY_KEYS[item.severity] ? t(SEVERITY_KEYS[item.severity]) : item.severity}
                          </span>
                          <span style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text)' }}>
                            {item.title}
                          </span>
                        </div>
                        <p style={{ margin: '0.2rem 0', fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                          {item.detail}
                        </p>
                        {item.recommendation && (
                          <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#0891b2', fontStyle: 'italic' }}>
                            💡 {item.recommendation}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p style={{ fontSize: '0.8rem', color: '#22c55e' }}>
                    {t('siteaudit.no_issues')}
                  </p>
                )}
              </div>
            </div>
          ))}
        </>
      )}
    </div>
  )
}
