import { useTranslation } from 'react-i18next'
import { useEffect, useMemo, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
import { Highlight } from './Highlight'
import { evaluateBias, listBiasTests } from '../api/client'
import { normalizeSearch } from '../utils/search'
import type { BiasTest } from '../types'

interface Props {
  workspaceId: string
  // Birleşik sayfada ortak arama + yenileme dışarıdan gelir
  embedded?: boolean
  searchQuery?: string
  refreshTick?: number
}

export function BiasPanel({ workspaceId: _ws, embedded, searchQuery = '', refreshTick = 0 }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'
  // Dil değişmedikçe kararlı kalır — filtre memo'sunun bağımlılığı boşa
  // yeniden hesaplanmasın (t değişince etiketler de değişir, doğru davranış)
  const METRIC_LABELS = useMemo<Record<string, string>>(() => ({
    demographic_parity: t('bias.metric_demographic_parity'),
    equal_opportunity: t('bias.metric_equal_opportunity'),
    disparate_impact: t('bias.metric_disparate_impact'),
  }), [t])

  const METRIC_DESCS = useMemo<Record<string, string>>(() => ({
    demographic_parity: t('bias.metric_dp_desc'),
    equal_opportunity: t('bias.metric_eo_desc'),
    disparate_impact: t('bias.metric_di_desc'),
  }), [t])
  const [tests, setTests] = useState<BiasTest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showEvaluate, setShowEvaluate] = useState(false)

  // Evaluate form
  const [modelId, setModelId] = useState('')
  const [metricType, setMetricType] = useState('demographic_parity')
  const [groupData, setGroupData] = useState('{"group_a": 0.8, "group_b": 0.6}')
  const [evalResult, setEvalResult] = useState<any>(null)
  const [evalLoading, setEvalLoading] = useState(false)

  useEffect(() => { loadTests() }, [refreshTick])

  // Ortak arama: model kimliği / metrik etiketine göre istemci tarafında filtrele
  const filteredTests = useMemo(() => {
    const q = normalizeSearch(searchQuery.trim())
    if (!q) return tests
    return tests.filter(test => {
      const metricLabel = METRIC_LABELS[test.metric_type] || test.metric_type
      return (
        normalizeSearch(test.model_id).includes(q) ||
        normalizeSearch(metricLabel).includes(q) ||
        normalizeSearch(test.metric_type).includes(q)
      )
    })
  }, [tests, searchQuery, METRIC_LABELS])

  async function loadTests() {
    try { setLoading(true); setError(null); setTests(await listBiasTests()) }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleEvaluate(e: React.FormEvent) {
    e.preventDefault()
    if (!modelId.trim()) return
    setEvalLoading(true)
    setEvalResult(null)
    try {
      let parsed: Record<string, number> = {}
      try { parsed = JSON.parse(groupData) } catch { setError(t('bias.invalid_json')); setEvalLoading(false); return }
      const result = await evaluateBias({ model_id: modelId, metric_type: metricType, data: parsed })
      setEvalResult(result)
      loadTests()
    } catch (err) { setError(err instanceof Error ? err.message : t('bias.eval_error')) }
    finally { setEvalLoading(false) }
  }

  const avgFairness = tests.length > 0 ?tests.reduce((s, item) => s + item.fairness_score, 0) / tests.length : 0
const biasCount = tests.filter(item => item.has_bias).length

  if (loading) return <PanelSkeleton message={t('bias.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('bias.title')}</h3>
        <p className="rec-desc">{t('bias.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      {/* Summary */}
      <div className="rec-summary">
        <div className="rec-summary-card total">
          <span className="rec-summary-value">{tests.length}</span>
          <span className="rec-summary-label">{t('bias.summary_total')}</span>
        </div>
        <div className="rec-summary-card" style={{ background: avgFairness >= 0.8 ? 'var(--success-bg)' : 'var(--danger-bg)' }}>
          <span className="rec-summary-value" style={{ color: avgFairness >= 0.8 ? '#22c55e' : '#ef4444' }}>
            {(avgFairness * 100).toFixed(0)}
          </span>
          <span className="rec-summary-label">{t('bias.summary_fairness')}</span>
        </div>
        <div className="rec-summary-card" style={{ background: biasCount > 0 ? 'var(--danger-bg)' : 'var(--success-bg)' }}>
          <span className="rec-summary-value" style={{ color: biasCount > 0 ? '#ef4444' : '#22c55e' }}>
            {biasCount}
          </span>
          <span className="rec-summary-label">{t('bias.summary_bias')}</span>
        </div>
      </div>

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowEvaluate(!showEvaluate)}>
          {showEvaluate ? t('guardrails.cancel') : t('bias.new_eval')}
        </button>
        {!embedded && <button className="refresh-btn" onClick={loadTests}>{t('bias.refresh')}</button>}
      </div>

      {showEvaluate && (
        <form onSubmit={handleEvaluate} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input className="notif-input" style={{ flex: 1 }} placeholder={t('bias.model_id_placeholder')} value={modelId} onChange={e => setModelId(e.target.value)} required />
              <select value={metricType} onChange={e => setMetricType(e.target.value)} className="filter-select">
                {Object.entries(METRIC_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{METRIC_DESCS[metricType]}</p>
            <textarea
              className="notif-input"
              rows={3}
              placeholder={t('bias.json_placeholder')}
              value={groupData}
              onChange={e => setGroupData(e.target.value)}
              style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}
            />
            <button type="submit" className="audit-btn" disabled={evalLoading}>
              {evalLoading ? t('bias.evaluating') : t('bias.evaluate')}
            </button>
          </div>
        </form>
      )}

      {evalResult && (
        <div style={{ background: evalResult.has_bias ? 'var(--danger-bg)' : 'var(--success-bg)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '1.5rem' }}>{evalResult.has_bias ? '🔴' : '✅'}</span>
            <div>
              <strong>{evalResult.has_bias ? t('bias.result_bias_detected') : t('bias.result_no_bias')}</strong>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>{t('bias.result_fairness_score', { score: (evalResult.fairness_score * 100).toFixed(1) })}</p>
            </div>
          </div>
          {evalResult.results?.recommendations?.length > 0 && (
            <ul style={{ marginTop: '0.5rem', padding: '0 0 0 1rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              {evalResult.results.recommendations.map((r: string, i: number) => <li key={i}>{r}</li>)}
            </ul>
          )}
        </div>
      )}

      {filteredTests.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">⚖️</div>{searchQuery ? <h4>{t('merged.no_results')}</h4> : <h4>{t('bias.empty_title')}</h4>}</div>
      ) : (
        <div className="rec-list">            {filteredTests.map((test) => (
            <div key={test.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: test.has_bias ? '#ef4444' : '#22c55e' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge"><Highlight text={METRIC_LABELS[test.metric_type] || test.metric_type} query={searchQuery} /></span>
                  <span className="rec-status-badge" style={{ background: test.has_bias ? 'var(--danger-bg)' : 'var(--success-bg)', color: test.has_bias ? '#ef4444' : '#22c55e' }}>
                    {test.has_bias ? t('bias.tag_bias') : t('bias.tag_fair')}
                  </span>
                </div>
                <h4 className="rec-title">{t('bias.model_label')}: <Highlight text={test.model_id} query={searchQuery} /></h4>
                <div className="rec-meta">
                  <span className="rec-confidence-label">{t('bias.fairness_label')}: {(test.fairness_score * 100).toFixed(1)}%</span>
                  <span className="rec-date">{t('bias.max_gap_label')}: {(test.max_gap * 100).toFixed(1)}%</span>
                  <span className="rec-date">{new Date(test.created_at).toLocaleDateString(dateLocale, { day: 'numeric', month: 'short' })}</span>
                </div>
                {test.recommendations?.length > 0 && (
                  <p className="rec-detail" style={{ marginTop: '0.3rem' }}>
                    {test.recommendations.slice(0, 2).map((r, i) => <span key={i}>• {r}<br /></span>)}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
