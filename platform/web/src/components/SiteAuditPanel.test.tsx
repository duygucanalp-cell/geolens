import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import i18n from '../i18n'
import { SiteAuditPanel } from './SiteAuditPanel'

// Mock API client
vi.mock('../api/client', () => ({
  getBrands: vi.fn(),
  getAuditFindings: vi.fn(),
  triggerAudit: vi.fn(),
}))

import { getBrands, getAuditFindings, triggerAudit } from '../api/client'
import type { AuditFindingsCatalog } from '../api/client'

function mockBrands() {
  return [
    { id: 'brand-1', name: 'Acme Corp', website_url: 'https://acme.com' },
    { id: 'brand-2', name: 'Beta Inc', website_url: 'https://beta.com' },
  ]
}

function mockFindings(overrides: Record<string, unknown> = {}): AuditFindingsCatalog {
  return {
    brand_id: 'brand-1',
    overall_score: 72,
    summary: { total: 3, critical: 1, high: 1, medium: 0, low: 1 },
    catalog: {
      robots_txt: [
        {
          title: 'robots.txt AI bot engeli',
          detail: 'GPTBot ve Google-Extended robots.txt ile engellenmiş.',
          severity: 'high',
          recommendation: 'AI botların siteye erişimine izin vermek için robots.txt güncelleyin.',
        },
      ],
      bot_access: [
        {
          title: 'Google-Extended 403 döndürüyor',
          detail: 'Google AI tarayıcısı siteye erişemiyor (HTTP 403).',
          severity: 'critical',
          recommendation: 'Sunucu yapılandırmasını kontrol edin.',
        },
      ],
      ssr: [],
      ssrf: [
        {
          title: 'CSP başlığı eksik',
          detail: 'Content-Security-Policy başlığı bulunamadı.',
          severity: 'low',
          recommendation: 'CSP başlığı ekleyerek güvenliği artırın.',
        },
      ],
    },
    ...overrides,
  }
}

function mockCleanFindings(): AuditFindingsCatalog {
  return {
    brand_id: 'brand-1',
    overall_score: 95,
    summary: { total: 0, critical: 0, high: 0, medium: 0, low: 0 },
    catalog: { robots_txt: [], bot_access: [], ssr: [], ssrf: [] },
  }
}

describe('SiteAuditPanel', () => {
  const workspaceId = 'ws-test-123'
  const onStatus = vi.fn()

  beforeEach(async () => {
    vi.clearAllMocks()
    await i18n.changeLanguage('tr')
  })

  it('shows brand selector and Denetim Başlat button', () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    expect(screen.getByText('Marka seçin...')).toBeInTheDocument()
    expect(screen.getByText('Denetim Başlat')).toBeInTheDocument()
    expect(screen.getByText(/Site Denetim Raporu/)).toBeInTheDocument()
  })

  it('loads brands into dropdown', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(getBrands).toHaveBeenCalledWith(workspaceId)
    })

    expect(screen.getAllByRole('option')).toHaveLength(3) // default + 2 brands
    expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    expect(screen.getByText('Beta Inc')).toBeInTheDocument()
  })

  it('disables audit button when no brand selected', () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    expect(screen.getByText('Denetim Başlat')).toBeDisabled()
  })

  it('shows no-findings message when brand selected but no audit yet', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockRejectedValue(new Error('not found'))

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('Henüz Denetim Yapılmamış')).toBeInTheDocument()
    })
  })

  it('loads findings when brand is selected', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockResolvedValue(mockFindings())

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(getAuditFindings).toHaveBeenCalledWith(workspaceId, 'brand-1')
    })

    // Score should render
    await waitFor(() => {
      expect(screen.getByText('72')).toBeInTheDocument()
    })

    // Severity cards should show count labels (use getAllByText since labels appear in cards + badges)
    expect(screen.getAllByText('Kritik').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Yüksek').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Düşük').length).toBeGreaterThanOrEqual(1)

    // Category cards should render
    expect(screen.getByText('robots.txt Analizi')).toBeInTheDocument()
    expect(screen.getByText('Bot Erişim Testi')).toBeInTheDocument()
    expect(screen.getByText('SSR & Meta Etiketler')).toBeInTheDocument()
    expect(screen.getByText('Güvenlik Başlıkları')).toBeInTheDocument()

    // Finding details should render
    expect(screen.getByText(/robots.txt AI bot engeli/)).toBeInTheDocument()
    expect(screen.getByText(/Google-Extended 403/)).toBeInTheDocument()
    expect(screen.getByText(/CSP başlığı eksik/)).toBeInTheDocument()
  })

  it('shows "✓ Sorun bulunamadı" for all categories when no issues', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockResolvedValue(mockCleanFindings())

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('95')).toBeInTheDocument()
    })

    expect(screen.getAllByText('✓ Sorun bulunamadı')).toHaveLength(4)
  })

  it('shows loading state while fetching findings', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockImplementation(
      () => new Promise(resolve => setTimeout(() => resolve(mockFindings()), 50))
    )

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('Bulgular yükleniyor...')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByText('72')).toBeInTheDocument()
    })
  })

  it('calls triggerAudit on Denetim Başlat click and reloads findings', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockRejectedValueOnce(new Error('not found'))

    const auditResult = {
      id: 'audit-1',
      brand_id: 'brand-1',
      brand_name: 'Acme Corp',
      website_url: 'https://acme.com',
      overall_score: 72,
      issues: [],
      created_at: new Date().toISOString(),
    }
    vi.mocked(triggerAudit).mockResolvedValue(auditResult as never)
    vi.mocked(getAuditFindings).mockResolvedValueOnce(mockFindings())

    render(<SiteAuditPanel workspaceId={workspaceId} onStatus={onStatus} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('Henüz Denetim Yapılmamış')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByText('Denetim Başlat'))

    await waitFor(() => {
      expect(triggerAudit).toHaveBeenCalledWith(
        workspaceId, 'brand-1', 'Acme Corp', 'https://acme.com'
      )
    })

    // Should show success message
    await waitFor(() => {
      expect(onStatus).toHaveBeenCalledWith('Site denetimi tamamlandı')
    })

    // Should reload findings
    await waitFor(() => {
      expect(screen.getByText('72')).toBeInTheDocument()
    })
  })

  it('shows error flashMsg when triggerAudit fails', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockRejectedValue(new Error('not found'))
    vi.mocked(triggerAudit).mockRejectedValue(new Error('API timeout'))

    render(<SiteAuditPanel workspaceId={workspaceId} onStatus={onStatus} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('Henüz Denetim Yapılmamış')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByText('Denetim Başlat'))

    await waitFor(() => {
      expect(onStatus).toHaveBeenCalledWith('Hata: API timeout')
    })
  })

  it('shows "Denetleniyor..." while audit is running', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    // First call: no audit yet
    vi.mocked(getAuditFindings).mockRejectedValueOnce(new Error('not found'))

    vi.mocked(triggerAudit).mockImplementation(
      () => new Promise(resolve => setTimeout(() => resolve({
        id: 'a',
        brand_id: 'brand-1',
        brand_name: 'Acme Corp',
        website_url: 'https://acme.com',
        overall_score: 80,
        issues: [],
        created_at: new Date().toISOString(),
      }), 30))
    )
    // Second call (after audit): findings available
    vi.mocked(getAuditFindings).mockResolvedValueOnce(mockFindings() as never)

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('Henüz Denetim Yapılmamış')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByText('Denetim Başlat'))

    // Button text should change immediately before promise resolves
    expect(screen.getByText('Denetleniyor...')).toBeInTheDocument()

    // Wait for audit to complete and button to return to normal
    await waitFor(() => {
      expect(screen.getByText('Denetim Başlat')).toBeInTheDocument()
    })
  })

  it('shows recommendation text when findings have recommendations', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockResolvedValue(mockFindings())

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText(/robots.txt güncelleyin/)).toBeInTheDocument()
    })
    expect(screen.getByText(/Sunucu yapılandırmasını kontrol edin/)).toBeInTheDocument()
    expect(screen.getByText(/CSP başlığı ekleyerek/)).toBeInTheDocument()
  })

  it('renders score for low score value', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockResolvedValue(mockFindings({ overall_score: 35 }))

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    await waitFor(() => {
      expect(screen.getByText('35')).toBeInTheDocument()
    })
  })

  it('handles getAuditFindings rejection gracefully', async () => {
    vi.mocked(getBrands).mockResolvedValue(mockBrands())
    vi.mocked(getAuditFindings).mockRejectedValue(new Error('network error'))

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })

    await userEvent.selectOptions(screen.getByRole('combobox'), 'brand-1')

    // After the API rejects, loading should stop and no-findings state should show
    await waitFor(() => {
      expect(screen.getByText('Henüz Denetim Yapılmamış')).toBeInTheDocument()
    })
  })

  it('handles empty brand list gracefully', async () => {
    vi.mocked(getBrands).mockResolvedValue([])

    render(<SiteAuditPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(getBrands).toHaveBeenCalledWith(workspaceId)
    })

    // Only the default option should be present
    expect(screen.getAllByRole('option')).toHaveLength(1)
    expect(screen.getByText('Marka seçin...')).toBeInTheDocument()
  })
})
