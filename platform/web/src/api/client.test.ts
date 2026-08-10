import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ApiError, isPermissionDenied, onSessionExpired, onPermissionDenied, getScores, login, refreshSession, logout } from './client'

// fetch'i stub'lamak için yardımcı
function mockFetch(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    blob: async () => new Blob(),
  }))
}

describe('client API auth handling', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('401 yanıtında session expired handler tetiklenir (oturum süresi doldu)', async () => {
    localStorage.setItem('token', 'expired-token')
    mockFetch(401, { error: 'authentication_required' })

    const handler = vi.fn()
    onSessionExpired(handler)

    await expect(getScores('WS1')).rejects.toThrow(ApiError)
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('401 yanıtı ApiError olarak fırlatılır ve durum kodu 401 olur', async () => {
    localStorage.setItem('token', 'expired-token')
    mockFetch(401, { error: 'invalid_token' })

    const err = await getScores('WS1').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(401)
  })

  it('login endpoint 401 döndüğünde session expired handler tetiklenmez (yanlış kimlik bilgisi)', async () => {
    mockFetch(401, { error: 'geçersiz e-posta veya şifre' })

    const handler = vi.fn()
    onSessionExpired(handler)

    const err = await login('user@example.com', 'wrong-password').catch((e: unknown) => e)
    expect(handler).not.toHaveBeenCalled()
    // Sunucunun verdiği okunabilir mesaj korunur, 'session expired' ile eşleşmez
    expect((err as Error).message).toBe('geçersiz e-posta veya şifre')
  })

  it('bilinmeyen kodlu hata mesajı olduğu gibi korunur', async () => {
    localStorage.setItem('token', 'expired-token')
    mockFetch(400, { error: 'some_unknown_error_code' })

    const err = await getScores('WS1').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as Error).message).toBe('some_unknown_error_code')
  })

  it('403 yanıtı panel içi gösterim için boş mesajla fırlatılır (gösterimi toast yönetir)', async () => {
    localStorage.setItem('token', 'valid-token')
    mockFetch(403, { error: 'insufficient_permissions' })

    const err = await getScores('WS1').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(403)
    // Panel içi {error && ...} kalıbı boş mesajı göstermez → yalnızca toast görünür
    expect((err as ApiError).message).toBe('')
  })

  it('403 yanıtında session expired handler tetiklenmez (yetki yetersiz, oturum açık kalır)', async () => {
    localStorage.setItem('token', 'valid-token')
    mockFetch(403, { error: 'insufficient_permissions' })

    const handler = vi.fn()
    onSessionExpired(handler)

    const err = await getScores('WS1').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(403)
    expect(handler).not.toHaveBeenCalled()
  })

  it('token yokken 401 session expired handler tetiklenmez', async () => {
    mockFetch(401, { error: 'authentication_required' })

    const handler = vi.fn()
    onSessionExpired(handler)

    await expect(getScores('WS1')).rejects.toThrow()
    expect(handler).not.toHaveBeenCalled()
  })

  it('handler null ile kayıttan kaldırılabilir', async () => {
    localStorage.setItem('token', 'expired-token')
    mockFetch(401, { error: 'authentication_required' })

    const handler = vi.fn()
    onSessionExpired(handler)
    onSessionExpired(null)

    await expect(getScores('WS1')).rejects.toThrow()
    expect(handler).not.toHaveBeenCalled()
  })
})

describe('client API — silent refresh & permission denied', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    onSessionExpired(null)
    onPermissionDenied(null)
  })

  it('refreshSession /v1/auth/refresh adresine token ile POST atar', async () => {
    localStorage.setItem('token', 'valid-token')
    mockFetch(200, { token: 'new-token', expires_at: '2099-01-01T00:00:00Z', user_id: 'u1', tenant_id: 't1', workspace_id: 'ws1', role: 'admin' })

    const res = await refreshSession()
    expect(res.token).toBe('new-token')
    const call = vi.mocked(fetch).mock.calls[0]
    expect(call[0]).toBe('/v1/auth/refresh')
    expect((call[1] as RequestInit).method).toBe('POST')
    expect((call[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer valid-token' })
  })

  it('refreshSession 401 dönerse session expired handler tetiklenir (süre gerçekten doldu)', async () => {
    localStorage.setItem('token', 'expired-token')
    mockFetch(401, { error: 'authentication_required' })

    const handler = vi.fn()
    onSessionExpired(handler)

    await expect(refreshSession()).rejects.toThrow(ApiError)
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('403 yanıtında permission denied handler dostu mesajı alır, session handler tetiklenmez', async () => {
    localStorage.setItem('token', 'valid-token')
    mockFetch(403, { error: 'insufficient_permissions' })

    const sessionHandler = vi.fn()
    const deniedHandler = vi.fn()
    onSessionExpired(sessionHandler)
    onPermissionDenied(deniedHandler)

    const err = await getScores('WS1').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(403)
    expect(deniedHandler).toHaveBeenCalledTimes(1)
    // Toast, ham kodu değil kullanıcı dostu çeviriyi gösterir
    const toastMessage = deniedHandler.mock.calls[0][0]
    expect(toastMessage).not.toBe('insufficient_permissions')
    expect(toastMessage).not.toBe('')
    expect(sessionHandler).not.toHaveBeenCalled()
  })

  it('onPermissionDenied null ile kayıttan kaldırılabilir', async () => {
    localStorage.setItem('token', 'valid-token')
    mockFetch(403, { error: 'insufficient_permissions' })

    const deniedHandler = vi.fn()
    onPermissionDenied(deniedHandler)
    onPermissionDenied(null)

    await expect(getScores('WS1')).rejects.toThrow(ApiError)
    expect(deniedHandler).not.toHaveBeenCalled()
  })

  it('logout() sunucu hatasını yutar (fire-and-forget)', async () => {
    localStorage.setItem('token', 'valid-token')
    mockFetch(500, { error: 'server_error' })

    await expect(logout()).resolves.toBeNull()
  })

  it('isPermissionDenied yardımcısı yalnızca 403 ApiError için true döner', async () => {
    localStorage.setItem('token', 'valid-token')

    mockFetch(403, { error: 'insufficient_permissions' })
    const err403 = await getScores('WS1').catch((e: unknown) => e)
    expect(isPermissionDenied(err403)).toBe(true)

    mockFetch(500, { error: 'server_error' })
    const err500 = await getScores('WS1').catch((e: unknown) => e)
    expect(isPermissionDenied(err500)).toBe(false)
  })
})
