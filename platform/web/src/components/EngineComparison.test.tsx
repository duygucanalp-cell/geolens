import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { EngineComparison } from './EngineComparison'
import type { Score } from '../types'

const mockScore = (overrides: Partial<Score> = {}): Score => ({
  id: '1',
  brand_name: 'TestBrand',
  value: 85,
  ci_low: 10,
  ci_high: 20,
  fidelity_label: 'Kademe 1',
  freshness_at: '2025-01-01T00:00:00Z',
  ...overrides,
})

describe('EngineComparison', () => {
  it('shows empty message when no scores', () => {
    render(<EngineComparison scores={[]} brandName="TestBrand" />)
    expect(screen.getByText('Henüz motor verisi yok')).toBeInTheDocument()
  })

  it('shows empty message when scores have no engine breakdown', () => {
    render(<EngineComparison scores={[mockScore()]} brandName="TestBrand" />)
    expect(screen.getByText('Henüz motor verisi yok')).toBeInTheDocument()
  })

  it('renders chart title with brand name when breakdown present', () => {
    const score = mockScore({
      engine_breakdown: { chatgpt: 80, gemini: 60 },
    })
    render(<EngineComparison scores={[score]} brandName="TestBrand" />)
    expect(screen.getByText(/Motor Karşılaştırması/)).toBeInTheDocument()
    expect(screen.getByText(/TestBrand/)).toBeInTheDocument()
  })
})
