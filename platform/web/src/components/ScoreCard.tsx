import { useTranslation } from 'react-i18next'
import type { Score } from '../types'

interface ScoreCardProps {
  score: Score
}

const engineColors: Record<string, string> = {
  perplexity: 'var(--accent)',
  chatgpt: '#10b981',
  gemini: '#f59e0b',
}

export function ScoreCard({ score }: ScoreCardProps) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  const ci = score.ci_high - score.ci_low
  const ciPercent = Math.min((ci / Math.max(score.value, 1)) * 100, 100)

  return (
    <div className="score-card">
      <div className="score-header">
        <h3>{score.brand_name}</h3>
        <span className="fidelity-badge">{score.fidelity_label}</span>
      </div>

      <div className="score-value">
        <span className="value">{Math.round(score.value)}</span>
        <span className="max">/100</span>
      </div>

      <div className="ci-bar">
        <div
          className="ci-range"
          style={{
            marginLeft: `${score.ci_low}%`,
            width: `${ciPercent}%`,
          }}
        />
      </div>

      <div className="ci-labels">
        <span>GA: %{Math.round(score.ci_low * 10) / 10}</span>
        <span>%{Math.round(score.ci_high * 10) / 10}</span>
      </div>

      {score.engine_breakdown && (
        <div className="engine-breakdown">
          <h4>{t('dashboard.engine_breakdown')}</h4>
          <div className="engine-bars">
            {Object.entries(score.engine_breakdown).map(([engine, val]) => (
              <div key={engine} className="engine-row">
                <span className="engine-name">{engine}</span>
                <div className="bar-track">
                  <div
                    className="bar-fill"
                    style={{
                      width: `${val}%`,
                      backgroundColor: engineColors[engine] || 'var(--text-faint)',
                    }}
                  />
                </div>
                <span className="engine-val">{Math.round(val)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="score-meta">
        <span>{t('score.last_updated', { date: new Date(score.freshness_at).toLocaleDateString(dateLocale) })}</span>
      </div>
    </div>
  )
}
