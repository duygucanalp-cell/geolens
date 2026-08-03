import { useEffect, useState } from 'react'
import { getBrands, getAuditFindings, triggerAudit } from '../api/client'
import type { AuditFindingsCatalog } from '../api/client'
import type { Brand } from '../types'

interface Props {
  workspaceId: string
  onStatus?: (msg: string | null) => void
}

const SEVERITY_LABELS: Record<string, string> = {
  critical: 'Kritik',
  high: 'Yüksek',
  medium: 'Orta',
  low: 'Düşük',
}

const SEVERITY_COLORS: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#22c55e',
}

const SEVERITY_BG: Record<string, string> = {
  critical: '#fef2f2',
  high: '#fff7ed',
  medium: '#fefce8',
  low: '#f0fdf4',
}

const CATEGORY_LABELS: Record<string, string> = {
  robots_txt: 'robots.txt Analizi',
  bot_access: 'Bot Erişim Testi',
  ssr: 'SSR & Meta Etiketler',
  ssrf: 'Güvenlik Başlıkları',
}

const CATEGORY_ICONS: Record<string, string> = {
  robots_txt: '🤖',
  bot_access: '🔍',
  ssr: '📄',
  ssrf: '🛡️',
}

function flashMsg(onStatus: ((msg: string | null) => void) | undefined, msg: string, duration = 3000) {
  if (onStatus) {
    onStatus(msg)
    setTimeout(() => onStatus(null), duration)
  }
}

export function SiteAuditPanel({ workspaceId, onStatus }: Props) {
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
      flashMsg(onStatus, 'Site denetimi tamamlandı', 3000)
      // Reload findings
      const data = await getAuditFindings(workspaceId, selectedBrandId)
      setFindings(data)
    } catch (err) {
      flashMsg(onStatus, `Hata: ${err instanceof Error ? err.message : 'Denetim başarısız'}`, 5000)
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
        <h3 style={{ margin: 0 }}>🔍 Site Denetim Raporu</h3>
        <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.25rem 0 0' }}>
          AI bot erişilebilirlik, SSR, güvenlik başlıkları ve robots.txt analizi.
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
            color: '#1e293b',
            background: '#fff',
          }}
        >
          <option value="">Marka seçin...</option>
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
          {auditing ? 'Denetleniyor...' : 'Denetim Başlat'}
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b', fontSize: '0.85rem' }}>
          Bulgular yükleniyor...
        </div>
      )}

      {/* No findings (brand selected but no audit run yet) */}
      {!loading && selectedBrandId && !findings && (
        <div className="reports-action-card" style={{ background: '#f8fafc' }}>
          <div className="reports-action-icon">📋</div>
          <div className="reports-action-info">
            <h4>Henüz Denetim Yapılmamış</h4>
            <p>Bu marka için henüz site denetimi yapılmamış. "Denetim Başlat" butonuna tıklayarak analizi başlatın.</p>
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
              <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Genel Skor</div>
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
                  {SEVERITY_LABELS[sev]}
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
                <h4>{CATEGORY_LABELS[cat]}</h4>
                {hasIssues(cat) ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.4rem' }}>
                    {findings.catalog[cat]!.map((item, i) => (
                      <div key={i} style={{
                        background: SEVERITY_BG[item.severity] || '#f8fafc',
                        borderRadius: '8px',
                        padding: '0.6rem 0.8rem',
                        border: `1px solid ${SEVERITY_COLORS[item.severity] || '#e2e8f0'}33`,
                      }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.2rem' }}>
                          <span style={{
                            background: SEVERITY_COLORS[item.severity] || '#64748b',
                            color: '#fff',
                            fontSize: '0.65rem',
                            padding: '0.1rem 0.4rem',
                            borderRadius: '4px',
                            fontWeight: 600,
                          }}>
                            {SEVERITY_LABELS[item.severity] || item.severity}
                          </span>
                          <span style={{ fontWeight: 600, fontSize: '0.85rem', color: '#1e293b' }}>
                            {item.title}
                          </span>
                        </div>
                        <p style={{ margin: '0.2rem 0', fontSize: '0.78rem', color: '#475569' }}>
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
                    ✓ Sorun bulunamadı
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
