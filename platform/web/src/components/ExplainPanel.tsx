import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { PanelSkeleton } from './PanelSkeleton'
import { explainEntity, listExplainResults } from '../api/client'
import type { ExplainResult } from '../types'

interface Props { workspaceId: string }

const IMPACT_COLORS: Record<string, string> = { positive: '#22c55e', negative: '#ef4444', neutral: 'var(--text-faint)' }

export function ExplainPanel({ workspaceId: _ws }: Props) {
  const { t } = useTranslation()
  const [analyses, setAnalyses] = useState<ExplainResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [entityId, setEntityId] = useState('')
  const [explainResult, setExplainResult] = useState<ExplainResult | null>(null)
  const [explainLoading, setExplainLoading] = useState(false)

  useEffect(() => { loadAnalyses() }, [])

  async function loadAnalyses() {
    try { setLoading(true); setError(null); setAnalyses(await listExplainResults()) }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleExplain(e: React.FormEvent) {
    e.preventDefault()
    if (!entityId.trim()) return
    setExplainLoading(true)
    setExplainResult(null)
    try {
      const result = await explainEntity(entityId)
      setExplainResult(result)
      loadAnalyses()
    } catch (err) { setError(err instanceof Error ? err.message : t('explain.error')) }
    finally { setExplainLoading(false) }
  }

  if (loading) return <PanelSkeleton message={t('explain.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('explain.title')}</h3>
        <p className="rec-desc">{t('explain.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      {/* Explain form */}
      <form onSubmit={handleExplain} style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem' }}>
        <input className="notif-input" style={{ flex: 1 }} placeholder={t('explain.entity_placeholder')} value={entityId} onChange={e => setEntityId(e.target.value)} required />
        <button type="submit" className="audit-btn" disabled={explainLoading}>
          {explainLoading ? t('explain.analyzing') : t('explain.analyze')}
        </button>
      </form>

      {/* Analysis result */}
      {explainResult && (
        <div style={{ background: 'var(--surface-2)', borderRadius: '10px', padding: '1rem', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
            <h4 style={{ fontSize: '0.95rem' }}>{explainResult.entity_name || explainResult.entity_id}</h4>
            <span className="rec-category-badge">{explainResult.method}</span>
          </div>

          {/* Prediction & Base Value */}
          <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
            <div style={{ textAlign: 'center', flex: 1, background: 'var(--surface)', borderRadius: '8px', padding: '0.75rem' }}>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.3px' }}>Base</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--accent)' }}>{explainResult.base_value.toFixed(1)}</div>
            </div>
            <div style={{ textAlign: 'center', flex: 1, background: 'var(--surface)', borderRadius: '8px', padding: '0.75rem' }}>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.3px' }}>Tahmin</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#f59e0b' }}>{explainResult.prediction.toFixed(1)}</div>
            </div>
            <div style={{ textAlign: 'center', flex: 1, background: 'var(--surface)', borderRadius: '8px', padding: '0.75rem' }}>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.3px' }}>Fark</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#22c55e' }}>
                {(explainResult.prediction - explainResult.base_value) > 0 ? '+' : ''}
                {(explainResult.prediction - explainResult.base_value).toFixed(1)}
              </div>
            </div>
          </div>

          {/* Interpretation */}
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', fontStyle: 'italic', marginBottom: '1rem', padding: '0.5rem', background: 'var(--accent-soft)', borderRadius: '6px' }}>
            {explainResult.interpretation}
          </p>

          {/* Feature Importance */}
          <h5 style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--text-strong)' }}>Feature Importance</h5>
          <div style={{ marginBottom: '1rem' }}>
            {Object.entries(explainResult.feature_importance || {}).map(([feat, weight]) => (
              <div key={feat} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.35rem' }}>
                <span style={{ fontSize: '0.8rem', width: '140px', color: 'var(--text-secondary)' }}>{feat.replace(/_/g, ' ')}</span>
                <div className="bar-track" style={{ flex: 1 }}>
                  <div className="bar-fill" style={{ width: `${(weight as number) * 100}%`, background: 'var(--accent)' }} />
                </div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', width: '45px', textAlign: 'right' }}>
                  {((weight as number) * 100).toFixed(0)}%
                </span>
              </div>
            ))}
          </div>

          {/* SHAP Values */}
          {explainResult.shap_values?.length > 0 && (
            <>
              <h5 style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--text-strong)' }}>{t('explain.shap_values')}</h5>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                {explainResult.shap_values.map((sv: any, i: number) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.4rem 0.5rem', background: 'var(--surface)', borderRadius: '6px' }}>
                    <span style={{
                      width: '8px', height: '8px', borderRadius: '50%',
                      background: IMPACT_COLORS[sv.impact] || 'var(--text-faint)',
                    }} />
                    <span style={{ fontSize: '0.8rem', width: '130px', color: 'var(--text-secondary)' }}>{sv.feature.replace(/_/g, ' ')}</span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>değer: {typeof sv.value === 'number' ? sv.value.toFixed(1) : sv.value}</span>
                    <span style={{
                      fontSize: '0.8rem', fontWeight: 600, marginLeft: 'auto',
                      color: sv.shap > 0 ? '#22c55e' : sv.shap < 0 ? '#ef4444' : 'var(--text-muted)',
                    }}>
                      {sv.shap > 0 ? '+' : ''}{sv.shap.toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* Analyses history */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
        {t('explain.history')} ({analyses.length})
      </h4>
      {analyses.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">🔎</div><h4>{t('explain.empty_title')}</h4></div>
      ) : (
        <div className="rec-list">
          {analyses.map((a, i) => (
            <div key={a.analysis_id || i} className="rec-card" style={{ cursor: 'pointer' }} onClick={() => setExplainResult(a)}>
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: 'var(--accent)' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{a.method}</span>
                  <span className="rec-category-badge">{a.entity_type}</span>
                </div>
                <h4 className="rec-title">{a.entity_name || a.entity_id}</h4>
                <p className="rec-detail">{a.interpretation}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
