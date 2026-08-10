import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../i18n'
import { OnboardingWizard } from './OnboardingWizard'
import * as client from '../api/client'
import type { SetupStatus } from '../types'

vi.mock('../api/client', () => ({
  getSetupStatus: vi.fn(),
  createBrand: vi.fn(),
  createPanel: vi.fn(),
  createPromptSet: vi.fn(),
  triggerMeasurement: vi.fn(),
  getBrands: vi.fn(),
}))

const WS = 'WS01'

function mockStatus(doneKeys: string[]): SetupStatus {
  const keys = ['brand', 'panel', 'prompt_set', 'measurement']
  return {
    setup_complete: doneKeys.length === keys.length,
    steps: keys.map(key => ({ key, label: key, done: doneKeys.includes(key) })),
  }
}

function renderWizard(onComplete = vi.fn()) {
  return render(<OnboardingWizard workspaceId={WS} onComplete={onComplete} />)
}

beforeAll(async () => {
  await i18n.changeLanguage('en')
})

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  vi.mocked(client.getBrands).mockResolvedValue([])
})

describe('OnboardingWizard — adım hatırlama', () => {
  it('kayıtlı adım yoksa ilk tamamlanmamış adımdan başlar', async () => {
    vi.mocked(client.getSetupStatus).mockResolvedValue(mockStatus([]))
    renderWizard()

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Brand name (e.g. Acme Corp)')).toBeInTheDocument()
    })
    // İlk adım kaydedilmiş olmalı
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('brand')
  })

  it('kayıtlı adım hâlâ yapılmadıysa oradan devam eder', async () => {
    sessionStorage.setItem('geolens.wizard_step', 'panel')
    vi.mocked(client.getSetupStatus).mockResolvedValue(mockStatus([]))

    renderWizard()

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Panel name (e.g. Weekly Tracking)')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('panel')
  })

  it('kayıtlı adım zaten tamamlandıysa ilk yapılmamış adıma düşer', async () => {
    sessionStorage.setItem('geolens.wizard_step', 'brand') // brand tamamlanmış
    vi.mocked(client.getSetupStatus).mockResolvedValue(mockStatus(['brand']))

    renderWizard()

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Panel name (e.g. Weekly Tracking)')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('panel')
  })

  it('geri/ileri navigasyonu adımı kaydeder', async () => {
    sessionStorage.setItem('geolens.wizard_step', 'prompt_set')
    vi.mocked(client.getSetupStatus).mockResolvedValue(mockStatus([]))

    const user = userEvent.setup()
    renderWizard()

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Prompt name (e.g. Default Prompt)')).toBeInTheDocument()
    })

    // Geri → panel
    await user.click(screen.getByText('← Back'))
    await waitFor(() => {
      expect(screen.getByPlaceholderText('Panel name (e.g. Weekly Tracking)')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('panel')

    // Geri → brand
    await user.click(screen.getByText('← Back'))
    await waitFor(() => {
      expect(screen.getByPlaceholderText('Brand name (e.g. Acme Corp)')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBe('brand')
  })

  it('kurulum tamamlandığında kayıtlı adımı temizler ve onComplete çağırır', async () => {
    sessionStorage.setItem('geolens.wizard_step', 'measurement')
    vi.mocked(client.getSetupStatus).mockResolvedValue(mockStatus(['brand', 'panel', 'prompt_set', 'measurement']))

    const onComplete = vi.fn()
    renderWizard(onComplete)

    await waitFor(() => {
      expect(onComplete).toHaveBeenCalled()
    })
    expect(sessionStorage.getItem('geolens.wizard_step')).toBeNull()
  })
})
