import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ScoreDashboard } from './components/ScoreDashboard'
import { login, register } from './api/client'

type Page = 'login' | 'dashboard'

export default function App() {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState<Page>('login')
  const [workspaceId, setWorkspaceId] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [isRegister, setIsRegister] = useState(false)
  const [authError, setAuthError] = useState<string | null>(null)

  const currentLang = i18n.language?.startsWith('en') ? 'en' : 'tr'

  function toggleLang() {
    const newLang = currentLang === 'tr' ? 'en' : 'tr'
    i18n.changeLanguage(newLang)
  }

  async function handleAuth(e: React.FormEvent) {
    e.preventDefault()
    setAuthError(null)
    try {
      const res = isRegister
        ? await register(email, password, name)
        : await login(email, password)
      localStorage.setItem('token', res.token)
      localStorage.setItem('tenant_id', res.tenant_id)
      localStorage.setItem('workspace_id', res.workspace_id)
      setWorkspaceId(res.workspace_id)
      setPage('dashboard')
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : t('auth.failed'))
    }
  }

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('tenant_id')
    localStorage.removeItem('workspace_id')
    setPage('login')
  }

  if (page === 'dashboard') {
    return (
      <div className="app">
        <header className="app-header">
          <h1>{t('app.title')}</h1>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <button className="lang-btn" onClick={toggleLang}>
              {currentLang === 'tr' ? '🇬🇧 EN' : '🇹🇷 TR'}
            </button>
            <button className="logout-btn" onClick={handleLogout}>{t('app.logout')}</button>
          </div>
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
        <button
          onClick={toggleLang}
          className="lang-btn"
          style={{ float: 'right', marginBottom: '0.5rem' }}
        >
          {currentLang === 'tr' ? '🇬🇧 EN' : '🇹🇷 TR'}
        </button>
        <h1>{t('app.platform')}</h1>
        <h2>{isRegister ? t('auth.register') : t('auth.login')}</h2>
        <form onSubmit={handleAuth}>
          <input
            type="email"
            placeholder={t('auth.email_placeholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <input
            type="password"
            placeholder={t('auth.password_placeholder')}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
          {isRegister && (
            <input
              type="text"
              placeholder={t('auth.name_placeholder')}
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          )}
          {authError && <p className="auth-error">{authError}</p>}
          <button type="submit">{isRegister ? t('auth.register') : t('auth.login')}</button>
        </form>
        <p className="auth-toggle">
          <button
            className="link-btn"
            onClick={() => { setIsRegister(!isRegister); setAuthError(null) }}
          >
            {isRegister ? t('auth.already_have_account') : t('auth.no_account')}
          </button>
        </p>
      </div>
    </div>
  )
}
