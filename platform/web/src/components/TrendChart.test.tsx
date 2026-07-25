import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TrendChart } from './TrendChart'
import type { Score } from '../types'

const mockScore = (overrides: Partial<Score> = {}): Score => ({
  id: '1',
  brand_name: 'TestBrand',
  value: 75,
  ci_low: 70,
  ci_high: 80,
  fidelity_label: 'Kademe 1',
  freshness_at: '2025-06-01T00:00:00Z',
  ...overrides,
})

describe('TrendChart', () => {
  it('shows empty message when no scores', () => {
    render(<TrendChart scores={[]} brandName="TestBrand" />)
    expect(screen.getByText('Trend verisi yok')).toBeInTheDocument()
  })

  it('renders chart title with brand name', () => {
    const scores = [
      mockScore({ freshness_at: '2025-06-01T00:00:00Z' }),
      mockScore({ id: '2', freshness_at: '2025-07-01T00:00:00Z' }),
    ]
    render(<TrendChart scores={scores} brandName="TestBrand" />)
    expect(screen.getByText(/TestBrand/)).toBeInTheDocument()
    expect(screen.getByText(/Görünürlük Trendi/)).toBeInTheDocument()
  })
})
