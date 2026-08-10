import { useTranslation } from 'react-i18next'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Cell,
} from 'recharts'
import type { Score } from '../types'
import { ENGINE_COLORS, ENGINE_NAMES } from '../types'

interface EngineComparisonProps {
  scores: Score[]
  brandName: string
}

export function EngineComparison({ scores, brandName }: EngineComparisonProps) {
  const { t } = useTranslation()
  // En son skorun engine breakdown'ını kullan
  const latest = scores.length > 0
    ? [...scores].sort((a, b) => new Date(b.freshness_at).getTime() - new Date(a.freshness_at).getTime())[0]
    : null

  if (!latest?.engine_breakdown || Object.keys(latest.engine_breakdown).length === 0) {
    return (
      <div className="engine-comparison">
        <h4>{t('enginecmp.title')}</h4>
        <p className="engine-empty">{t('enginecmp.empty')}</p>
      </div>
    )
  }

  const data = Object.entries(latest.engine_breakdown).map(([engine, val]) => ({
    name: t(ENGINE_NAMES[engine]) || engine,
    value: Math.round(val),
    color: ENGINE_COLORS[engine] || 'var(--text-faint)',
  }))

  return (
    <div className="engine-comparison">
      <h4>{t('enginecmp.title_brand', { brand: brandName })}</h4>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 12, fill: 'var(--text-muted)' }}
            tickLine={false}
            axisLine={{ stroke: 'var(--border)' }}
          />
          <YAxis
            domain={[0, 100]}
            tick={{ fontSize: 11, fill: 'var(--text-faint)' }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            contentStyle={{
              background: 'var(--text)',
              border: 'none',
              borderRadius: '8px',
              fontSize: '12px',
              color: 'var(--surface-2)',
            }}
            formatter={(value: number) => [`${value}`, t('score.trend_tooltip_score')]}
          />
          <Bar dataKey="value" radius={[6, 6, 0, 0]} maxBarSize={60}>
            {data.map((entry, i) => (
              <Cell key={i} fill={entry.color} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
