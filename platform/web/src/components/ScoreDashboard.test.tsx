import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../i18n'
import { ScoreDashboard, VALID_TABS, TAB_GROUPS } from './ScoreDashboard'

function jsonRes(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    blob: async () => new Blob(),
  } as Response
}

describe('ScoreDashboard — gruplu navigasyon', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
    localStorage.clear()
    sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  function stubFetch() {
    vi.stubGlobal('fetch', vi.fn((url: RequestInfo | URL) => {
      const u = String(url)
      if (u.includes('/scores')) return Promise.resolve(jsonRes(200, []))
      if (u.includes('/brands')) return Promise.resolve(jsonRes(200, []))
      if (u.includes('/panels')) return Promise.resolve(jsonRes(200, []))
      if (u.includes('/benchmark/context')) {
        return Promise.resolve(jsonRes(200, { my_score: 0, tenant_count: 0, sufficient_data: false }))
      }
      return Promise.resolve(jsonRes(200, {}))
    }))
  }

  it('navigasyon tetikleyicisi aktif sekme etiketini gösterir', async () => {
    stubFetch()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })
    expect(screen.getByText(/☰ Scores/)).toBeInTheDocument()
  })

  it('menü açılınca gruplar görünür, aktif sekme vurgulanır', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))

    expect(screen.getByText('📊 Measurement & Reports')).toBeInTheDocument()
    expect(screen.getByText('🌐 GEO & AI Visibility')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Scores' })).toHaveClass('active')
  })

  it('menüden sekme seçilince aktif sekme değişir ve sessionStorage kaydedilir', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    await user.click(screen.getByRole('menuitem', { name: 'Reports' }))

    expect(sessionStorage.getItem('geolens.last_tab')).toBe('reports')
    // Menü kapanır ve tetikleyici yeni aktif sekmeyi gösterir
    await waitFor(() => {
      expect(screen.getByText(/☰ Reports/)).toBeInTheDocument()
    })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('açılışta yalnızca aktif grubu içeren grup açıktır; grup başlığı ile genişletilir', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))

    // Aktif grup (Measurement) açık, diğer gruplar kapalı
    expect(screen.getByRole('menuitem', { name: 'Scores' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: '🛡️ Guardrails' })).not.toBeInTheDocument()

    // AI Governance grubunu genişlet
    await user.click(screen.getByText('🛡 AI Governance'))
    expect(screen.getByRole('menuitem', { name: '🛡️ Guardrails' })).toBeInTheDocument()
  })

  it('her sekme tam olarak bir grupta yer alır (VALID_TABS ⇄ TAB_GROUPS senkronu)', () => {
    const seen = new Map<string, number>()
    for (const group of TAB_GROUPS) {
      for (const key of group.tabKeys) {
        seen.set(key, (seen.get(key) ?? 0) + 1)
      }
    }
    // Hiçbir sekme iki grupta yer alamaz
    for (const count of seen.values()) {
      expect(count).toBe(1)
    }
    // Her geçerli sekme bir grupta olmalı (yoksa menüden erişilemez olur)
    for (const key of VALID_TABS) {
      expect(seen.has(key)).toBe(true)
    }
    // Gereksiz grup girişi olmamalı
    expect(seen.size).toBe(VALID_TABS.length)
  })

  it('arama kutusuna yazınca yalnızca eşleşen sekmeler görünür (canlı filtre)', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    await user.type(screen.getByPlaceholderText('Search tabs...'), 'replay')

    // Eşleşen öğe görünür; eşleşmeyen gruplar/öğeler gizlenir
    expect(screen.getByRole('menuitem', { name: '▶ Replay' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: 'Scores' })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: '🛡️ Guardrails' })).not.toBeInTheDocument()
  })

  it('arama büyük/küçük harfe duyarsızdır ve eşleşen kısım vurgulanır', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    await user.type(screen.getByPlaceholderText('Search tabs...'), 'REPLAY')

    expect(screen.getByRole('menuitem', { name: '▶ Replay' })).toBeInTheDocument()
    expect(screen.getByText('Replay', { selector: 'mark' })).toBeInTheDocument()
  })

  it('Türkçe İ ve aksansız arama eşleşir (İçerik → icerik)', async () => {
    await i18n.changeLanguage('tr')
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Görünürlük Panosu')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Skorlar/))
    await user.type(screen.getByPlaceholderText('Sekme ara...'), 'icerik')

    expect(screen.getByRole('menuitem', { name: '📚 İçerik GEO' })).toBeInTheDocument()
    // Eşleşen kısım vurgulanır
    expect(screen.getByText('İçerik', { selector: 'mark' })).toBeInTheDocument()
  })

  it('eşleşme yoksa sonuç bulunamadı mesajı gösterilir', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    await user.type(screen.getByPlaceholderText('Search tabs...'), 'zzz')

    expect(screen.getByText(/No tabs match 'zzz'/)).toBeInTheDocument()
  })

  it('temizle butonu aramayı sıfırlar ve tüm sekmeler tekrar görünür', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    const search = screen.getByPlaceholderText('Search tabs...')
    await user.type(search, 'replay')
    expect(screen.queryByRole('menuitem', { name: 'Scores' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Clear' }))

    // Filtre kaldırıldı: akordeon odak gereği aktif grup (Measurement) açıktır
    expect(screen.getByRole('menuitem', { name: 'Scores' })).toBeInTheDocument()
    expect((search as HTMLInputElement).value).toBe('')
  })

  it('filtrelenmiş sonuçtan sekme seçilince aktif sekme değişir ve kaydedilir', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    await user.type(screen.getByPlaceholderText('Search tabs...'), 'replay')
    await user.click(screen.getByRole('menuitem', { name: '▶ Replay' }))

    expect(sessionStorage.getItem('geolens.last_tab')).toBe('replay')
    await waitFor(() => {
      expect(screen.getByText(/☰ ▶ Replay/)).toBeInTheDocument()
    })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('ilk yüklemede 403 alınırsa genel hata görünümü + tekrar dene gösterilir', async () => {
    vi.stubGlobal('fetch', vi.fn((url: RequestInfo | URL) => {
      const u = String(url)
      if (u.includes('/scores') || u.includes('/brands') || u.includes('/panels')) {
        return Promise.resolve(jsonRes(403, { error: 'workspace_access_denied' }))
      }
      return Promise.resolve(jsonRes(200, {}))
    }))
    render(<ScoreDashboard workspaceId="ws1" />)

    // 403'te hata mesajı boştur (toast yönetir) ama ekran sessizce boş kalmaz:
    // genel hata görünümü + tekrar dene butonu korunur
    await waitFor(() => {
      expect(screen.getByText('Failed to load data')).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('dış tıklama menüyü kapatır', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    expect(screen.getByRole('menu')).toBeInTheDocument()

    await user.click(screen.getByText('Visibility Dashboard'))
    await waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
  })

  it('menüden sekme pinlenir, çip görünür ve localStorage\'a kaydedilir', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    // Replay GEO grubunda olduğu için önce arama ile görünür yap
    await user.click(screen.getByText(/☰ Scores/))
    await user.type(screen.getByPlaceholderText('Search tabs...'), 'replay')
    await user.click(screen.getByRole('menuitem', { name: 'Pin ▶ Replay' }))

    // Çip görünür + kalıcı kayıt
    expect(screen.getByText(/📌 ▶ Replay/)).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('geolens.pinned_tabs') || '[]')).toContain('replay')

    // Çipten seçim çalışır (menü kapalıyken bile)
    await user.click(screen.getByText(/📌 ▶ Replay/))
    expect(sessionStorage.getItem('geolens.last_tab')).toBe('replay')
  })

  it('pin çipinin ✕ ile sabitleme kaldırılır', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })

    await user.click(screen.getByText(/☰ Scores/))
    await user.type(screen.getByPlaceholderText('Search tabs...'), 'replay')
    await user.click(screen.getByRole('menuitem', { name: 'Pin ▶ Replay' }))
    expect(screen.getByText(/📌 ▶ Replay/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Unpin ▶ Replay' }))
    expect(screen.queryByText(/📌 ▶ Replay/)).not.toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('geolens.pinned_tabs') || '[]')).not.toContain('replay')
  })

  it('keep-alive: ziyaret edilen sekme gizlenir ama DOM\'da kalır', async () => {
    stubFetch()
    const user = userEvent.setup()
    render(<ScoreDashboard workspaceId="ws1" />)

    await waitFor(() => {
      expect(screen.getByText('Visibility Dashboard')).toBeInTheDocument()
    })
    // Başlangıçta yalnızca bir panel (skorlar) DOM'da ve görünür
    expect(screen.getByText('No scores yet')).toBeInTheDocument()
    const panels = () => Array.from(document.querySelectorAll('.tab-panel'))
    expect(panels().length).toBe(1)

    // Brands sekmesine geç → skorlar gizlenir ama DOM'da kalır (keep-alive)
    await user.click(screen.getByText(/☰ Scores/))
    await user.click(screen.getByRole('menuitem', { name: 'Brands' }))

    await waitFor(() => {
      expect(screen.getByText(/☰ Brands/)).toBeInTheDocument()
      // Her iki panel de DOM'da: biri gizli (skorlar), biri görünür (markalar)
      expect(panels().length).toBe(2)
      expect(panels().filter(p => p.hasAttribute('hidden')).length).toBe(1)
      expect(panels().filter(p => !p.hasAttribute('hidden')).length).toBe(1)
    })
  })
})
