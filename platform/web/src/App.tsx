import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ScoreDashboard } from './components/ScoreDashboard'
import { OnboardingWizard } from './components/OnboardingWizard'
import { login, register, acceptInvitation, getSetupStatus, onSessionExpired, onPermissionDenied, refreshSession, logout as apiLogout } from './api/client'

type Page = 'login' | 'onboarding' | 'dashboard' | 'invite'

// Davet e-postasındaki bağlantı #/invite?token=...&email=... biçimindedir.
// Uygulama açılırken hash bu rotaya işaret ediyorsa davet kabul sayfası gösterilir.
function readInviteParams(): { token: string; email: string } | null {
  const hash = window.location.hash
  const match = hash.match(/^#\/invite\?(.*)$/)
  if (!match) return null
  const params = new URLSearchParams(match[1])
  const token = params.get('token') || ''
  const email = params.get('email') || ''
  if (!token || !email) return null
  return { token, email }
}

// Davet kabul sayfasındaki başarı akışından sonra temiz bir URL için kullanılır.
function clearInviteHash() {
  try { window.location.hash = '' } catch { /* yoksay */ }
}

// Oturum süresi dolduğunda kullanıcının kaldığı yeri hatırlamak için kullanılır.
// Tekrar giriş yapıldığında bu konuma geri dönülür (sayfa + workspace).
const RETURN_KEY = 'geolens.return_to'

type ReturnLocation = { page: Page; workspaceId: string }

function readReturnLocation(): ReturnLocation | null {
  try {
    const raw = sessionStorage.getItem(RETURN_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as ReturnLocation
    if (parsed.page !== 'dashboard' && parsed.page !== 'onboarding') return null
    return parsed
  } catch {
    return null
  } finally {
    // Konum yalnızca bir kez kullanılır
    try { sessionStorage.removeItem(RETURN_KEY) } catch { /* yoksay */ }
  }
}

function clearReturnLocation() {
  try { sessionStorage.removeItem(RETURN_KEY) } catch { /* yoksay */ }
}

// Tema: kullanıcı tercihi localStorage'da saklanır; yoksa sistem tercihi kullanılır.
const THEME_KEY = 'geolens.theme'
type Theme = 'light' | 'dark'

function readInitialTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_KEY)
    if (stored === 'light' || stored === 'dark') return stored
  } catch { /* localStorage erişilemiyorsa sistem tercihi */ }
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }
  return 'light'
}

// İlk boyama (paint) öncesinde uygula — tema flaşı (flash of wrong theme) olmasın
const initialTheme = readInitialTheme()
if (typeof document !== 'undefined') {
  document.documentElement.setAttribute('data-theme', initialTheme)
}

export default function App() {
  const { t, i18n } = useTranslation()
  const [theme, setTheme] = useState<Theme>(initialTheme)
  const [page, setPage] = useState<Page>('login')
  const [workspaceId, setWorkspaceId] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [isRegister, setIsRegister] = useState(false)
  const [authError, setAuthError] = useState<string | null>(null)
  const [sessionExpired, setSessionExpired] = useState(false)
  const [inviteToken, setInviteToken] = useState('')
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteName, setInviteName] = useState('')
  const [invitePassword, setInvitePassword] = useState('')
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [inviting, setInviting] = useState(false)

  // Oturum bitmeden önce uyarı + kayan oturum (silent refresh)
  const [showExpiryWarning, setShowExpiryWarning] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [expiryMinutes, setExpiryMinutes] = useState(0)

  // 403 yetki hatası global toast'u
  const [toast, setToast] = useState<string | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const sessionNoticeKind = useRef<'expired' | 'elsewhere'>('expired')

  // Uygulama açılışında davet bağlantısı (hash) varsa davet kabul sayfasına git.
  useEffect(() => {
    const invite = readInviteParams()
    if (invite) {
      setInviteToken(invite.token)
      setInviteEmail(invite.email)
      setPage('invite')
    }
  }, [])

  // Session-expired handler'ında güncel değerleri okuyabilmek için ref'ler
  // (handler eşzamanlı 401'lerde birden çok kez çalışabilir; localStorage
  // ilk çalışmada temizlendiği için ref'ten okunur)
  const pageRef = useRef<Page>('login')
  const workspaceIdRef = useRef('')
  useEffect(() => { pageRef.current = page }, [page])
  useEffect(() => { workspaceIdRef.current = workspaceId }, [workspaceId])

  const currentLang = i18n.language?.startsWith('en') ? 'en' : 'tr'

  // Oturum süresi dolduğunda (API'den 401) oturumu kapat ve login sayfasına dön.
  // 'oturum süresi doldu' uyarısı 3 saniye sonra otomatik kaybolur.
  const sessionNoticeTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  function clearSessionNoticeTimer() {
    if (sessionNoticeTimer.current) {
      clearTimeout(sessionNoticeTimer.current)
      sessionNoticeTimer.current = null
    }
  }

  function showSessionExpiredNotice() {
    setSessionExpired(true)
    clearSessionNoticeTimer()
    sessionNoticeTimer.current = setTimeout(() => {
      setSessionExpired(false)
      sessionNoticeTimer.current = null
    }, 3000)
  }

  // Oturum sona erdiğinde: kaldığı yeri hatırla, oturumu temizle, login'e dön.
  // 401'ler paralel geldiğinde birden çok kez çalışabilir — idempotent olmalı.
  function expireSession(kind: 'expired' | 'elsewhere' = 'expired') {
    if (pageRef.current !== 'login') {
      try {
        sessionStorage.setItem(RETURN_KEY, JSON.stringify({
          page: pageRef.current,
          workspaceId: workspaceIdRef.current,
        }))
      } catch { /* sessionStorage erişilemiyorsa yoksay */ }
    }

    localStorage.removeItem('token')
    localStorage.removeItem('tenant_id')
    localStorage.removeItem('workspace_id')
    localStorage.removeItem('expires_at')
    setWorkspaceId('')
    setShowExpiryWarning(false)
    sessionNoticeKind.current = kind
    showSessionExpiredNotice()
    setPage('login')
  }

  useEffect(() => {
    onSessionExpired(() => expireSession('expired'))

    // 403 (yetki yetersiz): oturum kapanmaz, global toast gösterilir
    onPermissionDenied((message) => {
      setToast(message)
      if (toastTimer.current) clearTimeout(toastTimer.current)
      toastTimer.current = setTimeout(() => setToast(null), 4000)
    })

    return () => {
      onSessionExpired(null)
      onPermissionDenied(null)
      clearSessionNoticeTimer()
      if (toastTimer.current) clearTimeout(toastTimer.current)
    }
  }, [])

  // Çoklu sekme senkronu: başka bir sekme oturumu kapattığında bu sekme de kapanır.
  useEffect(() => {
    function onStorage(e: StorageEvent) {
      if (e.key === 'token' && !e.newValue && localStorage.getItem('token')) {
        expireSession('elsewhere')
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  // Oturum bitmeden önce uyarı + süre dolunca otomatik çıkış (idle kullanıcı için)
  useEffect(() => {
    if (page !== 'dashboard' && page !== 'onboarding') return
    const expiresAt = localStorage.getItem('expires_at')
    if (!expiresAt) return

    function checkExpiry() {
      const remainingMs = new Date(localStorage.getItem('expires_at') || '').getTime() - Date.now()
      if (!Number.isFinite(remainingMs)) return
      if (remainingMs <= 0) {
        expireSession('expired')
        return
      }
      if (remainingMs < 5 * 60 * 1000) {
        setExpiryMinutes(Math.max(1, Math.floor(remainingMs / 60000)))
        setShowExpiryWarning(true)
      } else {
        setShowExpiryWarning(false)
      }
    }

    checkExpiry()
    const interval = setInterval(checkExpiry, 30_000)
    return () => clearInterval(interval)
  }, [page])

  function toggleLang() {
    const newLang = currentLang === 'tr' ? 'en' : 'tr'
    i18n.changeLanguage(newLang)
  }

  // Tema değiştir ve kaydet; sistem tercihi yoksa kullanıcı seçimi geçerli olur
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    try { localStorage.setItem(THEME_KEY, theme) } catch { /* yoksay */ }
  }, [theme])

  // Kullanıcı açıkça tema seçmemişse sistem tercihindeki değişiklikleri takip et
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (!mq || typeof mq.addEventListener !== 'function') return
    const onChange = (e: MediaQueryListEvent) => {
      if (!localStorage.getItem(THEME_KEY)) setTheme(e.matches ? 'dark' : 'light')
    }
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  function toggleTheme() {
    setTheme(prev => (prev === 'dark' ? 'light' : 'dark'))
  }

  function themeButton() {
    return (
      <button
        className="header-icon-btn"
        onClick={toggleTheme}
        aria-label={t('app.theme')}
        title={t('app.theme')}
      >
        {theme === 'dark' ? '☀️' : '🌙'}
      </button>
    )
  }

  async function handleAuth(e: React.FormEvent) {
    e.preventDefault()
    setAuthError(null)
    setSessionExpired(false)
    setShowExpiryWarning(false)
    clearSessionNoticeTimer()
    try {
      const res = isRegister
        ? await register(email, password, name)
        : await login(email, password)
      localStorage.setItem('token', res.token)
      localStorage.setItem('tenant_id', res.tenant_id)
      localStorage.setItem('workspace_id', res.workspace_id)
      localStorage.setItem('expires_at', res.expires_at)
      setWorkspaceId(res.workspace_id)

      // Oturum süresi dolduktan sonra tekrar giriş: kullanıcıyı kaldığı yere götür
      if (!isRegister) {
        const returnTo = readReturnLocation()
        if (returnTo) {
          setWorkspaceId(returnTo.workspaceId || res.workspace_id)
          setPage(returnTo.page)
          return
        }
      } else {
        clearReturnLocation()
      }

      // Check setup status to decide onboarding vs dashboard
      try {
        const status = await getSetupStatus(res.workspace_id)
        setPage(status.setup_complete ? 'dashboard' : 'onboarding')
      } catch {
        setPage('dashboard')
      }
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : t('auth.failed'))
    }
  }

  function handleAcceptInvitation(e: React.FormEvent) {
    e.preventDefault()
    setInviteError(null)
    setInviting(true)
    acceptInvitation(inviteToken, inviteEmail, invitePassword, inviteName)
      .then(async (res) => {
        localStorage.setItem('token', res.token)
        localStorage.setItem('tenant_id', res.tenant_id)
        localStorage.setItem('workspace_id', res.workspace_id)
        localStorage.setItem('expires_at', res.expires_at)
        setWorkspaceId(res.workspace_id)
        clearReturnLocation()
        clearInviteHash()
        try {
          const status = await getSetupStatus(res.workspace_id)
          setPage(status.setup_complete ? 'dashboard' : 'onboarding')
        } catch {
          setPage('dashboard')
        }
      })
      .catch((err) => {
        setInviteError(err instanceof Error ? err.message : t('invite.error'))
        setInviting(false)
      })
  }

  function handleLogout() {
    // Sunucuda token'ı blacklist'e ekle (fire-and-forget — başarısızlık çıkışı engellemesin)
    apiLogout()
    localStorage.removeItem('token')
    localStorage.removeItem('tenant_id')
    localStorage.removeItem('workspace_id')
    localStorage.removeItem('expires_at')
    clearReturnLocation()
    // Manuel çıkışta hatırlanan sekmeyi ve sihirbaz adımını da temizle — temiz bir başlangıç
    // (ScoreDashboard'daki geolens.last_tab ve OnboardingWizard'daki geolens.wizard_step anahtarları)
    try {
      sessionStorage.removeItem('geolens.last_tab')
      sessionStorage.removeItem('geolens.wizard_step')
    } catch { /* yoksay */ }
    setSessionExpired(false)
    clearSessionNoticeTimer()
    setPage('login')
  }

  function handleSetupComplete() {
    setPage('dashboard')
  }

  // Kayan oturum: süre dolmadan önce token'ı yenile
  async function handleExtendSession() {
    setRefreshing(true)
    try {
      const res = await refreshSession()
      localStorage.setItem('token', res.token)
      localStorage.setItem('expires_at', res.expires_at)
      if (res.tenant_id) localStorage.setItem('tenant_id', res.tenant_id)
      if (res.workspace_id) localStorage.setItem('workspace_id', res.workspace_id)
      setShowExpiryWarning(false)
    } catch {
      // Token gerçekten süresi dolmuşsa 401 akışı zaten oturumu kapatmıştır
    } finally {
      setRefreshing(false)
    }
  }

  function renderSessionExtend() {
    if (!showExpiryWarning) return null
    return (
      <div className="session-warning">
        <span>⏳ {t('session.expiry_warning', { minutes: expiryMinutes })}</span>
        <button className="session-warning-btn" onClick={handleExtendSession} disabled={refreshing}>
          {refreshing ? t('session.extending') : t('session.extend')}
        </button>
      </div>
    )
  }

  if (page === 'invite') {
    return (
      <div className="auth-page">
        <div className="auth-shell">
          {authHero()}
          <div className="auth-card">
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.4rem', marginBottom: '0.5rem' }}>
              {themeButton()}
              <button onClick={toggleLang} className="lang-btn">
                {currentLang === 'tr' ? '🇬🇧 EN' : '🇹🇷 TR'}
              </button>
            </div>
            <div className="auth-brand-row">
              <div className="auth-brand-logo" aria-hidden="true">G</div>
              <span className="auth-brand-name">{t('app.platform')}</span>
            </div>
            <h2>{t('invite.title')}</h2>
            <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
              {t('invite.subtitle')} <strong>{inviteEmail}</strong>
            </p>
            <form onSubmit={handleAcceptInvitation}>
              <input
                type="text"
                placeholder={t('auth.name_placeholder')}
                value={inviteName}
                onChange={(e) => setInviteName(e.target.value)}
                required
              />
              <input
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                required
                readOnly
              />
              <input
                type="password"
                placeholder={t('invite.password_placeholder')}
                value={invitePassword}
                onChange={(e) => setInvitePassword(e.target.value)}
                required
                minLength={8}
              />
              {inviteError && <p className="auth-error">{inviteError}</p>}
              <button type="submit" disabled={inviting}>
                {inviting ? t('invite.accepting') : t('invite.accept')}
              </button>
            </form>
          </div>
        </div>
      </div>
    )
  }

  // Giriş sayfasındaki marka/özellik paneli (iki panelli düzenin sol tarafı)
  function authHero() {
    return (
      <div className="auth-hero" aria-hidden="true">
        <div className="auth-hero-content">
          <div className="auth-hero-logo">
            <div className="auth-brand-logo">G</div>
            <span className="auth-brand-name">{t('app.platform')}</span>
          </div>
          <h1 className="auth-hero-title">{t('auth.hero_title')}</h1>
          <p className="auth-hero-desc">{t('auth.hero_desc')}</p>
          <ul className="auth-hero-features">
            <li><span>📊</span>{t('auth.hero_feature_1')}</li>
            <li><span>🤖</span>{t('auth.hero_feature_2')}</li>
            <li><span>🛡️</span>{t('auth.hero_feature_3')}</li>
            <li><span>📈</span>{t('auth.hero_feature_4')}</li>
          </ul>
        </div>
      </div>
    )
  }

  // E-postadan baş harf avatarı üretir (adem@x.com → 'A', a.b.kaya@x.com → 'AB')
  function emailInitials(mail: string): string {
    const local = mail.split('@')[0] || ''
    const parts = local.split(/[._\-]+/).filter(Boolean)
    const first = (parts[0]?.[0] || local[0] || '?').toUpperCase()
    if (parts.length >= 2) return first + (parts[1][0] || '').toUpperCase()
    return first
  }

  // Profesyonel üst çubuk: logo + uygulama adı + kullanıcı avatarı + eylemler
  function appHeader() {
    return (
      <header className="app-header">
        <div className="app-header-brand">
          <div className="app-header-logo" aria-hidden="true">G</div>
          <div>
            <div className="app-header-title">{t('app.title')}</div>
            <div className="app-header-sub">{t('app.tagline')}</div>
          </div>
        </div>
        <div className="app-header-actions">
          {email && (
            <div className="app-header-user" title={`${email} · ${t('app.workspace')}`}>
              <span className="app-header-avatar" aria-hidden="true">{emailInitials(email)}</span>
              <span className="app-header-email-wrap">
                <span className="app-header-user-email">{email}</span>
                <span className="app-header-user-role">{t('app.workspace')}</span>
              </span>
            </div>
          )}
          {themeButton()}
          <button
            className="lang-btn"
            onClick={toggleLang}
            aria-label={t('app.switch_language')}
            title={t('app.switch_language')}
          >
            {currentLang === 'tr' ? '🇬🇧 EN' : '🇹🇷 TR'}
          </button>
          <button className="logout-btn" onClick={handleLogout}>{t('app.logout')}</button>
        </div>
      </header>
    )
  }

  if (page === 'onboarding') {
    return (
      <div className="app">
        {appHeader()}
        {renderSessionExtend()}
        {toast && <div className="app-toast" role="status">{toast}</div>}
        <main>
          <OnboardingWizard workspaceId={workspaceId} onComplete={handleSetupComplete} />
        </main>
      </div>
    )
  }

  if (page === 'dashboard') {
    return (
      <div className="app">
        {appHeader()}
        {renderSessionExtend()}
        {toast && <div className="app-toast" role="status">{toast}</div>}
        <main>
          <ScoreDashboard workspaceId={workspaceId} />
        </main>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <div className="auth-shell">
        {authHero()}
        <div className="auth-card">
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.4rem', marginBottom: '0.5rem' }}>
            {themeButton()}
            <button
              onClick={toggleLang}
              className="lang-btn"
            >
              {currentLang === 'tr' ? '🇬🇧 EN' : '🇹🇷 TR'}
            </button>
          </div>
          <div className="auth-brand-row">
            <div className="auth-brand-logo" aria-hidden="true">G</div>
            <span className="auth-brand-name">{t('app.platform')}</span>
          </div>
          <h2>{isRegister ? t('auth.register') : t('auth.login')}</h2>
          <p className="auth-subtitle">{isRegister ? t('auth.register_subtitle') : t('auth.login_subtitle')}</p>
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
            {sessionExpired && (
              <p className="auth-notice">
                {sessionNoticeKind.current === 'elsewhere' ? t('auth.session_expired_elsewhere') : t('auth.session_expired')}
              </p>
            )}
            {authError && <p className="auth-error">{authError}</p>}
            <button type="submit">{isRegister ? t('auth.register') : t('auth.login')}</button>
          </form>
          <p className="auth-toggle">
            <button
              className="link-btn"
              onClick={() => { setIsRegister(!isRegister); setAuthError(null); setSessionExpired(false); clearSessionNoticeTimer() }}
            >
              {isRegister ? t('auth.already_have_account') : t('auth.no_account')}
            </button>
          </p>
        </div>
      </div>
    </div>
  )
}
