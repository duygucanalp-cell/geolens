import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { ScoreCard } from './ScoreCard'
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

describe('ScoreCard', () => {
  it('renders brand name and score value', () => {
    render(<ScoreCard score={mockScore()} />)
    expect(screen.getByText('TestBrand')).toBeInTheDocument()
    expect(screen.getByText('85')).toBeInTheDocument()
  })

  it('renders fidelity label', () => {
    render(<ScoreCard score={mockScore({ fidelity_label: 'Kademe 2' })} />)
    expect(screen.getByText('Kademe 2')).toBeInTheDocument()
  })

  it('renders with zero score', () => {
    render(<ScoreCard score={mockScore({ value: 0 })} />)
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('renders engine breakdown when present', () => {
    const score = mockScore({
      engine_breakdown: { chatgpt: 80, gemini: 60 },
    })
    render(<ScoreCard score={score} />)
    expect(screen.getByText('chatgpt')).toBeInTheDocument()
    expect(screen.getByText('gemini')).toBeInTheDocument()
    expect(screen.getByText('80')).toBeInTheDocument()
    expect(screen.getByText('60')).toBeInTheDocument()
  })

  it('renders date in Turkish format', () => {
    render(<ScoreCard score={mockScore()} />)
    expect(screen.getByText(/1\.01\.2025|2025/)).toBeInTheDocument()
  })
})
