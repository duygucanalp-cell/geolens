import { useTranslation } from 'react-i18next'
import { useEffect, useMemo, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
import { listBenchmarks, runBenchmark, compareBenchmarks } from '../api/client'
import type { BenchmarkResult, BenchmarkComparison } from '../types'

interface Props {
  workspaceId: string
}

type SortKey = 'model_name' | 'benchmark_name' | 'score' | 'tested_at'

export function BenchmarkPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const [benchmarks, setBenchmarks] = useState<BenchmarkResult[]>([])
  const [comparison, setComparison] = useState<BenchmarkComparison | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [formModel, setFormModel] = useState('')
  const [formBenchmark, setFormBenchmark] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sortKey, setSortKey] = useState<SortKey>('tested_at')
  const [sortAsc, setSortAsc] = useState(false)
  const [showCompare, setShowCompare] = useState(false)

  useEffect(() => {
    loadData()
  }, [])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const [b, c] = await Promise.all([listBenchmarks(), compareBenchmarks()])
      setBenchmarks(b.data)
      setComparison(c)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('benchmark.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleRun(e: React.FormEvent) {
    e.preventDefault()
    if (!formModel.trim()) return
    try {
      setSubmitting(true)
      setError(null)
      await runBenchmark({
        model_name: formModel.trim(),
        benchmark_name: formBenchmark.trim() || undefined,
      })
      setFormModel('')
      setFormBenchmark('')
      await loadData()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('benchmark.run_error'))
    } finally {
      setSubmitting(false)
    }
  }

  const sortedBenchmarks = useMemo(() => {
    const sorted = [...benchmarks]
    sorted.sort((a, b) => {
      let cmp = 0
      if (sortKey === 'score') {
        cmp = a.score - b.score
      } else if (sortKey === 'tested_at') {
        cmp = new Date(a.tested_at).getTime() - new Date(b.tested_at).getTime()
      } else {
        cmp = String(a[sortKey]).localeCompare(String(b[sortKey]))
      }
      return sortAsc ? cmp : -cmp
    })
    return sorted
  }, [benchmarks, sortKey, sortAsc])

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortAsc((prev) => !prev)
    } else {
      setSortKey(key)
      setSortAsc(false)
    }
  }

  function sortArrow(key: SortKey) {
    if (sortKey !== key) return ''
    return sortAsc ? ' ▲' : ' ▼'
  }

  if (loading) return <PanelSkeleton message={t('benchmark.loading')} />
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>{t('common.retry')}</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>{t('benchmark.title')}</h3>
        <p className="monitoring-desc">{t('benchmark.desc')}</p>
      </div>

      {/* Run Benchmark Form */}
      <form onSubmit={handleRun} className="dashboard-filters" style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
        <input
          type="text"
          placeholder={t('benchmark.model_placeholder')}
          value={formModel}
          onChange={(e) => setFormModel(e.target.value)}
          className="filter-select"
          style={{ flex: '1 1 200px' }}
          required
        />
        <input
          type="text"
          placeholder={t('benchmark.name_placeholder')}
          value={formBenchmark}
          onChange={(e) => setFormBenchmark(e.target.value)}
          className="filter-select"
          style={{ flex: '1 1 200px' }}
        />
        <button type="submit" className="refresh-btn" disabled={submitting || !formModel.trim()}>
          {submitting ? t('benchmark.running') : t('benchmark.run')}
        </button>
      </form>

      {/* Toggle Comparison */}
      {comparison && (
        <div style={{ marginBottom: '1rem' }}>
          <button
            className="refresh-btn"
            onClick={() => setShowCompare((prev) => !prev)}
            style={{ marginBottom: '0.5rem' }}
          >
            {showCompare ? t('benchmark.hide_compare') : t('benchmark.compare')}
          </button>
        </div>
      )}

      {/* Comparison Table */}
      {showCompare && comparison && (
        <div style={{ marginBottom: '1.5rem', overflowX: 'auto' }}>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
            {t('benchmark.compare')}
          </h4>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '0.5rem' }}>{t('benchmark.col_metrics')}</th>
                {comparison.models.map((m) => (
                  <th key={m} style={{ padding: '0.5rem', textAlign: 'right' }}>{m}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {comparison.metrics.map((metric) => (
                <tr key={metric} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '0.5rem', fontWeight: 600 }}>{metric}</td>
                  {comparison.models.map((m) => {
                    const val = comparison.results[m]?.[metric]
                    return (
                      <td key={m} style={{ padding: '0.5rem', textAlign: 'right' }}>
                        {val !== undefined && val !== null ? val.toFixed(4) : '-'}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Benchmarks List */}
      {sortedBenchmarks.length > 0 ? (
        <div>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
            {t('benchmark.history')}
          </h4>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th
                  style={{ padding: '0.5rem', cursor: 'pointer' }}
                  onClick={() => toggleSort('model_name')}
                >
                  {t('benchmark.col_model')}{sortArrow('model_name')}
                </th>
                <th
                  style={{ padding: '0.5rem', cursor: 'pointer' }}
                  onClick={() => toggleSort('benchmark_name')}
                >
                  {t('benchmark.col_benchmark')}{sortArrow('benchmark_name')}
                </th>
                <th
                  style={{ padding: '0.5rem', cursor: 'pointer', textAlign: 'right' }}
                  onClick={() => toggleSort('score')}
                >
                  {t('benchmark.col_score')}{sortArrow('score')}
                </th>
                <th style={{ padding: '0.5rem' }}>{t('benchmark.col_metrics')}</th>
                <th
                  style={{ padding: '0.5rem', cursor: 'pointer' }}
                  onClick={() => toggleSort('tested_at')}
                >
                  {t('benchmark.col_date')}{sortArrow('tested_at')}
                </th>
              </tr>
            </thead>
            <tbody>
              {sortedBenchmarks.map((b) => (
                <tr key={b.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '0.5rem', fontWeight: 600 }}>{b.model_name}</td>
                  <td style={{ padding: '0.5rem', color: 'var(--text-muted)' }}>{b.benchmark_name}</td>
                  <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 600 }}>
                    {b.score.toFixed(2)}
                  </td>
                  <td style={{ padding: '0.5rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    {Object.entries(b.metrics).map(([k, v]) => (
                      <span key={k} style={{ marginRight: '0.75rem', whiteSpace: 'nowrap' }}>
                        {k}: <strong>{typeof v === 'number' ? v.toFixed(2) : v}</strong>
                      </span>
                    ))}
                  </td>
                  <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                    {new Date(b.tested_at).toLocaleDateString(dateLocale, {
                      day: 'numeric',
                      month: 'short',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="rec-empty">
          <div className="rec-empty-icon">🧪</div>
          <h4>{t('benchmark.empty_title')}</h4>
          <p>{t('benchmark.empty_desc')}</p>
        </div>
      )}
    </div>
  )
}
