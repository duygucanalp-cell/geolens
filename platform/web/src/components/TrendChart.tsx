import { useMemo } from 'react'
import type { Score } from '../types'

interface TrendChartProps {
  scores: Score[]
  brandName: string
}

interface Point {
  x: number
  y: number
  ciLow?: number
  ciHigh?: number
  label: string
  date: string
}

interface ChartData {
  points: Point[]
  yMin: number
  yMax: number
}

const WIDTH = 500
const HEIGHT = 200
const PAD = { top: 20, right: 20, bottom: 30, left: 40 }
const CHART_W = WIDTH - PAD.left - PAD.right
const CHART_H = HEIGHT - PAD.top - PAD.bottom

export function TrendChart({ scores, brandName }: TrendChartProps) {
  const { points, yMin, yMax } = useMemo<ChartData>(() => {
    if (scores.length === 0) return { points: [], yMin: 0, yMax: 100 }

    const sorted = [...scores].sort(
      (a, b) => new Date(a.freshness_at).getTime() - new Date(b.freshness_at).getTime()
    )

    const vals = sorted.map((s) => s.value)
    const min = Math.min(...vals)
    const max = Math.max(...vals)
    const range = max - min
    const padding = range * 0.2 || 10
    const computedYMin = Math.max(0, Math.floor(min - padding))
    const computedYMax = Math.min(100, Math.ceil(max + padding))

    const xScale = (i: number) =>
      PAD.left + (sorted.length > 1 ? (i / (sorted.length - 1)) * CHART_W : CHART_W / 2)
    const yScale = (v: number) =>
      PAD.top + CHART_H - ((v - computedYMin) / (computedYMax - computedYMin)) * CHART_H

    const pts: Point[] = sorted.map((s, i) => ({
      x: xScale(i),
      y: yScale(s.value),
      ciLow: s.ci_low !== undefined ? yScale(s.ci_low) : undefined,
      ciHigh: s.ci_high !== undefined ? yScale(s.ci_high) : undefined,
      label: s.value.toFixed(1),
      date: new Date(s.freshness_at).toLocaleDateString('tr-TR'),
    }))

    return { points: pts, yMin: computedYMin, yMax: computedYMax }
  }, [scores])

  if (points.length === 0) {
    return <div className="trend-empty">Trend verisi yok</div>
  }

  const linePath = points.map((p: Point, i: number) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')

  const ciAreaPath =
    points.length > 1
      ? points
          .map((p: Point, i: number) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${(p.ciHigh ?? p.y).toFixed(1)}`)
          .join(' ') +
        ' ' +
        points
          .map((_p: Point, i: number) => `L${points[points.length - 1 - i].x.toFixed(1)},${(points[points.length - 1 - i].ciLow ?? points[points.length - 1 - i].y).toFixed(1)}`)
          .join(' ') +
        ' Z'
      : ''

  const yTicks: number[] = []
  const step = Math.max(1, Math.round((yMax - yMin) / 4))
  for (let v = yMin; v <= yMax; v += step) {
    yTicks.push(v)
  }

  return (
    <div className="trend-chart">
      <h3 className="trend-title">{brandName} — Görünürlük Trendi</h3>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="trend-svg">
        {yTicks.map((v) => (
          <g key={v}>
            <line
              x1={PAD.left}
              y1={PAD.top + CHART_H - ((v - yMin) / (yMax - yMin)) * CHART_H}
              x2={WIDTH - PAD.right}
              y2={PAD.top + CHART_H - ((v - yMin) / (yMax - yMin)) * CHART_H}
              stroke="#e2e8f0"
              strokeWidth={1}
            />
            <text
              x={PAD.left - 6}
              y={PAD.top + CHART_H - ((v - yMin) / (yMax - yMin)) * CHART_H + 4}
              textAnchor="end"
              className="trend-axis-label"
            >
              {v}
            </text>
          </g>
        ))}

        {ciAreaPath && (
          <path d={ciAreaPath} fill="#6366f1" opacity={0.1} />
        )}

        <path d={linePath} fill="none" stroke="#6366f1" strokeWidth={2} strokeLinejoin="round" />

        {points.map((p: Point, i: number) => (
          <g key={i}>
            <circle cx={p.x} cy={p.y} r={4} fill="#6366f1" stroke="#fff" strokeWidth={2} />
            {p.ciLow !== undefined && p.ciHigh !== undefined && (
              <line
                x1={p.x}
                y1={p.ciLow}
                x2={p.x}
                y2={p.ciHigh}
                stroke="#6366f1"
                strokeWidth={1}
                opacity={0.4}
              />
            )}
            <text x={p.x} y={HEIGHT - 6} textAnchor="middle" className="trend-x-label">
              {p.date}
            </text>
          </g>
        ))}
      </svg>
    </div>
  )
}
