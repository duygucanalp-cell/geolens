import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listPromptSets } from '../api/client'

interface PromptSet {
  id: string
  name: string
  description?: string
  prompt_text: string
  is_active: boolean
}

export function PromptSetsPanel({ workspaceId }: { workspaceId: string }) {
  const { t } = useTranslation()

  const [promptSets, setPromptSets] = useState<PromptSet[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<PromptSet | null>(null)

  useEffect(() => { load() }, [workspaceId])

  async function load() {
    try {
      setLoading(true)
      setError(null)
      const data = await listPromptSets(workspaceId)
      setPromptSets(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('registry.load_error'))
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <div className="dashboard-loading">{t('promptaudit.loading')}</div>
  if (error) return <div className="dashboard-error"><p>{error}</p><button onClick={load}>{t('dashboard.retry')}</button></div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>💬 {t('wizard.step_prompt_set_short')}</h3>
        <p className="rec-desc">{t('wizard.step_prompt_set_desc')}</p>
      </div>

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={load}>{t('common.refresh')}</button>
      </div>

      {selected && (
        <div style={{ background: '#f0f9ff', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <strong style={{ fontSize: '1rem' }}>{selected.name}</strong>
              {selected.description && <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>{selected.description}</p>}
            </div>
            <button className="rec-dismiss-btn" onClick={() => setSelected(null)}>✕</button>
          </div>
          <div style={{ marginTop: '0.75rem', background: 'var(--surface)', padding: '0.75rem', borderRadius: '6px', border: '1px solid #e2e8f0', whiteSpace: 'pre-wrap', fontSize: '0.85rem' }}>
            {selected.prompt_text}
          </div>
          <div className="rec-meta" style={{ marginTop: '0.5rem' }}>
            <span className="rec-category-badge">{selected.is_active ? t('guardrails.enabled') : t('guardrails.disabled')}</span>
            <span className="rec-date">ID: {selected.id}</span>
          </div>
        </div>
      )}

      {promptSets.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">💬</div>
          <h4>{t('guardrails.empty_title')}</h4>
          <p>{t('guardrails.empty_desc')}</p>
        </div>
      ) : (
        <div className="rec-list">
          {promptSets.map(ps => (
            <div
              key={ps.id}
              className="rec-card"
              style={{ cursor: 'pointer', ...(selected?.id === ps.id ? { border: '2px solid #3b82f6' } : {}) }}
              onClick={() => setSelected(ps)}
            >
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: ps.is_active ? '#22c55e' : 'var(--text-faint)' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{ps.is_active ? t('guardrails.enabled') : t('guardrails.disabled')}</span>
                </div>
                <h4 className="rec-title">{ps.name}</h4>
                <p className="rec-detail" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {ps.prompt_text}
                </p>
                {ps.description && <p className="rec-detail" style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>{ps.description}</p>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default PromptSetsPanel