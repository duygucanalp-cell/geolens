import { useTranslation } from 'react-i18next'
import { useMemo } from 'react'
import {
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ComposedChart,
} from 'recharts'
import type { Score } from '../types'

interface TrendChartProps {
  scores: Score[]
  brandName: string
}

interface ChartPoint {
  date: string
  dateLabel: string
  value: number
  ciLow: number
  ciHigh: number
}

export function TrendChart({ scores, brandName }: TrendChartProps) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const chartData = useMemo<ChartPoint[]>(() => {
    if (scores.length === 0) return []

    const sorted = [...scores].sort(
      (a, b) => new Date(a.freshness_at).getTime() - new Date(b.freshness_at).getTime()
    )
    return sorted.map((s) => ({
      date: s.freshness_at,
      dateLabel: new Date(s.freshness_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' }),
      value: Math.round(s.value * 10) / 10,
      ciLow: s.ci_low ?? Math.max(0, s.value - 5),
      ciHigh: s.ci_high ?? Math.min(100, s.value + 5),
    }))
  }, [scores])

  if (chartData.length === 0) {
    return <div className="trend-empty">{t('score.trend_empty')}</div>
  }

  return (
    <div className="trend-chart">
      <h3 className="trend-title">{t('score.trend_title', { brand: brandName })}</h3>
      <ResponsiveContainer width="100%" height={220}>
        <ComposedChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
          <defs>
            <linearGradient id="ciBand" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#6366f1" stopOpacity={0.15} />
              <stop offset="95%" stopColor="#6366f1" stopOpacity={0.05} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis
            dataKey="dateLabel"
            tick={{ fontSize: 11, fill: 'var(--text-faint)' }}
            tickLine={false}
            axisLine={{ stroke: 'var(--border)' }}
          />
          <YAxis
            domain={[0, 100]}
            tick={{ fontSize: 11, fill: 'var(--text-faint)' }}
            tickLine={false}
            axisLine={false}
            tickCount={6}
          />
          <Tooltip
            contentStyle={{
              background: 'var(--text)',
              border: 'none',
              borderRadius: '8px',
              fontSize: '12px',
              color: 'var(--surface-2)',
            }}
            labelFormatter={(label) => t('score.trend_tooltip_date', { label })}
            formatter={(value: number) => [`${value.toFixed(1)}`, t('score.trend_tooltip_score')]}
          />
          {/* CI band area */}
          <Area
            type="monotone"
            dataKey="ciHigh"
            stroke="none"
            fill="url(#ciBand)"
            fillOpacity={1}
          />
          <Area
            type="monotone"
            dataKey="ciLow"
            stroke="none"
            fill="var(--surface-2)"
            fillOpacity={1}
          />
          {/* Score line */}
          <Line
            type="monotone"
            dataKey="value"
            stroke="#6366f1"
            strokeWidth={2.5}
            dot={{ r: 4, fill: 'var(--accent)', stroke: '#fff', strokeWidth: 2 }}
            activeDot={{ r: 6, fill: 'var(--accent)', stroke: '#fff', strokeWidth: 2 }}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}
