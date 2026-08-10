import { PanelSkeleton } from './PanelSkeleton'
import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { listRedTeamCases, createRedTeamCase, deleteRedTeamCase, runRedTeam, listRedTeamRuns } from '../api/client'
import type { RedTeamCase, RedTeamRun, RedTeamResult } from '../types'

interface Props { workspaceId: string }

const SEVERITY_COLORS: Record<string, string> = { critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#22c55e' }

export function RedTeamPanel({ workspaceId: _ws }: Props) {
  const { t } = useTranslation()
  const CATEGORY_LABELS: Record<string, string> = {
    prompt_injection: t('redteam.cat_injection'), jailbreak: t('redteam.cat_jailbreak'),
    roleplay: t('redteam.cat_roleplay'), encoding: t('redteam.cat_encoding'),
    pii_extraction: t('redteam.cat_pii'), misinformation: t('redteam.cat_misinfo'),
    refusal_override: t('redteam.cat_refusal'), custom: t('policy.framework_custom'),
  }
  const [cases, setCases] = useState<RedTeamCase[]>([])
  const [runs, setRuns] = useState<RedTeamRun[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newCategory, setNewCategory] = useState('prompt_injection')
  const [newPayload, setNewPayload] = useState('')
  const [targetName, setTargetName] = useState('')
  const [targetPrompt, setTargetPrompt] = useState('')
  const [runResult, setRunResult] = useState<{ run: RedTeamRun; results: RedTeamResult[]; defense_score: number; passed: number; failed: number } | null>(null)
  const [running, setRunning] = useState(false)

  useEffect(() => { loadAll() }, [])

  async function loadAll() {
    try {
      setLoading(true)
      const [c, r] = await Promise.all([listRedTeamCases(), listRedTeamRuns()])
      setCases(c.cases)
      setRuns(r.runs)
    } catch (e) { setError(e instanceof Error ? e.message : t('registry.load_error')) }
    finally { setLoading(false) }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try {
      await createRedTeamCase({ name: newName, category: newCategory, payload: newPayload })
      setShowCreate(false); setNewName(''); setNewPayload(''); loadAll()
    } catch (e) { setError(e instanceof Error ? e.message : t('registry.create_error')) }
  }

  async function handleDelete(id: string) {
    try { await deleteRedTeamCase(id); loadAll() }
    catch (e) { setError(e instanceof Error ? e.message : t('registry.delete_error')) }
  }

  async function handleRun() {
    if (!targetName.trim() && !targetPrompt.trim()) return
    setRunning(true)
    setError(null)
    try {
      const res = await runRedTeam(targetName || 'varsayılan hedef', targetPrompt)
      setRunResult(res)
      loadAll()
    } catch (e) { setError(e instanceof Error ? e.message : t('redteam.run_error')) }
    finally { setRunning(false) }
  }

  if (loading) return <PanelSkeleton message={t('redteam.loading')} />

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>{t('redteam.title')}</h3>
        <p className="rec-desc">{t('redteam.desc')}</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      {/* Run form */}
      <form onSubmit={(e) => { e.preventDefault(); handleRun() }} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input className="notif-input" style={{ flex: 1 }} placeholder={t('redteam.target_name')} value={targetName} onChange={e => setTargetName(e.target.value)} />
            <button type="submit" className="audit-btn" disabled={running}>
              {running ? t('redteam.running') : t('redteam.run_test')}
            </button>
          </div>
          <textarea className="notif-input" rows={3} placeholder={t('redteam.target_prompt')} value={targetPrompt} onChange={e => setTargetPrompt(e.target.value)} />
        </div>
      </form>

      {/* Run result */}
      {runResult && (
        <div style={{ marginBottom: '1.5rem' }}>
          <div style={{
            textAlign: 'center', padding: '1.5rem', borderRadius: '12px', marginBottom: '1rem',
            background: runResult.defense_score >= 80 ? 'var(--success-bg)' : runResult.defense_score >= 50 ? 'var(--amber-bg)' : 'var(--danger-bg)',
          }}>
            <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>
              {runResult.defense_score >= 80 ? '🛡️' : runResult.defense_score >= 50 ? '⚠️' : '🔴'}
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 700, color: runResult.defense_score >= 80 ? '#22c55e' : runResult.defense_score >= 50 ? '#f59e0b' : '#ef4444' }}>
              {runResult.defense_score} / 100
            </div>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
              {t('redteam.defense_score')}: {runResult.passed}/{runResult.failed + runResult.passed} {t('redteam.caught')}
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {runResult.results.map((r, i) => (
              <div key={r.case_id + i} style={{
                display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem',
                background: r.outcome === 'passed' ? 'var(--success-bg)' : 'var(--danger-bg)', borderRadius: '8px',
              }}>
                <span style={{ fontSize: '1.1rem' }}>{r.outcome === 'passed' ? '✅' : '❌'}</span>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: '0.85rem' }}>{CATEGORY_LABELS[r.category] || r.category}</strong>
                  <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                    {r.outcome === 'passed' ? t('redteam.caught_by') + ': ' + (r.matched_rule || '-') : t('redteam.not_caught')}
                  </p>
                </div>
                <span className="rec-severity-badge" style={{ color: SEVERITY_COLORS[r.risk_level] || 'var(--text-faint)', borderColor: SEVERITY_COLORS[r.risk_level] || 'var(--text-faint)' }}>{r.risk_level}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>{showCreate ? t('guardrails.cancel') : t('redteam.add_case')}</button>
        <button className="refresh-btn" onClick={loadAll}>{t('guardrails.refresh')}</button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: 'var(--surface-2)', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <input className="notif-input" placeholder={t('redteam.case_name')} value={newName} onChange={e => setNewName(e.target.value)} required />
            <textarea className="notif-input" rows={3} placeholder={t('redteam.case_payload')} value={newPayload} onChange={e => setNewPayload(e.target.value)} required />
            <select value={newCategory} onChange={e => setNewCategory(e.target.value)} className="filter-select">
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <button type="submit" className="audit-btn">{t('redteam.create')}</button>
          </div>
        </form>
      )}

      {/* Runs history */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
        {t('redteam.run_history')} ({runs.length})
      </h4>
      {runs.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">🥊</div><h4>{t('redteam.no_runs')}</h4></div>
      ) : (
        <div className="rec-list">
          {runs.slice(0, 10).map(r => (
            <div key={r.id} className="rec-card">
              <div className="rec-card-left">
                <div className="rec-severity-bar" style={{ backgroundColor: r.defense_score >= 80 ? '#22c55e' : r.defense_score >= 50 ? '#f59e0b' : '#ef4444' }} />
              </div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{r.target_name}</span>
                  <span className="rec-severity-badge">{r.defense_score} / 100</span>
                </div>
                <div className="rec-meta">
                  <span className="rec-date">{r.passed}/{r.total_cases} {t('redteam.caught')}</span>
                  <span className="rec-date">{new Date(r.created_at).toLocaleString()}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Test cases */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, margin: '1rem 0 0.75rem', color: 'var(--text-strong)' }}>
        {t('redteam.test_cases')} ({cases.length})
      </h4>
      {cases.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">🥊</div><h4>{t('redteam.no_cases')}</h4></div>
      ) : (
        <div className="rec-list">
          {cases.map(c => (
            <div key={c.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: SEVERITY_COLORS[c.severity] || 'var(--text-faint)' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{CATEGORY_LABELS[c.category] || c.category}</span>
                  <span className="rec-severity-badge" style={{ color: SEVERITY_COLORS[c.severity], borderColor: SEVERITY_COLORS[c.severity] }}>{c.severity}</span>
                </div>
                <h4 className="rec-title">{c.name}</h4>
                <p className="rec-detail" style={{ fontSize: '0.78rem' }}>{c.payload}</p>
              </div>
              <div className="rec-card-actions">
                <button className="rec-dismiss-btn" onClick={() => handleDelete(c.id)} title={t('common.delete')}>✕</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
