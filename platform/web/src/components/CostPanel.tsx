import { useTranslation } from 'react-i18next'
import { useEffect, useMemo, useState } from 'react'
import { getCostEntries, getCostSummary } from '../api/client'
import type { CostEntry, CostSummary } from '../types'
import { ENGINE_NAMES } from '../types'

interface Props {
  workspaceId: string
}

type Period = '1d' | '7d' | '30d' | '90d'

export function CostPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const [entries, setEntries] = useState<CostEntry[]>([])
  const [summary, setSummary] = useState<CostSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Period>('7d')
  const [filterEngine, setFilterEngine] = useState('all')

  useEffect(() => {
    loadData()
  }, [period])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const [entriesData, summaryData] = await Promise.all([
        getCostEntries(),
        getCostSummary(period),
      ])
      setEntries(entriesData)
      setSummary(summaryData)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('cost.load_error'))
    } finally {
      setLoading(false)
    }
  }

  const filteredEntries = useMemo(() => {
    if (filterEngine === 'all') return entries
    return entries.filter((e) => e.engine_name === filterEngine)
  }, [entries, filterEngine])

  const engines = useMemo(() => {
    const set = new Set(entries.map((e) => e.engine_name))
    return Array.from(set)
  }, [entries])

  if (loading) return <div className="dashboard-loading">{t('cost.loading')}</div>
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>{t('cost.retry')}</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>{t('cost.title')}</h3>
        <p className="monitoring-desc">{t('cost.desc')}</p>
      </div>

      {/* Period Selector */}
      <div className="dashboard-filters">
        <select value={period} onChange={(e) => setPeriod(e.target.value as Period)} className="filter-select">
          <option value="1d">{t('period.24h')}</option>
          <option value="7d">{t('period.7d')}</option>
          <option value="30d">{t('period.30d')}</option>
          <option value="90d">{t('period.90d')}</option>
        </select>
        <select value={filterEngine} onChange={(e) => setFilterEngine(e.target.value)} className="filter-select">
          <option value="all">{t('cost.filter_all')}</option>
          {engines.map((e) => (
            <option key={e} value={e}>{t(ENGINE_NAMES[e]) || e}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadData}>{t('common.refresh')}</button>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="monitoring-quick-stats" style={{ marginBottom: '1.5rem' }}>
          <div className="quick-stat">
            <span className="quick-stat-label">{t('cost.total_cost')}</span>
            <span style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--accent)' }}>
              ${summary.total_cost_usd.toFixed(4)}
            </span>
          </div>
          <div className="quick-stat">
            <span className="quick-stat-label">{t('cost.total_tokens')}</span>
            <span style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--accent)' }}>
              {summary.total_tokens.toLocaleString(dateLocale)}
            </span>
          </div>
          {summary.engine_breakdown.length > 0 && (
            <div className="quick-stat">
              <span className="quick-stat-label">{t('cost.engine_breakdown')}</span>
              <ul className="quick-stat-list">
                {summary.engine_breakdown.map((eb) => (
                  <li key={eb.engine}>
                    {t(ENGINE_NAMES[eb.engine]) || eb.engine}: <strong>${eb.cost.toFixed(4)}</strong> ({eb.tokens.toLocaleString(dateLocale)} {t('cost.token_suffix')})
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Entries Table */}
      {filteredEntries.length > 0 ? (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #e2e8f0', textAlign: 'left' }}>
              <th style={{ padding: '0.5rem' }}>{t('cost.table_engine')}</th>
              <th style={{ padding: '0.5rem' }}>{t('cost.table_model')}</th>
              <th style={{ padding: '0.5rem' }}>{t('cost.table_operation')}</th>
              <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('cost.table_tokens')}</th>
              <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('cost.table_cost')}</th>
              <th style={{ padding: '0.5rem' }}>{t('cost.table_date')}</th>
            </tr>
          </thead>
          <tbody>
            {filteredEntries.map((e) => (
              <tr key={e.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.5rem', fontWeight: 600 }}>{t(ENGINE_NAMES[e.engine_name]) || e.engine_name}</td>
                <td style={{ padding: '0.5rem', color: 'var(--text-muted)' }}>{e.model_name || '-'}</td>
                <td style={{ padding: '0.5rem' }}>{e.operation}</td>
                <td style={{ padding: '0.5rem', textAlign: 'right' }}>{e.token_count.toLocaleString(dateLocale)}</td>
                <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 600 }}>${e.cost_usd.toFixed(4)}</td>
                <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                  {new Date(e.recorded_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="rec-empty">
          <div className="rec-empty-icon">💰</div>
          <h4>{t('cost.empty_title')}</h4>
          <p>{t('cost.empty_desc')}</p>
        </div>
      )}
    </div>
  )
}
