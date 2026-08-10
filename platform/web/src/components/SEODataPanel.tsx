import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getSEOConnections, getSEOAuthURL, disconnectSEO, getGA4Data, getSearchConsoleData } from '../api/client'
import type { SEOConnection, GA4DataRow, SearchConsoleRow } from '../api/client'

interface Props {
  workspaceId: string
  onStatus?: (msg: string | null) => void
}

export function SEODataPanel({ workspaceId, onStatus }: Props) {
  const { t } = useTranslation()
  const [seoConns, setSeoConns] = useState<SEOConnection[]>([])
  const [seoConnecting, setSeoConnecting] = useState<string | null>(null)
  const [ga4Data, setGA4Data] = useState<GA4DataRow[]>([])
  const [ga4DataLoading, setGA4DataLoading] = useState(false)
  const [scData, setSCData] = useState<SearchConsoleRow[]>([])
  const [scDataLoading, setSCDataLoading] = useState(false)

  const flashMsg = (msg: string, duration = 3000) => {
    if (onStatus) {
      onStatus(msg)
      setTimeout(() => onStatus(null), duration)
    }
  }

  useEffect(() => {
    getSEOConnections(workspaceId).then(setSeoConns).catch(() => {})
    // OAuth callback detection
    const params = new URLSearchParams(window.location.search)
    if (params.get('seo') === 'connected') {
      window.history.replaceState({}, '', window.location.pathname)
      getSEOConnections(workspaceId).then(setSeoConns).catch(() => {})
    }
  }, [workspaceId])

  // GA4 verisini bağlantı varsa yükle
  useEffect(() => {
    const ga4Conn = seoConns.find(c => c.platform === 'ga4')
    if (ga4Conn) {
      setGA4DataLoading(true)
      loadGA4Data()
    } else {
      setGA4Data([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seoConns, workspaceId])

  async function loadGA4Data() {
    try {
      setGA4DataLoading(true)
      const data = await getGA4Data(workspaceId)
      setGA4Data(data)
    } catch {
      flashMsg(t('seo.ga4_load_error'), 4000)
    } finally {
      setGA4DataLoading(false)
    }
  }

  async function loadSCData() {
    try {
      setSCDataLoading(true)
      const data = await getSearchConsoleData(workspaceId)
      setSCData(data)
    } catch {
      flashMsg(t('seo.sc_load_error'), 4000)
    } finally {
      setSCDataLoading(false)
    }
  }

  // Search Console verisini bağlantı varsa yükle
  useEffect(() => {
    const scConn = seoConns.find(c => c.platform === 'search_console')
    if (scConn) {
      setSCDataLoading(true)
      loadSCData()
    } else {
      setSCData([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seoConns, workspaceId])

  return (
    <>
      {/* SEO Entegrasyonları (FR-B8) — Canlı bağlantı yönetimi */}
      <div className="reports-section-title" style={{ marginTop: '2rem', marginBottom: '0.75rem' }}>
        <h3 style={{ margin: 0 }}>{t('seo.title')}</h3>
        <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '0.25rem 0 0' }}>
          {t('seo.desc')}
        </p>
      </div>

      <div className="reports-actions">
        {/* Search Console */}
        <div className="reports-action-card">
          <div className="reports-action-icon">🔍</div>
          <div className="reports-action-info">
            <h4>{t('seo.gsc_title')}</h4>
            <p>
              {seoConns.find(c => c.platform === 'search_console')
                ? t('seo.connected', { email: seoConns.find(c => c.platform === 'search_console')!.email })
                : t('seo.gsc_connect_desc')}
            </p>
          </div>
          {seoConns.find(c => c.platform === 'search_console') ? (
            <button
              className="reports-generate-btn"
              style={{ background: 'var(--danger-bg)', color: '#dc2626', borderColor: 'var(--danger-soft)' }}
              onClick={async () => {
                try {
                  await disconnectSEO(workspaceId, 'search_console')
                  flashMsg(t('seo.gsc_disconnected'), 3000)
                  const conns = await getSEOConnections(workspaceId)
                  setSeoConns(conns)
                } catch (err) {
                  flashMsg(t('seo.error', { error: err instanceof Error ? err.message : t('seo.disconnect_failed') }), 5000)
                }
              }}
            >
              {t('seo.disconnect')}
            </button>
          ) : (
            <button
              className="reports-generate-btn"
              onClick={async () => {
                try {
                  setSeoConnecting('search_console')
                  const { auth_url } = await getSEOAuthURL(workspaceId, 'search_console')
                  window.location.href = auth_url
                } catch (err) {
                  flashMsg(t('seo.error', { error: err instanceof Error ? err.message : t('seo.connect_failed') }), 5000)
                } finally {
                  setSeoConnecting(null)
                }
              }}
              disabled={seoConnecting === 'search_console'}
            >
              {seoConnecting === 'search_console' ? t('seo.redirecting') : t('seo.connect')}
            </button>
          )}
        </div>

        {/* GA4 */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📈</div>
          <div className="reports-action-info">
            <h4>{t('seo.ga4_title')}</h4>
            <p>
              {seoConns.find(c => c.platform === 'ga4')
                ? t('seo.connected', { email: seoConns.find(c => c.platform === 'ga4')!.email })
                : t('seo.ga4_connect_desc')}
            </p>
          </div>
          {seoConns.find(c => c.platform === 'ga4') ? (
            <button
              className="reports-generate-btn"
              style={{ background: 'var(--danger-bg)', color: '#dc2626', borderColor: 'var(--danger-soft)' }}
              onClick={async () => {
                try {
                  await disconnectSEO(workspaceId, 'ga4')
                  flashMsg(t('seo.ga4_disconnected'), 3000)
                  const conns = await getSEOConnections(workspaceId)
                  setSeoConns(conns)
                } catch (err) {
                  flashMsg(t('seo.error', { error: err instanceof Error ? err.message : t('seo.disconnect_failed') }), 5000)
                }
              }}
            >
              {t('seo.disconnect')}
            </button>
          ) : (
            <button
              className="reports-generate-btn"
              onClick={async () => {
                try {
                  setSeoConnecting('ga4')
                  const { auth_url } = await getSEOAuthURL(workspaceId, 'ga4')
                  window.location.href = auth_url
                } catch (err) {
                  flashMsg(t('seo.error', { error: err instanceof Error ? err.message : t('seo.connect_failed') }), 5000)
                } finally {
                  setSeoConnecting(null)
                }
              }}
              disabled={seoConnecting === 'ga4'}
            >
              {seoConnecting === 'ga4' ? t('seo.redirecting') : t('seo.connect')}
            </button>
          )}
        </div>
      </div>

      {/* GA4 Trafik Verileri */}
      {seoConns.find(c => c.platform === 'ga4') && (
        <div className="reports-section-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', flex: 1 }}>{t('seo.ga4_data_title')}</h4>
          <button
            onClick={loadGA4Data}
            disabled={ga4DataLoading}
            style={{
              background: 'none',
              border: '1px solid #e2e8f0',
              borderRadius: '6px',
              padding: '0.25rem 0.6rem',
              fontSize: '0.75rem',
              cursor: 'pointer',
              color: 'var(--text-muted)',
              transition: 'all 0.15s',
              display: 'flex',
              alignItems: 'center',
              gap: '0.25rem'
            }}
            onMouseEnter={e => { e.currentTarget.style.background = 'var(--surface-hover)'; e.currentTarget.style.color = 'var(--text)' }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = 'var(--text-muted)' }}
          >
            <span style={{ display: 'inline-block', transition: 'transform 0.3s', transform: ga4DataLoading ? 'rotate(180deg)' : 'none' }}>🔄</span>
            {ga4DataLoading ? t('common.loading') : t('common.refresh')}
          </button>
        </div>
      )}
      {ga4Data.length > 0 && (
        <div className="seo-data-grid" style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
          gap: '0.75rem',
          marginBottom: '1rem'
        }}>
          <div className="seo-metric-card" style={{
            background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
            borderRadius: '12px',
            padding: '1rem',
            color: '#fff',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.page_views')}</div>
            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
              {ga4Data[0].page_views.toLocaleString()}
            </div>
          </div>
          <div className="seo-metric-card" style={{
            background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
            borderRadius: '12px',
            padding: '1rem',
            color: '#fff',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.sessions')}</div>
            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
              {ga4Data[0].sessions.toLocaleString()}
            </div>
          </div>
          <div className="seo-metric-card" style={{
            background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
            borderRadius: '12px',
            padding: '1rem',
            color: '#fff',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.bounce_rate')}</div>
            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
              {(ga4Data[0].bounce_rate * 100).toFixed(1)}%
            </div>
          </div>
          <div className="seo-metric-card" style={{
            background: 'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)',
            borderRadius: '12px',
            padding: '1rem',
            color: '#fff',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.avg_session_duration')}</div>
            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
              {Math.round(ga4Data[0].avg_session_duration)}s
            </div>
          </div>
        </div>
      )}
      {ga4DataLoading && (
        <div style={{ textAlign: 'center', padding: '1rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          {t('seo.ga4_loading')}
        </div>
      )}

      {/* Search Console Verileri */}
      {seoConns.find(c => c.platform === 'search_console') && (
        <div className="reports-section-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', flex: 1 }}>{t('seo.sc_title')}</h4>
          <button
            onClick={loadSCData}
            disabled={scDataLoading}
            style={{
              background: 'none',
              border: '1px solid #e2e8f0',
              borderRadius: '6px',
              padding: '0.25rem 0.6rem',
              fontSize: '0.75rem',
              cursor: 'pointer',
              color: 'var(--text-muted)',
              transition: 'all 0.15s',
              display: 'flex',
              alignItems: 'center',
              gap: '0.25rem'
            }}
            onMouseEnter={e => { e.currentTarget.style.background = 'var(--surface-hover)'; e.currentTarget.style.color = 'var(--text)' }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = 'var(--text-muted)' }}
          >
            <span style={{ display: 'inline-block', transition: 'transform 0.3s', transform: scDataLoading ? 'rotate(180deg)' : 'none' }}>🔄</span>
            {scDataLoading ? t('common.loading') : t('common.refresh')}
          </button>
        </div>
      )}
      {scData.length > 0 && (() => {
        const totalClicks = scData.reduce((s, r) => s + r.clicks, 0)
        const totalImpressions = scData.reduce((s, r) => s + r.impressions, 0)
        const avgCTR = totalImpressions > 0 ? (totalClicks / totalImpressions) * 100 : 0
        const avgPos = scData.reduce((s, r) => s + r.avg_position * r.impressions, 0) / (totalImpressions || 1)
        const topQueries = [...scData].sort((a, b) => b.clicks - a.clicks).slice(0, 10)

        return (
          <>
            <div className="seo-data-grid" style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: '0.75rem',
              marginBottom: '1rem'
            }}>
              <div className="seo-metric-card" style={{
                background: 'linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%)',
                borderRadius: '12px',
                padding: '1rem',
                color: '#fff',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.total_clicks')}</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                  {totalClicks.toLocaleString()}
                </div>
              </div>
              <div className="seo-metric-card" style={{
                background: 'linear-gradient(135deg, #14b8a6 0%, #0d9488 100%)',
                borderRadius: '12px',
                padding: '1rem',
                color: '#fff',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.total_impressions')}</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                  {totalImpressions.toLocaleString()}
                </div>
              </div>
              <div className="seo-metric-card" style={{
                background: 'linear-gradient(135deg, #f97316 0%, #ea580c 100%)',
                borderRadius: '12px',
                padding: '1rem',
                color: '#fff',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.avg_ctr')}</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                  {avgCTR.toFixed(2)}%
                </div>
              </div>
              <div className="seo-metric-card" style={{
                background: 'linear-gradient(135deg, #ec4899 0%, #db2777 100%)',
                borderRadius: '12px',
                padding: '1rem',
                color: '#fff',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>{t('seo.avg_position')}</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                  {avgPos.toFixed(1)}
                </div>
              </div>
            </div>

            {/* En çok tıklanan sorgular */}
            <div style={{
              background: 'var(--surface)',
              borderRadius: '12px',
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
              marginBottom: '1rem'
            }}>
              <div style={{
                padding: '0.6rem 1rem',
                fontSize: '0.8rem',
                fontWeight: 600,
                color: 'var(--text-secondary)',
                borderBottom: '1px solid #e2e8f0',
                background: 'var(--surface-2)'
              }}>
                {t('seo.top_queries')}
              </div>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem' }}>
                <thead>
                  <tr style={{ background: 'var(--surface-2)', color: 'var(--text-muted)' }}>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left', fontWeight: 500 }}>{t('seo.table_query')}</th>
                    <th style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 500 }}>{t('seo.table_clicks')}</th>
                    <th style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 500 }}>{t('seo.table_impressions')}</th>
                    <th style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 500 }}>{t('seo.table_ctr')}</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'right', fontWeight: 500 }}>{t('seo.table_position')}</th>
                  </tr>
                </thead>
                <tbody>
                  {topQueries.map((row, i) => (
                    <tr key={i} style={{
                      borderTop: '1px solid #f1f5f9',
                      transition: 'background 0.15s'
                    }} onMouseEnter={e => (e.currentTarget.style.background = 'var(--surface-2)')} onMouseLeave={e => (e.currentTarget.style.background = '')}>
                      <td style={{ padding: '0.5rem 1rem', color: 'var(--text)', maxWidth: '250px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {row.query || t('seo.empty_query')}
                      </td>
                      <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 600, color: '#0ea5e9' }}>
                        {row.clicks.toLocaleString()}
                      </td>
                      <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', color: 'var(--text-secondary)' }}>
                        {row.impressions.toLocaleString()}
                      </td>
                      <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', color: 'var(--text-secondary)' }}>
                        {(row.ctr * 100).toFixed(1)}%
                      </td>
                      <td style={{ padding: '0.5rem 1rem', textAlign: 'right', color: 'var(--text-secondary)' }}>
                        {row.avg_position.toFixed(1)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )
      })()}
      {scDataLoading && (
        <div style={{ textAlign: 'center', padding: '1rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          {t('seo.sc_loading')}
        </div>
      )}
    </>
  )
}
