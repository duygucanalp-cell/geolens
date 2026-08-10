import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { analyzeSentiment, getSentimentSummary, listSentiment } from '../api/client'
import type { SentimentResult, SentimentSummary } from '../types'

interface Props {
  workspaceId: string
}

const SENTIMENT_EMOJI: Record<string, string> = {
  positive: '😊',
  negative: '😟',
  neutral: '😐',
}

const SENTIMENT_COLOR: Record<string, string> = {
  positive: '#22c55e',
  negative: '#ef4444',
  neutral: '#eab308',
}

export function SentimentPanel({ workspaceId: ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [summary, setSummary] = useState<SentimentSummary | null>(null)
  const [results, setResults] = useState<SentimentResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Analyze form
  const [text, setText] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [lastResult, setLastResult] = useState<SentimentResult | null>(null)

  useEffect(() => { loadData() }, [])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const [s, r] = await Promise.all([getSentimentSummary(ws), listSentiment(ws)])
      setSummary(s)
      setResults(r.data || r)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('sentiment.load_error'))
    } finally {
      setLoading(false)
    }
  }

  async function handleAnalyze(e: React.FormEvent) {
    e.preventDefault()
    if (!text.trim()) return
    setAnalyzing(true)
    setLastResult(null)
    try {
      const result = await analyzeSentiment(ws, text)
      setLastResult(result)
      setText('')
      loadData()
    } catch (err) {
      setError(err instanceof Error ? err.message : t('sentiment.analyze_error'))
    } finally {
      setAnalyzing(false)
    }
  }

  if (loading) return <PanelSkeleton message={t('sentiment.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>💬 {t('sentiment.title')}</h3>
        <p className="rec-desc">{t('sentiment.desc')}</p>
      </div>

      {error && <div className="audit-error">{error}</div>}

      {/* Summary */}
      {summary && (
        <div className="rec-summary">
          <div className="rec-summary-card total">
            <span className="rec-summary-value">{summary.total}</span>
            <span className="rec-summary-label">{t('sentiment.total')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: 'var(--success-bg)' }}>
            <span className="rec-summary-value" style={{ color: SENTIMENT_COLOR.positive }}>{summary.positive}</span>
            <span className="rec-summary-label">{t('sentiment.positive')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: 'var(--danger-bg)' }}>
            <span className="rec-summary-value" style={{ color: SENTIMENT_COLOR.negative }}>{summary.negative}</span>
            <span className="rec-summary-label">{t('sentiment.negative')}</span>
          </div>
          <div className="rec-summary-card" style={{ background: 'var(--amber-bg)' }}>
            <span className="rec-summary-value" style={{ color: SENTIMENT_COLOR.neutral }}>{summary.neutral}</span>
            <span className="rec-summary-label">{t('sentiment.neutral')}</span>
          </div>
        </div>
      )}

      {/* Analyze form */}
      <form onSubmit={handleAnalyze} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <textarea
            className="notif-input"
            rows={3}
            placeholder={t('sentiment.text_placeholder')}
            value={text}
            onChange={e => setText(e.target.value)}
            style={{ fontFamily: 'inherit', fontSize: '0.85rem', resize: 'vertical' }}
          />
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button type="submit" className="audit-btn" disabled={analyzing || !text.trim()}>
              {analyzing ? t('sentiment.analyzing') : t('sentiment.analyze')}
            </button>
          </div>
        </div>
      </form>

      {/* Last analysis result */}
      {lastResult && (
        <div style={{ background: 'var(--success-bg)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ fontSize: '1.5rem' }}>{SENTIMENT_EMOJI[lastResult.sentiment]}</span>
            <div style={{ flex: 1 }}>
              <strong style={{ color: SENTIMENT_COLOR[lastResult.sentiment] }}>
                {t(`sentiment.${lastResult.sentiment}`)}
              </strong>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                {t('sentiment.confidence')}: {(lastResult.confidence * 100).toFixed(1)}%
              </p>
            </div>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>
              {new Date(lastResult.analyzed_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
            </span>
          </div>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.5rem', fontStyle: 'italic' }}>
            &ldquo;{lastResult.text.length > 120 ? lastResult.text.slice(0, 120) + '…' : lastResult.text}&rdquo;
          </p>
        </div>
      )}

      {/* Results list */}
      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={loadData}>{t('common.refresh')}</button>
      </div>

      {results.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">💬</div>
          <h4>{t('sentiment.empty_title')}</h4>
          <p>{t('sentiment.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {results.map((r) => (
            <div key={r.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: SENTIMENT_COLOR[r.sentiment] }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge" style={{ background: 'var(--surface-hover)' }}>
                    {SENTIMENT_EMOJI[r.sentiment]} {t(`sentiment.${r.sentiment}`)}
                  </span>
                  <span className="rec-status-badge" style={{
                    background: r.sentiment === 'positive' ? 'var(--success-bg)' : r.sentiment === 'negative' ? 'var(--danger-bg)' : 'var(--amber-bg)',
                    color: SENTIMENT_COLOR[r.sentiment],
                  }}>
                    {(r.confidence * 100).toFixed(0)}%
                  </span>
                </div>
                <p className="rec-title" style={{ fontSize: '0.85rem', fontWeight: 400, color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                  {r.text.length > 180 ? r.text.slice(0, 180) + '…' : r.text}
                </p>
                <div className="rec-meta">
                  <span className="rec-date">
                    {new Date(r.analyzed_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
