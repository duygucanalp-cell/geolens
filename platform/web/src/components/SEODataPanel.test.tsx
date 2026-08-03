import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { SEODataPanel } from './SEODataPanel'

// Mock API client
vi.mock('../api/client', () => ({
  getSEOConnections: vi.fn(),
  getSEOAuthURL: vi.fn(),
  disconnectSEO: vi.fn(),
  getGA4Data: vi.fn(),
  getSearchConsoleData: vi.fn(),
}))

import {
  getSEOConnections,
  getSEOAuthURL,
  disconnectSEO,
  getGA4Data,
  getSearchConsoleData,
} from '../api/client'

function mockConnection(platform: string, overrides: Record<string, unknown> = {}) {
  return {
    id: `${platform}-1`,
    platform,
    email: `user@${platform}.com`,
    is_active: true,
    last_synced_at: null,
    created_at: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function mockGA4Data() {
  return [
    {
      page_views: 15000,
      sessions: 3200,
      bounce_rate: 0.45,
      avg_session_duration: 145.2,
      measured_at: '2026-07-28',
    },
  ]
}

function mockSCData() {
  return [
    { query: 'ai visibility tool', clicks: 120, impressions: 4500, ctr: 0.0267, avg_position: 3.2, measured_at: '2026-07-28' },
    { query: 'geo seo platform', clicks: 85, impressions: 3200, ctr: 0.0266, avg_position: 4.1, measured_at: '2026-07-28' },
    { query: 'generative engine optimization', clicks: 45, impressions: 1800, ctr: 0.025, avg_position: 5.8, measured_at: '2026-07-28' },
    { query: 'llm analytics', clicks: 30, impressions: 950, ctr: 0.0316, avg_position: 2.9, measured_at: '2026-07-28' },
  ]
}

describe('SEODataPanel', () => {
  const workspaceId = 'ws-test-123'
  const onStatus = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    window.history.replaceState({}, '', '/')
  })

  afterEach(() => {
    window.history.replaceState({}, '', '/')
  })

  it('shows connect buttons when no connections exist', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([])

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(getSEOConnections).toHaveBeenCalledWith(workspaceId)
    })

    expect(screen.getByText('Google Search Console')).toBeInTheDocument()
    expect(screen.getAllByText('Google ile Bağlan').length).toBe(2) // SC + GA4
  })

  it('shows connected state and data when GA4 is active', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('ga4'),
    ])
    vi.mocked(getGA4Data).mockResolvedValue(mockGA4Data())

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText(/Bağlı: user@ga4.com/)).toBeInTheDocument()
    })

    // GA4 metric cards should be visible (wait for async data loading)
    await waitFor(() => {
      // Use toLocaleString() to match locale-agnostic rendering
      expect(screen.getByText((15000).toLocaleString())).toBeInTheDocument()
    })
    await waitFor(() => {
      expect(screen.getByText((3200).toLocaleString())).toBeInTheDocument()
    })
    await waitFor(() => {
      expect(screen.getByText('45.0%')).toBeInTheDocument()
    })
    await waitFor(() => {
      expect(screen.getByText('145s')).toBeInTheDocument()
    })

    // Refresh button and data section title
    expect(screen.getByText('Yenile')).toBeInTheDocument()
    expect(screen.getByText(/GA4 Trafik Verileri/)).toBeInTheDocument()
  })

  it('shows Search Console data when SC is active', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('search_console'),
    ])
    vi.mocked(getSearchConsoleData).mockResolvedValue(mockSCData())

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText(/Bağlı: user@search_console.com/)).toBeInTheDocument()
    })

    // SC summary metrics (wait for async data loading)
    await waitFor(() => {
      expect(screen.getByText('280')).toBeInTheDocument()
    })

    // Top queries should render
    await waitFor(() => {
      expect(screen.getByText('ai visibility tool')).toBeInTheDocument()
    })
    expect(screen.getByText('geo seo platform')).toBeInTheDocument()
    expect(screen.getByText('generative engine optimization')).toBeInTheDocument()

    // Refresh button visible
    expect(screen.getAllByText('Yenile').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/Search Console Performansı/)).toBeInTheDocument()
  })

  it('shows GA4 and SC data when both connections are active', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('search_console'),
      mockConnection('ga4'),
    ])
    vi.mocked(getGA4Data).mockResolvedValue(mockGA4Data())
    vi.mocked(getSearchConsoleData).mockResolvedValue(mockSCData())

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText(/Bağlı: user@ga4.com/)).toBeInTheDocument()
      expect(screen.getByText(/Bağlı: user@search_console.com/)).toBeInTheDocument()
    })

    // Both data sections present (wait for async)
    await waitFor(() => {
      expect(screen.getByText(/GA4 Trafik Verileri/)).toBeInTheDocument()
    })
    expect(screen.getByText(/Search Console Performansı/)).toBeInTheDocument()

    // Both refresh buttons
    await waitFor(() => {
      expect(screen.getAllByText('Yenile').length).toBe(2)
    })

    // Both disconnect buttons
    expect(screen.getAllByText('Bağlantıyı Kes').length).toBe(2)
  })

  it('calls onStatus when data loading fails', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('ga4'),
    ])
    vi.mocked(getGA4Data).mockRejectedValue(new Error('API error'))

    render(<SEODataPanel workspaceId={workspaceId} onStatus={onStatus} />)

    await waitFor(() => {
      expect(onStatus).toHaveBeenCalledWith('GA4 verisi yüklenemedi')
    })

    // Status should auto-clear after 4s
    expect(onStatus).toHaveBeenCalledTimes(1) // called once so far
  })

  it('calls onStatus with error on disconnect failure', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('search_console'),
    ])
    vi.mocked(getSearchConsoleData).mockResolvedValue(mockSCData())
    vi.mocked(disconnectSEO).mockRejectedValue(new Error('Network error'))

    render(<SEODataPanel workspaceId={workspaceId} onStatus={onStatus} />)

    await waitFor(() => {
      expect(screen.getByText('Bağlantıyı Kes')).toBeInTheDocument()
    })

    const disconnectBtn = screen.getByText('Bağlantıyı Kes')
    await userEvent.click(disconnectBtn)

    await waitFor(() => {
      expect(disconnectSEO).toHaveBeenCalledWith(workspaceId, 'search_console')
      expect(onStatus).toHaveBeenCalledWith('Hata: Network error')
    })
  })

  it('calls getSEOAuthURL on connect button click', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([])
    vi.mocked(getSEOAuthURL).mockResolvedValue({ auth_url: 'https://accounts.google.com/o/oauth2/v2/auth?state=abc', state_token: 'abc' })

    // Mock window.location.href for the redirect
    // Store a reference so we can restore it in afterEach
    const origLocation = window.location
    Object.defineProperty(window, 'location', {
      value: { ...origLocation, href: '' },
      writable: true,
    })

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getAllByText('Google ile Bağlan').length).toBe(2)
    })

    const connectBtns = screen.getAllByText('Google ile Bağlan')
    await userEvent.click(connectBtns[1]) // GA4 connect button

    await waitFor(() => {
      expect(getSEOAuthURL).toHaveBeenCalledWith(workspaceId, 'ga4')
    })

    // Restore window.location
    Object.defineProperty(window, 'location', {
      value: origLocation,
      writable: true,
    })
  })

  it('refreshes GA4 data when refresh button is clicked', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('ga4'),
    ])
    vi.mocked(getGA4Data).mockResolvedValue(mockGA4Data())

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      // Use toLocaleString() to match locale-agnostic rendering
      expect(screen.getByText((15000).toLocaleString())).toBeInTheDocument()
    })

    // Clear mock call count
    vi.mocked(getGA4Data).mockClear()
    vi.mocked(getGA4Data).mockResolvedValue(mockGA4Data())

    const refreshBtn = screen.getByText('Yenile')
    await userEvent.click(refreshBtn)

    await waitFor(() => {
      expect(getGA4Data).toHaveBeenCalledWith(workspaceId)
    })
  })

  it('shows loading state while fetching SC data', async () => {
    // Delay resolution to show loading state
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('search_console'),
    ])
    vi.mocked(getSearchConsoleData).mockImplementation(
      () => new Promise(resolve => setTimeout(() => resolve(mockSCData()), 100))
    )

    render(<SEODataPanel workspaceId={workspaceId} />)

    // Loading text should appear shortly
    await waitFor(() => {
      expect(screen.getByText('Search Console verileri yükleniyor...')).toBeInTheDocument()
    })

    // Eventually data should load
    await waitFor(() => {
      expect(screen.getByText('ai visibility tool')).toBeInTheDocument()
    })
  })

  it('shows flashMsg success message on successful disconnect', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('search_console'),
    ])
    vi.mocked(getSearchConsoleData).mockResolvedValue(mockSCData())
    vi.mocked(disconnectSEO).mockResolvedValue({ status: 'disconnected', platform: 'search_console' })

    render(<SEODataPanel workspaceId={workspaceId} onStatus={onStatus} />)

    await waitFor(() => {
      expect(screen.getByText('Bağlantıyı Kes')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByText('Bağlantıyı Kes'))

    await waitFor(() => {
      expect(onStatus).toHaveBeenCalledWith('Search Console bağlantısı kaldırıldı')
    })
  })

  it('handles empty GA4 data gracefully', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('ga4'),
    ])
    vi.mocked(getGA4Data).mockResolvedValue([])

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText(/Bağlı: user@ga4.com/)).toBeInTheDocument()
    })

    // Section title should show when connected
    expect(screen.getByText(/GA4 Trafik Verileri/)).toBeInTheDocument()

    // But no metric cards when data is empty
    expect(screen.queryByText('Sayfa Görüntüleme')).not.toBeInTheDocument()
  })

  it('handles empty SC data gracefully', async () => {
    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('search_console'),
    ])
    vi.mocked(getSearchConsoleData).mockResolvedValue([])

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      expect(screen.getByText(/Bağlı: user@search_console.com/)).toBeInTheDocument()
    })

    // Section title should show when connected
    expect(screen.getByText(/Search Console Performansı/)).toBeInTheDocument()

    // But no metric cards when data is empty
    expect(screen.queryByText('Toplam Tıklama')).not.toBeInTheDocument()
  })

  it('detects OAuth callback from URL params', async () => {
    // Simulate URL with seo=connected param
    window.history.replaceState({}, '', '/?seo=connected&platform=ga4')

    vi.mocked(getSEOConnections).mockResolvedValue([
      mockConnection('ga4'),
    ])

    render(<SEODataPanel workspaceId={workspaceId} />)

    await waitFor(() => {
      // Should have been called twice: once from useEffect, once from callback detection
      expect(getSEOConnections).toHaveBeenCalledTimes(2)
    })

    // Cleanup URL
    window.history.replaceState({}, '', '/')
  })
})
