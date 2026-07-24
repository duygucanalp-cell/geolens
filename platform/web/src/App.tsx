import { useState } from 'react'
import { ScoreDashboard } from './components/ScoreDashboard'
import { login, register } from './api/client'

type Page = 'login' | 'dashboard'

export default function App() {
  const [page, setPage] = useState<Page>('login')
  const [workspaceId, setWorkspaceId] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [isRegister, setIsRegister] = useState(false)
  const [authError, setAuthError] = useState<string | null>(null)

  async function handleAuth(e: React.FormEvent) {
    e.preventDefault()
    setAuthError(null)
    try {
      const res = isRegister
        ? await register(email, password, name)
        : await login(email, password)
      localStorage.setItem('token', res.token)
      localStorage.setItem('tenant_id', res.tenant_id)
      setWorkspaceId(res.workspace_id)
      setPage('dashboard')
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : 'İşlem başarısız')
    }
  }

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('tenant_id')
    setPage('login')
  }

  if (page === 'dashboard') {
    return (
      <div className="app">
        <header className="app-header">
          <h1>GeoLens</h1>
          <button className="logout-btn" onClick={handleLogout}>Çıkış</button>
        </header>
        <main>
          <ScoreDashboard workspaceId={workspaceId} />
        </main>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>GeoLens Platform</h1>
        <h2>{isRegister ? 'Kayıt Ol' : 'Giriş Yap'}</h2>
        <form onSubmit={handleAuth}>
          <input
            type="email"
            placeholder="E-posta"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <input
            type="password"
            placeholder="Şifre"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
          {isRegister && (
            <input
              type="text"
              placeholder="Ad Soyad"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          )}
          {authError && <p className="auth-error">{authError}</p>}
          <button type="submit">{isRegister ? 'Kayıt Ol' : 'Giriş Yap'}</button>
        </form>
        <p className="auth-toggle">
          <button
            className="link-btn"
            onClick={() => { setIsRegister(!isRegister); setAuthError(null) }}
          >
            {isRegister ? 'Zaten hesabın var mı? Giriş yap' : 'Hesabın yok mu? Kayıt ol'}
          </button>
        </p>
      </div>
    </div>
  )
}
