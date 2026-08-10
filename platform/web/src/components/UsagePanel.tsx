import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getUsageMetrics, getUsageSummary } from '../api/client'
import type { UsageMetric, UsageSummary } from '../types'

interface Props {
  workspaceId: string
}

type Period = '1d' | '7d' | '30d' | '90d'

export function UsagePanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const [metrics, setMetrics] = useState<UsageMetric[]>([])
  const [summary, setSummary] = useState<UsageSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Period>('7d')

  useEffect(() => { loadData() }, [period])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const [m, s] = await Promise.all([getUsageMetrics(), getUsageSummary(period)])
      setMetrics(m)
      setSummary(s)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('usage.load_error'))
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <PanelSkeleton message={t('usage.loading')} />
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>{t('common.retry')}</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>{t('usage.title')}</h3>
        <p className="monitoring-desc">{t('usage.desc')}</p>
      </div>

      <div className="dashboard-filters">
        <select value={period} onChange={(e) => setPeriod(e.target.value as Period)} className="filter-select">
          <option value="1d">{t('period.24h')}</option>
          <option value="7d">{t('period.7d')}</option>
          <option value="30d">{t('period.30d')}</option>
          <option value="90d">{t('period.90d')}</option>
        </select>
        <button className="refresh-btn" onClick={loadData}>{t('common.refresh')}</button>
      </div>

      {summary && (
        <div className="monitoring-quick-stats" style={{ marginBottom: '1.5rem' }}>
          <div className="quick-stat">
            <span className="quick-stat-label">{t('usage.total_requests')}</span>
            <span style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--accent)' }}>
              {summary.total_requests}
            </span>
          </div>
          <div className="quick-stat">
            <span className="quick-stat-label">{t('usage.error_rate')}</span>
            <span style={{
              fontSize: '1.5rem', fontWeight: 700,
              color: summary.error_rate_pct > 5 ? '#ef4444' : summary.error_rate_pct > 1 ? '#eab308' : '#22c55e',
            }}>
              %{summary.error_rate_pct.toFixed(1)}
            </span>
          </div>
          <div className="quick-stat">
            <span className="quick-stat-label">{t('usage.avg_latency')}</span>
            <span style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--accent)' }}>
              {summary.avg_latency_ms.toFixed(0)}ms
            </span>
          </div>
        </div>
      )}

      {summary && summary.top_endpoints.length > 0 && (
        <div style={{ marginBottom: '1.5rem' }}>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
            {t('usage.top_endpoints')}
          </h4>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '0.5rem' }}>{t('usage.table_endpoint')}</th>
                <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('usage.table_hits')}</th>
                <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('usage.table_latency')}</th>
              </tr>
            </thead>
            <tbody>
              {summary.top_endpoints.map((ep, i) => (
                <tr key={i} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.8rem' }}>{ep.endpoint}</td>
                  <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 600 }}>{ep.hits}</td>
                  <td style={{ padding: '0.5rem', textAlign: 'right' }}>{ep.avg_latency_ms.toFixed(0)}ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {metrics.length > 0 ? (
        <div>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
            {t('usage.recent_metrics')}
          </h4>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '0.5rem' }}>{t('usage.table_endpoint')}</th>
                <th style={{ padding: '0.5rem' }}>{t('usage.table_method')}</th>
                <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('usage.table_status')}</th>
                <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('usage.table_latency')}</th>
                <th style={{ padding: '0.5rem' }}>{t('usage.table_date')}</th>
              </tr>
            </thead>
            <tbody>
              {metrics.map((m) => (
                <tr key={m.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '0.5rem', fontFamily: 'monospace', fontSize: '0.8rem' }}>{m.endpoint}</td>
                  <td style={{ padding: '0.5rem', fontWeight: 600 }}>{m.method}</td>
                  <td style={{
                    padding: '0.5rem', textAlign: 'right',
                    color: m.status_code >= 400 ? '#ef4444' : '#22c55e',
                    fontWeight: 600,
                  }}>
                    {m.status_code}
                  </td>
                  <td style={{ padding: '0.5rem', textAlign: 'right' }}>{m.latency_ms}ms</td>
                  <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                    {new Date(m.recorded_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="rec-empty">
          <div className="rec-empty-icon">📊</div>
          <h4>{t('usage.empty_title')}</h4>
          <p>{t('usage.empty_desc')}</p>
        </div>
      )}
    </div>
  )
}
