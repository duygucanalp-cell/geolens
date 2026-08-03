import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import i18n from '../i18n'
import { BrandManagement } from './BrandManagement'
import * as client from '../api/client'

// Mock all API client functions
vi.mock('../api/client', () => ({
  getBrands: vi.fn(),
  updateBrand: vi.fn(),
  deleteBrand: vi.fn(),
  getBrandCompetitors: vi.fn(),
  updateBrandCompetitors: vi.fn(),
  deleteBrandCompetitor: vi.fn(),
  searchBrands: vi.fn(),
}))

const mockBrands = [
  { id: 'B01', name: 'Acme Corp', website_url: 'https://acme.com' },
  { id: 'B02', name: 'Beta Inc', website_url: 'https://beta.com' },
  { id: 'B03', name: 'Gamma Ltd', website_url: 'https://gamma.com' },
]

const mockCompetitors: client.CompetitorItem[] = [
  { competitor_id: 'B02', competitor_name: 'Beta Inc', created_at: '2025-01-01T00:00:00Z' },
]

const WS = 'WS01'

beforeAll(async () => {
  await i18n.changeLanguage('en')
})

function renderBrandManagement() {
  return render(<BrandManagement workspaceId={WS} />)
}

// -----------------------------------------------
// Loading & Error states
// -----------------------------------------------
describe('BrandManagement — Loading & Error states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading state on mount', () => {
    vi.mocked(client.getBrands).mockReturnValue(new Promise(() => {}))
    renderBrandManagement()
    expect(screen.getByText('Loading brands...')).toBeInTheDocument()
  })

  it('shows error state when getBrands fails', async () => {
    vi.mocked(client.getBrands).mockRejectedValue(new Error('API error'))
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('API error')).toBeInTheDocument()
    })
    expect(screen.getByText('Retry')).toBeInTheDocument()
  })

  it('retries loading after error', async () => {
    const user = userEvent.setup()
    vi.mocked(client.getBrands).mockRejectedValueOnce(new Error('API error'))
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Retry')).toBeInTheDocument()
    })
    vi.mocked(client.getBrands).mockResolvedValueOnce(mockBrands)
    await user.click(screen.getByText('Retry'))
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
  })

  it('shows empty state when no brands exist', async () => {
    vi.mocked(client.getBrands).mockResolvedValue([])
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText(/No brands yet/)).toBeInTheDocument()
    })
  })
})

// -----------------------------------------------
// Brand CRUD
// -----------------------------------------------
describe('BrandManagement — Brand CRUD', () => {
  const user = userEvent.setup()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(client.getBrands).mockResolvedValue(mockBrands)
  })

  it('renders brand list', async () => {
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    expect(screen.getByText('Beta Inc')).toBeInTheDocument()
    expect(screen.getByText('Gamma Ltd')).toBeInTheDocument()
    expect(screen.getByText('https://acme.com')).toBeInTheDocument()
  })

  it('opens edit dialog and pre-fills fields', async () => {
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Edit')[0])
    await waitFor(() => {
      expect(screen.getByText('Edit Brand')).toBeInTheDocument()
    })
    const nameInput = screen.getByPlaceholderText('Brand name') as HTMLInputElement
    const urlInput = screen.getByPlaceholderText('https://') as HTMLInputElement
    expect(nameInput.value).toBe('Acme Corp')
    expect(urlInput.value).toBe('https://acme.com')
  })

  it('saves brand and shows success banner', async () => {
    const updatedBrand = { id: 'B01', name: 'Acme Updated', website_url: 'https://acme-updated.com' }
    vi.mocked(client.updateBrand).mockResolvedValue(updatedBrand)
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Edit')[0])
    await waitFor(() => {
      expect(screen.getByText('Edit Brand')).toBeInTheDocument()
    })
    const nameInput = screen.getByPlaceholderText('Brand name')
    await user.clear(nameInput)
    await user.keyboard('Acme Updated')
    await user.click(screen.getByText('Save'))
    await waitFor(() => {
      expect(screen.queryByText('Edit Brand')).not.toBeInTheDocument()
    })
    expect(screen.getByText(/Brand updated/)).toBeInTheDocument()
    expect(screen.getByText('Acme Updated')).toBeInTheDocument()
  })

  it('save button is disabled when name is empty', async () => {
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Edit')[0])
    await waitFor(() => {
      expect(screen.getByText('Edit Brand')).toBeInTheDocument()
    })
    const nameInput = screen.getByPlaceholderText('Brand name')
    await user.clear(nameInput)
    const saveBtn = screen.getByText('Save')
    expect(saveBtn).toBeDisabled()
  })

  it('shows error on save failure', async () => {
    vi.mocked(client.updateBrand).mockRejectedValue(new Error('Update failed'))
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Edit')[0])
    await waitFor(() => {
      expect(screen.getByText('Edit Brand')).toBeInTheDocument()
    })
    await user.click(screen.getByText('Save'))
    await waitFor(() => {
      expect(screen.getByText('Update failed')).toBeInTheDocument()
    })
  })

  it('cancels edit dialog without saving', async () => {
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Edit')[0])
    await waitFor(() => {
      expect(screen.getByText('Edit Brand')).toBeInTheDocument()
    })
    await user.click(screen.getByText('Cancel'))
    await waitFor(() => {
      expect(screen.queryByText('Edit Brand')).not.toBeInTheDocument()
    })
  })

  it('deletes brand and shows success banner', async () => {
    vi.mocked(client.deleteBrand).mockResolvedValue({ status: 'deleted', brand_id: 'B03' })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Gamma Ltd')).toBeInTheDocument()
    })
    const deleteButtons = screen.getAllByText('Delete')
    await user.click(deleteButtons[2])
    await waitFor(() => {
      expect(screen.getByText(/Brand deleted/)).toBeInTheDocument()
    })
    expect(screen.queryByText('Gamma Ltd')).not.toBeInTheDocument()
    vi.restoreAllMocks()
  })
})

// -----------------------------------------------
// Competitor CRUD
// -----------------------------------------------
describe('BrandManagement — Competitor CRUD', () => {
  const user = userEvent.setup()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(client.getBrands).mockResolvedValue(mockBrands)
    vi.mocked(client.getBrandCompetitors).mockResolvedValue(mockCompetitors)
  })

  it('opens competitors dialog and lists competitors', async () => {
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Competitors')[0])
    await waitFor(() => {
      expect(screen.getByText(/Acme Corp — Competitors/)).toBeInTheDocument()
    })
    // Beta Inc appears in brand list cards AND in the competitor list
    expect(screen.getAllByText('Beta Inc').length).toBeGreaterThanOrEqual(2)
  })

  it('shows empty competitors state', async () => {
    vi.mocked(client.getBrandCompetitors).mockResolvedValue([])
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Competitors')[0])
    await waitFor(() => {
      expect(screen.getByText(/No competitors defined/)).toBeInTheDocument()
    })
  })

  it('adds competitor via autocomplete', async () => {
    vi.mocked(client.getBrandCompetitors).mockResolvedValueOnce(mockCompetitors)
    vi.mocked(client.searchBrands).mockResolvedValue({
      data: [{ id: 'B03', name: 'Gamma Ltd', website_url: 'https://gamma.com' }],
      total: 1,
      offset: 0,
      limit: 20,
    })
    vi.mocked(client.updateBrandCompetitors).mockResolvedValue({ status: 'ok' })
    vi.mocked(client.getBrandCompetitors).mockResolvedValueOnce([
      ...mockCompetitors,
      { competitor_id: 'B03', competitor_name: 'Gamma Ltd', created_at: '2025-01-02T00:00:00Z' },
    ])
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Competitors')[0])
    await waitFor(() => {
      expect(screen.getByText(/Acme Corp — Competitors/)).toBeInTheDocument()
    })
    // Type search query
    const searchInput = screen.getByPlaceholderText('Search or enter competitor brand ID')
    await user.type(searchInput, 'Gamma')
    // Wait for the autocomplete dropdown to contain Gamma Ltd (retries past 300ms debounce)
    let gammaBtn: HTMLElement
    await waitFor(() => {
      const dropdown = document.querySelector('.brand-mgmt-autocomplete-dropdown')
      expect(dropdown).not.toBeNull()
      const items = dropdown!.querySelectorAll('.brand-mgmt-autocomplete-item')
      expect(items.length).toBeGreaterThan(0)
      gammaBtn = items[0] as HTMLElement
    }, { timeout: 2000 })
    await user.click(gammaBtn!)
    await waitFor(() => {
      expect(screen.getByText(/Competitor added/)).toBeInTheDocument()
    })
  })

  it('adds competitor via quick add', async () => {
    vi.mocked(client.getBrandCompetitors).mockResolvedValueOnce(mockCompetitors)
    vi.mocked(client.updateBrandCompetitors).mockResolvedValue({ status: 'ok' })
    vi.mocked(client.getBrandCompetitors).mockResolvedValueOnce([
      ...mockCompetitors,
      { competitor_id: 'B03', competitor_name: 'Gamma Ltd', created_at: '2025-01-02T00:00:00Z' },
    ])
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Competitors')[0])
    await waitFor(() => {
      expect(screen.getByText(/Acme Corp — Competitors/)).toBeInTheDocument()
    })
    // Quick add shows only Gamma Ltd: B01 excluded (current), B02 excluded (already competitor)
    // Wait for the "Add Selected" button which appears when quick-add brands exist
    await waitFor(() => {
      expect(screen.getByText(/Add Selected/)).toBeInTheDocument()
    })
    // Find Gamma Ltd's checkbox in the quick-add section (Gamma appears in brand cards too)
    const gammaLabels = screen.getAllByText('Gamma Ltd')
    const gammaLabel = gammaLabels.find(el => el.closest('label') !== null)?.closest('label')
    expect(gammaLabel).not.toBeNull()
    const gammaCheckbox = gammaLabel!.querySelector('input[type="checkbox"]')
    expect(gammaCheckbox).not.toBeNull()
    await user.click(gammaCheckbox!)
    // Click "Add Selected (1)"
    await user.click(screen.getByText(/Add Selected \(1/))
    await waitFor(() => {
      expect(screen.getByText(/Competitor added/)).toBeInTheDocument()
    })
  })

  it('removes competitor and shows success banner', async () => {
    vi.mocked(client.deleteBrandCompetitor).mockResolvedValue({ status: 'deleted', competitor_id: 'B02' })
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Competitors')[0])
    await waitFor(() => {
      expect(screen.getByText(/Acme Corp — Competitors/)).toBeInTheDocument()
    })
    // Click remove (×) button on Beta Inc (first remove button, since each competitor has one)
    const removeBtns = screen.getAllByTitle('Remove competitor')
    await user.click(removeBtns[0])

    await waitFor(() => {
      expect(screen.getByText(/Competitor removed/)).toBeInTheDocument()
    })
  })

  it('closes competitors dialog', async () => {
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Competitors')[0])
    await waitFor(() => {
      expect(screen.getByText(/Acme Corp — Competitors/)).toBeInTheDocument()
    })
    await user.click(screen.getByText('Close'))
    await waitFor(() => {
      expect(screen.queryByText(/Acme Corp — Competitors/)).not.toBeInTheDocument()
    })
  })
})

// -----------------------------------------------
// Success banner
// -----------------------------------------------
describe('BrandManagement — Success banner', () => {
  const user = userEvent.setup()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(client.getBrands).mockResolvedValue(mockBrands)
  })

  it('shows success banner after save', async () => {
    vi.mocked(client.updateBrand).mockResolvedValue({ id: 'B01', name: 'Acme Updated', website_url: 'https://acme-updated.com' })
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    await user.click(screen.getAllByText('Edit')[0])
    await waitFor(() => {
      expect(screen.getByText('Edit Brand')).toBeInTheDocument()
    })
    await user.click(screen.getByText('Save'))
    await waitFor(() => {
      expect(screen.queryByText('Edit Brand')).not.toBeInTheDocument()
    })
    expect(screen.getByText(/Brand updated/)).toBeInTheDocument()
  })

  it('replaces old success banner with new one on consecutive actions', async () => {
    vi.mocked(client.deleteBrand).mockResolvedValue({ status: 'deleted', brand_id: 'B03' })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderBrandManagement()
    await waitFor(() => {
      expect(screen.getByText('Acme Corp')).toBeInTheDocument()
    })
    // Delete Gamma Ltd (index 2)
    await user.click(screen.getAllByText('Delete')[2])
    await waitFor(() => {
      expect(screen.getByText(/Brand deleted/)).toBeInTheDocument()
    })
    // Delete Beta Inc (now index 1 after Gamma removed)
    await user.click(screen.getAllByText('Delete')[1])
    await waitFor(() => {
      expect(screen.getByText(/Brand deleted/)).toBeInTheDocument()
    })
    vi.restoreAllMocks()
  })
})
