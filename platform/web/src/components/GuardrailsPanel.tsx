import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getGuardrailRules, createGuardrailRule, toggleGuardrailRule, deleteGuardrailRule, evaluateGuardrail } from '../api/client'
import type { GuardrailRule } from '../types'

interface Props { workspaceId: string }

const SEVERITY_COLORS: Record<string, string> = { critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#22c55e' }

export function GuardrailsPanel({ workspaceId: _ws }: Props) {
  const { t } = useTranslation()
  const CATEGORY_LABELS: Record<string, string> = { prompt_injection: t('guardrails.prompt_injection'), pii_leakage: t('guardrails.pii_leakage'), toxic_output: t('guardrails.toxic_output'), hallucination: t('guardrails.hallucination'), custom: t('policy.framework_custom') }
  const ACTION_LABELS: Record<string, string> = { block: t('guardrails.action_block'), flag: t('guardrails.action_flag'), log: t('guardrails.action_log') }
  const [rules, setRules] = useState<GuardrailRule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newCategory, setNewCategory] = useState('prompt_injection')
  const [newPattern, setNewPattern] = useState('')
  const [evalPrompt, setEvalPrompt] = useState('')
  const [evalResult, setEvalResult] = useState<any>(null)

  useEffect(() => { loadRules() }, [])

  async function loadRules() {
    try { setLoading(true); const d = await getGuardrailRules(); setRules(d.rules) }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try {
      await createGuardrailRule({ name: newName, category: newCategory, pattern: newPattern })
      setShowCreate(false); setNewName(''); setNewPattern(''); loadRules()
    } catch (e) { setError(e instanceof Error ? e.message : t('registry.create_error')) }
  }

  async function handleToggle(id: string, enabled: boolean) {
    try { await toggleGuardrailRule(id, !enabled); loadRules() }
    catch (e) { setError(e instanceof Error ? e.message : t('policy.update_error')) }
  }

  async function handleDelete(id: string) {
    try { await deleteGuardrailRule(id); loadRules() }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.delete_error')) }
  }

  async function handleEvaluate() {
    if (!evalPrompt.trim()) return
    try { setEvalResult(await evaluateGuardrail(evalPrompt)) }
    catch (e) { setError(e instanceof Error ? e.message : t('bias.eval_error')) }
  }

  if (loading) return <div className="dashboard-loading">{t('guardrails.loading')}</div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('guardrails.title')}</h3>
        <p className="rec-desc">{t('guardrails.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>{showCreate ? t('guardrails.cancel') : t('guardrails.add_rule')}</button>
        <button className="refresh-btn" onClick={loadRules}>{t('guardrails.refresh')}</button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <input className="notif-input" placeholder={t('guardrails.rule_name_placeholder')} value={newName} onChange={e => setNewName(e.target.value)} required />
            <input className="notif-input" placeholder={t('guardrails.pattern_placeholder')} value={newPattern} onChange={e => setNewPattern(e.target.value)} required />
            <select value={newCategory} onChange={e => setNewCategory(e.target.value)} className="filter-select">
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <button type="submit" className="audit-btn">{t('common.create')}</button>
          </div>
        </form>
      )}

      {/* Evaluate */}
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem' }}>
        <input className="notif-input" style={{ flex: 1 }} placeholder={t('guardrails.evaluate_prompt_hint')} value={evalPrompt} onChange={e => setEvalPrompt(e.target.value)} />
        <button className="audit-btn" onClick={handleEvaluate}>{t('guardrails.evaluate')}</button>
      </div>
      {evalResult && (
        <div style={{ background: evalResult.blocked ? 'var(--danger-bg)' : 'var(--success-bg)', padding: '0.75rem', borderRadius: '8px', marginBottom: '1rem', fontSize: '0.85rem' }}>
          <strong>{evalResult.blocked ? t('guardrails.blocked') : t('guardrails.allowed')}</strong>
          <ul style={{ marginTop: '0.5rem', listStyle: 'none', padding: 0 }}>
            {evalResult.results.filter((r: any) => r.matched).map((r: any) => (
              <li key={r.rule_id} style={{ padding: '0.2rem 0', color: 'var(--text-muted)' }}>{r.rule_name} → {r.action_taken}</li>
            ))}
          </ul>
        </div>
      )}

      {rules.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">🛡️</div><h4>{t('guardrails.empty_title')}</h4><p>{t('guardrails.empty_desc')}</p></div>
      ) : (
        <div className="rec-list">
          {rules.map(r => (
            <div key={r.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: SEVERITY_COLORS[r.severity] || 'var(--text-faint)' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{CATEGORY_LABELS[r.category] || r.category}</span>
                  <span className="rec-severity-badge" style={{ color: SEVERITY_COLORS[r.severity], borderColor: SEVERITY_COLORS[r.severity] }}>{r.severity}</span>
                  <span className="rec-status-badge" style={{ background: r.enabled ? 'var(--success-soft)' : 'var(--danger-bg)', color: r.enabled ? '#22c55e' : '#ef4444' }}>{r.enabled ? t('guardrails.enabled') : t('guardrails.disabled')}</span>
                </div>
                <h4 className="rec-title">{r.name}</h4>
                <p className="rec-detail">Pattern: <code style={{ background: 'var(--border)', padding: '0.1rem 0.3rem', borderRadius: '3px' }}>{r.pattern}</code></p>
                <div className="rec-meta">
                  <span className="rec-confidence-label">{ACTION_LABELS[r.action] || r.action}</span>
                </div>
              </div>
              <div className="rec-card-actions">
                <button className="rec-apply-btn" onClick={() => handleToggle(r.id, r.enabled)} title={r.enabled ? t('guardrails.disable_rule') : t('guardrails.enable_rule')}>
                  {r.enabled ? '⏸' : '▶'}
                </button>
                <button className="rec-dismiss-btn" onClick={() => handleDelete(r.id)} title={t('common.delete')}>✕</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
