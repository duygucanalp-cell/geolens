import { useEffect, useState } from 'react'
import { getSEOConnections, getSEOAuthURL, disconnectSEO, getGA4Data, getSearchConsoleData } from '../api/client'
import type { SEOConnection, GA4DataRow, SearchConsoleRow } from '../api/client'

interface Props {
  workspaceId: string
  onStatus?: (msg: string | null) => void
}

export function SEODataPanel({ workspaceId, onStatus }: Props) {
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
      flashMsg('GA4 verisi yüklenemedi', 4000)
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
      flashMsg('Search Console verisi yüklenemedi', 4000)
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
        <h3 style={{ margin: 0 }}>🔗 SEO Entegrasyonları</h3>
        <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.25rem 0 0' }}>
          Google Search Console ve GA4 bağlantısı ile AI görünürlük verilerinizi organik arama performansınızla karşılaştırın.
        </p>
      </div>

      <div className="reports-actions">
        {/* Search Console */}
        <div className="reports-action-card">
          <div className="reports-action-icon">🔍</div>
          <div className="reports-action-info">
            <h4>Google Search Console</h4>
            <p>
              {seoConns.find(c => c.platform === 'search_console')
                ? `Bağlı: ${seoConns.find(c => c.platform === 'search_console')!.email}`
                : 'Organik arama verilerinizi AI görünürlüğünüzle karşılaştırmak için Google Search Console hesabınızı bağlayın.'}
            </p>
          </div>
          {seoConns.find(c => c.platform === 'search_console') ? (
            <button
              className="reports-generate-btn"
              style={{ background: '#fee2e2', color: '#dc2626', borderColor: '#fecaca' }}
              onClick={async () => {
                try {
                  await disconnectSEO(workspaceId, 'search_console')
                  flashMsg('Search Console bağlantısı kaldırıldı', 3000)
                  const conns = await getSEOConnections(workspaceId)
                  setSeoConns(conns)
                } catch (err) {
                  flashMsg(`Hata: ${err instanceof Error ? err.message : 'Bağlantı kaldırılamadı'}`, 5000)
                }
              }}
            >
              Bağlantıyı Kes
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
                  flashMsg(`Hata: ${err instanceof Error ? err.message : 'Bağlantı başlatılamadı'}`, 5000)
                } finally {
                  setSeoConnecting(null)
                }
              }}
              disabled={seoConnecting === 'search_console'}
            >
              {seoConnecting === 'search_console' ? 'Yönlendiriliyor...' : 'Google ile Bağlan'}
            </button>
          )}
        </div>

        {/* GA4 */}
        <div className="reports-action-card">
          <div className="reports-action-icon">📈</div>
          <div className="reports-action-info">
            <h4>Google Analytics 4</h4>
            <p>
              {seoConns.find(c => c.platform === 'ga4')
                ? `Bağlı: ${seoConns.find(c => c.platform === 'ga4')!.email}`
                : 'Web sitesi trafik verilerinizi AI görünürlük metriklerinizle ilişkilendirmek için GA4 hesabınızı bağlayın.'}
            </p>
          </div>
          {seoConns.find(c => c.platform === 'ga4') ? (
            <button
              className="reports-generate-btn"
              style={{ background: '#fee2e2', color: '#dc2626', borderColor: '#fecaca' }}
              onClick={async () => {
                try {
                  await disconnectSEO(workspaceId, 'ga4')
                  flashMsg('GA4 bağlantısı kaldırıldı', 3000)
                  const conns = await getSEOConnections(workspaceId)
                  setSeoConns(conns)
                } catch (err) {
                  flashMsg(`Hata: ${err instanceof Error ? err.message : 'Bağlantı kaldırılamadı'}`, 5000)
                }
              }}
            >
              Bağlantıyı Kes
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
                  flashMsg(`Hata: ${err instanceof Error ? err.message : 'Bağlantı başlatılamadı'}`, 5000)
                } finally {
                  setSeoConnecting(null)
                }
              }}
              disabled={seoConnecting === 'ga4'}
            >
              {seoConnecting === 'ga4' ? 'Yönlendiriliyor...' : 'Google ile Bağlan'}
            </button>
          )}
        </div>
      </div>

      {/* GA4 Trafik Verileri */}
      {seoConns.find(c => c.platform === 'ga4') && (
        <div className="reports-section-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', flex: 1 }}>📊 GA4 Trafik Verileri (Son 7 Gün)</h4>
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
              color: '#64748b',
              transition: 'all 0.15s',
              display: 'flex',
              alignItems: 'center',
              gap: '0.25rem'
            }}
            onMouseEnter={e => { e.currentTarget.style.background = '#f1f5f9'; e.currentTarget.style.color = '#1e293b' }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = '#64748b' }}
          >
            <span style={{ display: 'inline-block', transition: 'transform 0.3s', transform: ga4DataLoading ? 'rotate(180deg)' : 'none' }}>🔄</span>
            {ga4DataLoading ? 'Yükleniyor...' : 'Yenile'}
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
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Sayfa Görüntüleme</div>
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
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Oturumlar</div>
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
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Hemen Çıkma</div>
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
            <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Ort. Oturum Süresi</div>
            <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
              {Math.round(ga4Data[0].avg_session_duration)}s
            </div>
          </div>
        </div>
      )}
      {ga4DataLoading && (
        <div style={{ textAlign: 'center', padding: '1rem', color: '#64748b', fontSize: '0.85rem' }}>
          GA4 verileri yükleniyor...
        </div>
      )}

      {/* Search Console Verileri */}
      {seoConns.find(c => c.platform === 'search_console') && (
        <div className="reports-section-title" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', flex: 1 }}>🔍 Search Console Performansı (Son 7 Gün)</h4>
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
              color: '#64748b',
              transition: 'all 0.15s',
              display: 'flex',
              alignItems: 'center',
              gap: '0.25rem'
            }}
            onMouseEnter={e => { e.currentTarget.style.background = '#f1f5f9'; e.currentTarget.style.color = '#1e293b' }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = '#64748b' }}
          >
            <span style={{ display: 'inline-block', transition: 'transform 0.3s', transform: scDataLoading ? 'rotate(180deg)' : 'none' }}>🔄</span>
            {scDataLoading ? 'Yükleniyor...' : 'Yenile'}
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
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Toplam Tıklama</div>
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
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Toplam Gösterim</div>
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
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Ort. TO</div>
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
                <div style={{ fontSize: '0.7rem', opacity: 0.8, marginBottom: '0.25rem' }}>Ort. Pozisyon</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                  {avgPos.toFixed(1)}
                </div>
              </div>
            </div>

            {/* En çok tıklanan sorgular */}
            <div style={{
              background: '#fff',
              borderRadius: '12px',
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
              marginBottom: '1rem'
            }}>
              <div style={{
                padding: '0.6rem 1rem',
                fontSize: '0.8rem',
                fontWeight: 600,
                color: '#475569',
                borderBottom: '1px solid #e2e8f0',
                background: '#f8fafc'
              }}>
                En Çok Tıklanan Sorgular
              </div>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem' }}>
                <thead>
                  <tr style={{ background: '#f8fafc', color: '#64748b' }}>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left', fontWeight: 500 }}>Sorgu</th>
                    <th style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 500 }}>Tıklama</th>
                    <th style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 500 }}>Gösterim</th>
                    <th style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 500 }}>TO</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'right', fontWeight: 500 }}>Pozisyon</th>
                  </tr>
                </thead>
                <tbody>
                  {topQueries.map((row, i) => (
                    <tr key={i} style={{
                      borderTop: '1px solid #f1f5f9',
                      transition: 'background 0.15s'
                    }} onMouseEnter={e => (e.currentTarget.style.background = '#f8fafc')} onMouseLeave={e => (e.currentTarget.style.background = '')}>
                      <td style={{ padding: '0.5rem 1rem', color: '#1e293b', maxWidth: '250px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {row.query || '(boş)'}
                      </td>
                      <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', fontWeight: 600, color: '#0ea5e9' }}>
                        {row.clicks.toLocaleString()}
                      </td>
                      <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', color: '#475569' }}>
                        {row.impressions.toLocaleString()}
                      </td>
                      <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', color: '#475569' }}>
                        {(row.ctr * 100).toFixed(1)}%
                      </td>
                      <td style={{ padding: '0.5rem 1rem', textAlign: 'right', color: '#475569' }}>
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
        <div style={{ textAlign: 'center', padding: '1rem', color: '#64748b', fontSize: '0.85rem' }}>
          Search Console verileri yükleniyor...
        </div>
      )}
    </>
  )
}
