import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from './i18n'
import App from './App'

function jsonRes(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    blob: async () => new Blob(),
  } as Response
}

const loginResponse = {
  token: 'test-token',
  expires_at: '2099-01-01T00:00:00Z',
  user_id: 'u1',
  tenant_id: 't1',
  workspace_id: 'ws1',
  role: 'admin',
}

describe('App — session restore (kaldığı yere dön)', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
    localStorage.clear()
    sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  let config: { setupComplete: boolean; dashboardStatus: number }

  const ALL_STEPS = ['brand', 'panel', 'prompt_set', 'measurement']

  function setupStatusBody(setupComplete: boolean) {
    const doneKeys = setupComplete ? ALL_STEPS : []
    return {
      setup_complete: setupComplete,
      steps: ALL_STEPS.map(key => ({ key, label: key, done: doneKeys.includes(key) })),
    }
  }

  function stubFetch(cfg: { setupComplete?: boolean; dashboardStatus?: number } = {}) {
    config = { setupComplete: true, dashboardStatus: 200, ...cfg }
    vi.stubGlobal('fetch', vi.fn((url: RequestInfo | URL) => {
      const u = String(url)
      if (u.includes('/auth/login')) {
        return Promise.resolve(jsonRes(200, loginResponse))
      }
      if (u.includes('/setup-status')) {
        return Promise.resolve(jsonRes(200, setupStatusBody(config.setupComplete)))
      }
      if (u.includes('/scores') || u.includes('/brands') || u.includes('/panels')) {
        return config.dashboardStatus === 200
          ? Promise.resolve(jsonRes(200, []))
          : Promise.resolve(jsonRes(401, { error: 'authentication_required' }))
      }
      return Promise.resolve(jsonRes(404, { error: 'not_found' }))
    }))
  }

  async function loginUser(user = userEvent.setup()) {
    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com')
    await user.type(screen.getByPlaceholderText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Sign In' }))
  }

  it('oturum süresi dolduktan sonra tekrar girişte dashboarda geri döner', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<App />)

    // İlk giriş → dashboard
    await loginUser(user)
    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.return_to')).toBeNull()

    // Oturum süresi dolsun: dashboard veri çağrıları 401 dönmeye başlar
    stubFetch({ dashboardStatus: 401 })
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    // Login sayfasına döner + 'oturum süresi doldu' bildirimi görünür
    await waitFor(() => {
      expect(screen.getByPlaceholderText('Email')).toBeInTheDocument()
    })
    expect(screen.getByText(/session has expired/i)).toBeInTheDocument()

    // Kaldığı yer kaydedilmiş olmalı
    const saved = JSON.parse(sessionStorage.getItem('geolens.return_to') || '{}')
    expect(saved.page).toBe('dashboard')
    expect(saved.workspaceId).toBe('ws1')

    // Setup henüz tamamlanmamış olsa bile (setupComplete=false) tekrar girişte
    // kullanıcı onboarding'e değil, kaldığı dashboard'a götürülür
    stubFetch({ setupComplete: false, dashboardStatus: 200 })
    await user.click(screen.getByRole('button', { name: 'Sign In' }))

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })
    expect(screen.queryByText('Welcome to GeoLens')).not.toBeInTheDocument()
    // Konum bir kez kullanılır
    expect(sessionStorage.getItem('geolens.return_to')).toBeNull()
  })

  it('manuel çıkış yapılınca kayıtlı konum temizlenir', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<App />)

    await loginUser(user)
    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    // Kaldığı yer varsa da manuel logout onu temizler
    sessionStorage.setItem('geolens.return_to', JSON.stringify({ page: 'dashboard', workspaceId: 'ws1' }))
    await user.click(screen.getByRole('button', { name: 'Logout' }))

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Email')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.return_to')).toBeNull()
    expect(sessionStorage.getItem('geolens.last_tab')).toBeNull()
  })

  it('onboarding sırasında oturum biterse aynı sihirbaz adımına geri döner', async () => {
    const user = userEvent.setup()

    // İlk giriş → kurulum tamamlanmamış olduğu için onboarding sayfası
    stubFetch({ setupComplete: false, dashboardStatus: 200 })
    render(<App />)
    await loginUser(user)
    await waitFor(() => {
      expect(screen.getByText('Welcome to GeoLens')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('brand')

    // Kullanıcı prompt_set adımına ilerlemiş olsun (adım kaydetme davranışı
    // OnboardingWizard.test.tsx içinde ayrıca kapsanıyor)
    sessionStorage.setItem('geolens.wizard_step', 'prompt_set')

    // Oturum süresi dolsun: marka oluşturma isteği 401 döner → session expired
    vi.stubGlobal('fetch', vi.fn((url: RequestInfo | URL) => {
      const u = String(url)
      if (u.includes('/auth/login')) return Promise.resolve(jsonRes(200, loginResponse))
      return Promise.resolve(jsonRes(401, { error: 'authentication_required' }))
    }))
    await user.type(screen.getByPlaceholderText('Brand name (e.g. Acme Corp)'), 'Acme')
    await user.type(screen.getByPlaceholderText('Website URL (e.g. https://acme.com)'), 'https://acme.com')
    await user.click(screen.getByRole('button', { name: 'Add Brand' }))

    // Login sayfasına döner ve onboarding konumu kaydedilir
    await waitFor(() => {
      expect(screen.getByPlaceholderText('Email')).toBeInTheDocument()
    })
    const saved = JSON.parse(sessionStorage.getItem('geolens.return_to') || '{}')
    expect(saved.page).toBe('onboarding')
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('prompt_set')

    // Tekrar giriş → kaldığı onboarding sayfasına + prompt_set adımına döner
    stubFetch({ setupComplete: false, dashboardStatus: 200 })
    await user.click(screen.getByRole('button', { name: 'Sign In' }))
    await waitFor(() => {
      expect(screen.getByPlaceholderText('Prompt name (e.g. Default Prompt)')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('prompt_set')
  })

  it('başarısız giriş denemesi kayıtlı konumu tüketmez', async () => {
    stubFetch({ dashboardStatus: 401 })
    const user = userEvent.setup()
    render(<App />)

    // Oturum süresi dolmuş olsun: giriş sayfasında, kayıtlı konum olsun
    sessionStorage.setItem('geolens.return_to', JSON.stringify({ page: 'dashboard', workspaceId: 'ws1' }))

    // Yanlış kimlik bilgisi: login endpoint 401 döner
    vi.stubGlobal('fetch', vi.fn((url: RequestInfo | URL) => {
      const u = String(url)
      if (u.includes('/auth/login')) {
        return Promise.resolve(jsonRes(401, { error: 'geçersiz e-posta veya şifre' }))
      }
      return Promise.resolve(jsonRes(404, { error: 'not_found' }))
    }))

    await user.type(screen.getByPlaceholderText('Email'), 'user@example.com')
    await user.type(screen.getByPlaceholderText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Sign In' }))

    // Hata mesajı görünür, konum korunur
    await waitFor(() => {
      expect(screen.getByText('geçersiz e-posta veya şifre')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.return_to')).not.toBeNull()

    // Doğru bilgilerle tekrar giriş → kaldığı yere döner
    stubFetch({ setupComplete: false, dashboardStatus: 200 })
    await user.type(screen.getByPlaceholderText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Sign In' }))

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })
    expect(screen.queryByText('Welcome to GeoLens')).not.toBeInTheDocument()
    expect(sessionStorage.getItem('geolens.return_to')).toBeNull()
  })

  it('başka bir sekme oturumu kapatınca bu sekme de kapanır (çoklu sekme senkronu)', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<App />)

    await loginUser(user)
    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    // Başka bir sekme token'ı sildi: storage event bu sekmeye ulaşır,
    // bu sekmede token hâlâ duruyor → oturum kapatılmalı
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'token',
      oldValue: 'test-token',
      newValue: null,
    }))

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Email')).toBeInTheDocument()
    })
    expect(localStorage.getItem('token')).toBeNull()
    expect(screen.getByText(/closed in another tab/i)).toBeInTheDocument()
  })

  it('oturum bitmeden önce uyarı gösterir, devam edilince token yenilenir', async () => {
    const soon = new Date(Date.now() + 2 * 60 * 1000).toISOString()
    vi.stubGlobal('fetch', vi.fn((url: RequestInfo | URL) => {
      const u = String(url)
      if (u.includes('/auth/login')) {
        return Promise.resolve(jsonRes(200, { ...loginResponse, expires_at: soon }))
      }
      if (u.includes('/auth/refresh')) {
        return Promise.resolve(jsonRes(200, { ...loginResponse, token: 'new-token', expires_at: '2099-01-01T00:00:00Z' }))
      }
      if (u.includes('/setup-status')) {
        return Promise.resolve(jsonRes(200, setupStatusBody(true)))
      }
      if (u.includes('/scores') || u.includes('/brands') || u.includes('/panels')) {
        return Promise.resolve(jsonRes(200, []))
      }
      return Promise.resolve(jsonRes(404, { error: 'not_found' }))
    }))

    const user = userEvent.setup()
    render(<App />)
    await loginUser(user)
    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    // Uyarı görünür (birkaç dakika kaldı — hesaplama anına göre 1-2 dk olabilir)
    await waitFor(() => {
      expect(screen.getByText(/session expires in \d+ minute/i)).toBeInTheDocument()
    })

    // Devam Et → token yenilenir, uyarı kaybolur
    await user.click(screen.getByRole('button', { name: 'Continue Session' }))
    await waitFor(() => {
      expect(screen.queryByText(/session expires in \d+ minute/i)).not.toBeInTheDocument()
    })
    expect(localStorage.getItem('token')).toBe('new-token')
  })
})

describe('App — karanlık tema', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
    localStorage.clear()
    sessionStorage.clear()
    vi.unstubAllGlobals()
    document.documentElement.setAttribute('data-theme', 'light')
  })

  it('tema butonu karanlık moda geçirir, tercihi localStorage\'a kaydeder ve geri çevirir', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonRes(200, {}))))
    const user = userEvent.setup()
    render(<App />)

    // Login sayfasındaki tema butonu
    const themeBtn = screen.getByRole('button', { name: 'Toggle theme' })
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')

    await user.click(themeBtn)
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(localStorage.getItem('geolens.theme')).toBe('dark')

    await user.click(screen.getByRole('button', { name: 'Toggle theme' }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    expect(localStorage.getItem('geolens.theme')).toBe('light')
  })
})
