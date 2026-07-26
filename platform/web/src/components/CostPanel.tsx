import { useEffect, useMemo, useState } from 'react'
import { getCostEntries, getCostSummary } from '../api/client'
import type { CostEntry, CostSummary } from '../types'
import { ENGINE_NAMES } from '../types'

interface Props {
  workspaceId: string
}

type Period = '1d' | '7d' | '30d' | '90d'

export function CostPanel({ workspaceId: _ws }: Props) {
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
      setError(err instanceof Error ? err.message : 'Maliyet verileri yüklenemedi')
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

  if (loading) return <div className="dashboard-loading">Maliyet verileri yükleniyor...</div>
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>Tekrar Dene</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>💰 Maliyet Analizi</h3>
        <p className="monitoring-desc">AI motor ve model bazlı maliyet takibi.</p>
      </div>

      {/* Period Selector */}
      <div className="dashboard-filters">
        <select value={period} onChange={(e) => setPeriod(e.target.value as Period)} className="filter-select">
          <option value="1d">Son 24 Saat</option>
          <option value="7d">Son 7 Gün</option>
          <option value="30d">Son 30 Gün</option>
          <option value="90d">Son 90 Gün</option>
        </select>
        <select value={filterEngine} onChange={(e) => setFilterEngine(e.target.value)} className="filter-select">
          <option value="all">Tüm Motorlar</option>
          {engines.map((e) => (
            <option key={e} value={e}>{ENGINE_NAMES[e] || e}</option>
          ))}
        </select>
        <button className="refresh-btn" onClick={loadData}>Yenile</button>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="monitoring-quick-stats" style={{ marginBottom: '1.5rem' }}>
          <div className="quick-stat">
            <span className="quick-stat-label">Toplam Maliyet</span>
            <span style={{ fontSize: '1.5rem', fontWeight: 700, color: '#6366f1' }}>
              ${summary.total_cost_usd.toFixed(4)}
            </span>
          </div>
          <div className="quick-stat">
            <span className="quick-stat-label">Toplam Token</span>
            <span style={{ fontSize: '1.5rem', fontWeight: 700, color: '#6366f1' }}>
              {summary.total_tokens.toLocaleString('tr-TR')}
            </span>
          </div>
          {summary.engine_breakdown.length > 0 && (
            <div className="quick-stat">
              <span className="quick-stat-label">Motor Bazında</span>
              <ul className="quick-stat-list">
                {summary.engine_breakdown.map((eb) => (
                  <li key={eb.engine}>
                    {ENGINE_NAMES[eb.engine] || eb.engine}: <strong>${eb.cost.toFixed(4)}</strong> ({eb.tokens.toLocaleString('tr-TR')} token)
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
              <th style={{ padding: '0.5rem' }}>Motor</th>
              <th style={{ padding: '0.5rem' }}>Model</th>
              <th style={{ padding: '0.5rem' }}>İşlem</th>
              <th style={{ padding: '0.5rem', textAlign: 'right' }}>Token</th>
              <th style={{ padding: '0.5rem', textAlign: 'right' }}>Maliyet</th>
              <th style={{ padding: '0.5rem' }}>Tarih</th>
            </tr>
          </thead>
          <tbody>
            {filteredEntries.map((e) => (
              <tr key={e.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                <td style={{ padding: '0.5rem', fontWeight: 600 }}>{ENGINE_NAMES[e.engine_name] || e.engine_name}</td>
                <td style={{ padding: '0.5rem', color: '#64748b' }}>{e.model_name || '-'}</td>
                <td style={{ padding: '0.5rem' }}>{e.operation}</td>
                <td style={{ padding: '0.5rem', textAlign: 'right' }}>{e.token_count.toLocaleString('tr-TR')}</td>
                <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 600 }}>${e.cost_usd.toFixed(4)}</td>
                <td style={{ padding: '0.5rem', color: '#94a3b8', fontSize: '0.8rem' }}>
                  {new Date(e.recorded_at).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="rec-empty">
          <div className="rec-empty-icon">💰</div>
          <h4>Henüz maliyet kaydı yok</h4>
          <p>AI motor kullanımı başladıkça maliyet verileri burada görünecek.</p>
        </div>
      )}
    </div>
  )
}
