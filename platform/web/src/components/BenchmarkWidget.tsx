import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getBenchmarkContext } from '../api/client'
import type { BenchmarkContext } from '../types'

interface Props {
  workspaceId: string
}

export function BenchmarkWidget({ workspaceId }: Props) {
  const { t } = useTranslation()
  const [data, setData] = useState<BenchmarkContext | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showDetails, setShowDetails] = useState(false)

  useEffect(() => {
    loadData()
  }, [workspaceId])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const ctx = await getBenchmarkContext(workspaceId)
      setData(ctx)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sektör verisi alınamadı')
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="benchmark-widget">
        <div className="benchmark-loading">{t('benchmark_widget.loading')}</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="benchmark-widget">
        <div className="benchmark-error">
          <span className="benchmark-error-text">{error}</span>
          <button className="benchmark-retry-btn" onClick={loadData}>
            {t('common.retry')}
          </button>
        </div>
      </div>
    )
  }

  if (!data) {
    return null
  }

  if (!data.sufficient_data) {
    return (
      <div className="benchmark-widget">
        <div className="benchmark-header">
          <span className="benchmark-icon">📊</span>
          <span className="benchmark-title">{t('benchmark_widget.title')}</span>
        </div>
        <div className="benchmark-insufficient">
          {data.message || t('benchmark_widget.insufficient_data')}
        </div>
      </div>
    )
  }

  const myScore = data.my_score
  const sectorAvg = data.sector_average!
  const trendIcon = data.trend === 'up' ? '▲' : data.trend === 'down' ? '▼' : '◆'
  const trendClass = data.trend === 'up' ? 'trend-up' : data.trend === 'down' ? 'trend-down' : 'trend-stable'
  const diff = data.difference ?? 0
  const diffLabel = diff > 0 ? `+${diff.toFixed(1)}` : diff.toFixed(1)

  // Position score as a percentage bar
  const maxVal = Math.max(myScore, data.sector_max ?? sectorAvg, 100)
  const myPct = (myScore / maxVal) * 100
  const avgPct = (sectorAvg / maxVal) * 100

  // Determine which percentile bucket
  let percentileLabel = ''
  if (data.percentile_25 !== undefined && data.percentile_75 !== undefined) {
    if (myScore >= data.percentile_75) {
      percentileLabel = t('benchmark_widget.top_25')
    } else if (myScore >= data.percentile_25) {
      percentileLabel = t('benchmark_widget.mid_50')
    } else {
      percentileLabel = t('benchmark_widget.bottom_25')
    }
  }

  return (
    <div className="benchmark-widget">
      <div className="benchmark-header">
        <span className="benchmark-icon">📊</span>
        <span className="benchmark-title">{t('benchmark_widget.title')}</span>
        <button className="benchmark-refresh-btn" onClick={loadData} title={t('common.refresh')}>
          ↻
        </button>
      </div>

      <div className="benchmark-body">
        {/* Score comparison row */}
        <div className="benchmark-row">
          <div className="benchmark-label">{t('benchmark_widget.my_score')}</div>
          <div className="benchmark-score-group">
            <span className="benchmark-score-value">{myScore.toFixed(0)}</span>
            <span className={`benchmark-trend ${trendClass}`}>
              <span className="benchmark-trend-arrow">{trendIcon}</span>
              <span className="benchmark-trend-diff">{diffLabel}</span>
            </span>
          </div>
        </div>

        {/* My score bar */}
        <div className="benchmark-bar-track">
          <div
            className="benchmark-bar-fill benchmark-bar-my"
            style={{ width: `${myPct}%` }}
          >
            <div className="benchmark-bar-dot" />
          </div>
        </div>

        {/* Sector avg row */}
        <div className="benchmark-row benchmark-row-secondary">
          <div className="benchmark-label">{t('benchmark_widget.sector_avg')}</div>
          <span className="benchmark-score-value benchmark-score-secondary">
            {sectorAvg.toFixed(1)}
          </span>
        </div>

        {/* Sector avg bar */}
        <div className="benchmark-bar-track">
          <div
            className="benchmark-bar-fill benchmark-bar-avg"
            style={{ width: `${avgPct}%` }}
          >
            <div className="benchmark-bar-marker" />
          </div>
        </div>

        {/* Percentile badge */}
        {percentileLabel && (
          <div className="benchmark-percentile-badge">
            {percentileLabel}
          </div>
        )}

        {/* Detail toggle */}
        <button
          className="benchmark-detail-toggle"
          onClick={() => setShowDetails(prev => !prev)}
        >
          {showDetails ? t('benchmark_widget.hide_details') : t('benchmark_widget.show_details')}
        </button>

        {/* Detailed stats */}
        {showDetails && (
          <div className="benchmark-details">
            <div className="benchmark-detail-grid">
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">{t('benchmark_widget.median')}</span>
                <span className="benchmark-detail-value">{data.sector_median?.toFixed(1)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">{t('benchmark_widget.stddev')}</span>
                <span className="benchmark-detail-value">{data.sector_stddev?.toFixed(1)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">{t('benchmark_widget.min')}</span>
                <span className="benchmark-detail-value">{data.sector_min?.toFixed(0)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">{t('benchmark_widget.max')}</span>
                <span className="benchmark-detail-value">{data.sector_max?.toFixed(0)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">P25</span>
                <span className="benchmark-detail-value">{data.percentile_25?.toFixed(0)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">P75</span>
                <span className="benchmark-detail-value">{data.percentile_75?.toFixed(0)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">P90</span>
                <span className="benchmark-detail-value">{data.percentile_90?.toFixed(0)}</span>
              </div>
              <div className="benchmark-detail-item">
                <span className="benchmark-detail-label">{t('benchmark_widget.tenants')}</span>
                <span className="benchmark-detail-value">{data.tenant_count}</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
