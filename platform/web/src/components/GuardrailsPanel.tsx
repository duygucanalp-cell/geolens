import { useEffect, useState } from 'react'
import { getGuardrailRules, createGuardrailRule, toggleGuardrailRule, deleteGuardrailRule, evaluateGuardrail } from '../api/client'
import type { GuardrailRule } from '../types'

interface Props { workspaceId: string }

const CATEGORY_LABELS: Record<string, string> = { prompt_injection: 'Prompt Injection', pii_leakage: 'PII Sızıntısı', toxic_output: 'Toksik Çıktı', hallucination: 'Halüsinasyon', custom: 'Özel' }
const ACTION_LABELS: Record<string, string> = { block: 'Engelle', flag: 'İşaretle', log: 'Günlükle' }
const SEVERITY_COLORS: Record<string, string> = { critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#22c55e' }

export function GuardrailsPanel({ workspaceId: _ws }: Props) {
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
    catch (e) { setError(e instanceof Error ? e.message : 'Yüklenemedi') }
    finally { setLoading(false) }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    try {
      await createGuardrailRule({ name: newName, category: newCategory, pattern: newPattern })
      setShowCreate(false); setNewName(''); setNewPattern(''); loadRules()
    } catch (e) { setError(e instanceof Error ? e.message : 'Oluşturulamadı') }
  }

  async function handleToggle(id: string, enabled: boolean) {
    try { await toggleGuardrailRule(id, !enabled); loadRules() }
    catch (e) { setError(e instanceof Error ? e.message : 'Güncellenemedi') }
  }

  async function handleDelete(id: string) {
    try { await deleteGuardrailRule(id); loadRules() }
    catch (e) { setError(e instanceof Error ? e.message : 'Silinemedi') }
  }

  async function handleEvaluate() {
    if (!evalPrompt.trim()) return
    try { setEvalResult(await evaluateGuardrail(evalPrompt)) }
    catch (e) { setError(e instanceof Error ? e.message : 'Değerlendirme hatası') }
  }

  if (loading) return <div className="dashboard-loading">Guardrail kuralları yükleniyor...</div>

  return (
    <div className="rec-panel">
      <div className="rec-header">
        <h3>🛡️ Runtime Guardrails</h3>
        <p className="rec-desc">AI prompt/response değerlendirme kuralları.</p>
      </div>
      {error && <div className="audit-error">{error}</div>}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => setShowCreate(!showCreate)}>{showCreate ? 'İptal' : 'Kural Ekle'}</button>
        <button className="refresh-btn" onClick={loadRules}>Yenile</button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: '#f8fafc', padding: '1rem', borderRadius: '10px', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <input className="notif-input" placeholder="Kural adı" value={newName} onChange={e => setNewName(e.target.value)} required />
            <input className="notif-input" placeholder="Pattern (regex veya /keyword/)" value={newPattern} onChange={e => setNewPattern(e.target.value)} required />
            <select value={newCategory} onChange={e => setNewCategory(e.target.value)} className="filter-select">
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <button type="submit" className="audit-btn">Oluştur</button>
          </div>
        </form>
      )}

      {/* Evaluate */}
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem' }}>
        <input className="notif-input" style={{ flex: 1 }} placeholder="Test prompt'u girin..." value={evalPrompt} onChange={e => setEvalPrompt(e.target.value)} />
        <button className="audit-btn" onClick={handleEvaluate}>Test Et</button>
      </div>
      {evalResult && (
        <div style={{ background: evalResult.blocked ? '#fef2f2' : '#f0fdf4', padding: '0.75rem', borderRadius: '8px', marginBottom: '1rem', fontSize: '0.85rem' }}>
          <strong>{evalResult.blocked ? '🔴 Engellendi' : '✅ İzin Verildi'}</strong>
          <ul style={{ marginTop: '0.5rem', listStyle: 'none', padding: 0 }}>
            {evalResult.results.filter((r: any) => r.matched).map((r: any) => (
              <li key={r.rule_id} style={{ padding: '0.2rem 0', color: '#64748b' }}>{r.rule_name} → {r.action_taken}</li>
            ))}
          </ul>
        </div>
      )}

      {rules.length === 0 ? (
        <div className="rec-empty"><div className="rec-empty-icon">🛡️</div><h4>Henüz kural yok</h4><p>Kurallar eklendikçe burada görünecek.</p></div>
      ) : (
        <div className="rec-list">
          {rules.map(r => (
            <div key={r.id} className="rec-card">
              <div className="rec-card-left"><div className="rec-severity-bar" style={{ backgroundColor: SEVERITY_COLORS[r.severity] || '#94a3b8' }} /></div>
              <div className="rec-card-content">
                <div className="rec-card-header">
                  <span className="rec-category-badge">{CATEGORY_LABELS[r.category] || r.category}</span>
                  <span className="rec-severity-badge" style={{ color: SEVERITY_COLORS[r.severity], borderColor: SEVERITY_COLORS[r.severity] }}>{r.severity}</span>
                  <span className="rec-status-badge" style={{ background: r.enabled ? '#dcfce7' : '#fef2f2', color: r.enabled ? '#22c55e' : '#ef4444' }}>{r.enabled ? 'Aktif' : 'Pasif'}</span>
                </div>
                <h4 className="rec-title">{r.name}</h4>
                <p className="rec-detail">Pattern: <code style={{ background: '#e2e8f0', padding: '0.1rem 0.3rem', borderRadius: '3px' }}>{r.pattern}</code></p>
                <div className="rec-meta">
                  <span className="rec-confidence-label">{ACTION_LABELS[r.action] || r.action}</span>
                </div>
              </div>
              <div className="rec-card-actions">
                <button className="rec-apply-btn" onClick={() => handleToggle(r.id, r.enabled)} title={r.enabled ? 'Devre dışı bırak' : 'Etkinleştir'}>
                  {r.enabled ? '⏸' : '▶'}
                </button>
                <button className="rec-dismiss-btn" onClick={() => handleDelete(r.id)} title="Sil">✕</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
