import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BenchmarkWidget } from './BenchmarkWidget'
import * as client from '../api/client'

// Mock the API client
vi.mock('../api/client', () => ({
  getBenchmarkContext: vi.fn(),
}))

const mockGetBenchmarkContext = vi.mocked(client.getBenchmarkContext)

const mockFullData = {
  my_score: 72,
  tenant_count: 24,
  sufficient_data: true,
  sector_average: 54,
  sector_median: 52,
  sector_min: 12,
  sector_max: 95,
  sector_stddev: 14.2,
  percentile_25: 35,
  percentile_75: 68,
  percentile_90: 82,
  difference: 18,
  trend: 'up' as const,
}

const mockInsufficientData = {
  my_score: 72,
  tenant_count: 3,
  sufficient_data: false,
  message: 'yetersiz veri — anonim kıyas için en az 5 kiracı gerekli',
}

describe('BenchmarkWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading state initially', () => {
    mockGetBenchmarkContext.mockReturnValue(new Promise(() => {})) // never resolves

    render(<BenchmarkWidget workspaceId="ws-1" />)
    expect(screen.getByText(/sektör verileri yükleniyor/i)).toBeInTheDocument()
  })

  it('shows error state with retry button', async () => {
    mockGetBenchmarkContext.mockRejectedValue(new Error('API error'))

    render(<BenchmarkWidget workspaceId="ws-1" />)
    await waitFor(() => {
      expect(screen.getByText('API error')).toBeInTheDocument()
    })
    expect(screen.getByText(/tekrar dene/i)).toBeInTheDocument()
  })

  it('shows insufficient data message', async () => {
    mockGetBenchmarkContext.mockResolvedValue(mockInsufficientData)

    render(<BenchmarkWidget workspaceId="ws-1" />)
    await waitFor(() => {
      expect(screen.getByText(/yetersiz veri/i)).toBeInTheDocument()
    })
  })

  it('renders score, sector avg, trend, and percentile badge', async () => {
    mockGetBenchmarkContext.mockResolvedValue(mockFullData)

    render(<BenchmarkWidget workspaceId="ws-1" />)
    await waitFor(() => {
      expect(screen.getByText('72')).toBeInTheDocument()
    })
    expect(screen.getByText('54.0')).toBeInTheDocument()
    expect(screen.getByText('+18.0')).toBeInTheDocument()
    expect(screen.getByText(/üst %25'lik dilimde/i)).toBeInTheDocument()
  })

  it('shows detail section when toggle is clicked', async () => {
    mockGetBenchmarkContext.mockResolvedValue(mockFullData)

    render(<BenchmarkWidget workspaceId="ws-1" />)
    await waitFor(() => {
      expect(screen.getByText('72')).toBeInTheDocument()
    })

    const detailBtn = screen.getByText('Detaylı Analiz →')
    await userEvent.click(detailBtn)

    // Detail values should now be visible
    expect(screen.getByText('52.0')).toBeInTheDocument() // median
    expect(screen.getByText('14.2')).toBeInTheDocument() // stddev
    expect(screen.getByText('12')).toBeInTheDocument()    // min
    expect(screen.getByText('95')).toBeInTheDocument()    // max
    expect(screen.getByText('35')).toBeInTheDocument()    // P25
    expect(screen.getByText('68')).toBeInTheDocument()    // P75
    expect(screen.getByText('82')).toBeInTheDocument()    // P90
    expect(screen.getByText('24')).toBeInTheDocument()    // tenant count
  })

  it('renders with stable trend when difference is small', async () => {
    mockGetBenchmarkContext.mockResolvedValue({
      ...mockFullData,
      my_score: 50,
      sector_average: 50,
      difference: 0,
      trend: 'stable' as const,
    })

    render(<BenchmarkWidget workspaceId="ws-1" />)
    await waitFor(() => {
      expect(screen.getByText('0.0')).toBeInTheDocument()
    })
  })

  it('shows down trend arrow', async () => {
    mockGetBenchmarkContext.mockResolvedValue({
      ...mockFullData,
      my_score: 40,
      sector_average: 60,
      difference: -20,
      trend: 'down' as const,
    })

    render(<BenchmarkWidget workspaceId="ws-1" />)
    await waitFor(() => {
      expect(screen.getByText('-20.0')).toBeInTheDocument()
    })
  })
})
