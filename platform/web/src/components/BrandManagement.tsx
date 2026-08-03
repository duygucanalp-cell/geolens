import { useState, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { getBrands, createBrand, updateBrand, deleteBrand, getBrandCompetitors, updateBrandCompetitors, deleteBrandCompetitor, searchBrands } from '../api/client'
import type { Brand } from '../types'
import type { CompetitorItem } from '../api/client'

const PAGE_SIZE = 20

interface BrandManagementProps {
  workspaceId: string
}

export function BrandManagement({ workspaceId }: BrandManagementProps) {
  const { t } = useTranslation()
  const [brands, setBrands] = useState<Brand[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingBrand, setEditingBrand] = useState<Brand | null>(null)
  const [editName, setEditName] = useState('')
  const [editUrl, setEditUrl] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [competitorsBrand, setCompetitorsBrand] = useState<Brand | null>(null)
  const [competitorList, setCompetitorList] = useState<CompetitorItem[]>([])
  const [competitorsLoading, setCompetitorsLoading] = useState(false)
  const [competitorsError, setCompetitorsError] = useState<string | null>(null)
  const [addingCompetitor, setAddingCompetitor] = useState(false)
  const [addCompetitorError, setAddCompetitorError] = useState<string | null>(null)
  const [removingCompetitorId, setRemovingCompetitorId] = useState<string | null>(null)
  const [selectedQuickAdd, setSelectedQuickAdd] = useState<string[]>([])
  const [addingQuickAdd, setAddingQuickAdd] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [showDropdown, setShowDropdown] = useState(false)
  const [searchResults, setSearchResults] = useState<Brand[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchTotal, setSearchTotal] = useState(0)
  const [searchOffset, setSearchOffset] = useState(0)
  const searchRef = useRef<HTMLDivElement>(null)
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const successTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const [showAdd, setShowAdd] = useState(false)
  const [addName, setAddName] = useState('')
  const [addUrl, setAddUrl] = useState('')
  const [addSaving, setAddSaving] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)

  function showSuccess(msg: string) {
    if (successTimerRef.current) clearTimeout(successTimerRef.current)
    setSuccessMessage(msg)
    successTimerRef.current = setTimeout(() => setSuccessMessage(null), 2500)
  }

  useEffect(() => {
    return () => {
      if (successTimerRef.current) clearTimeout(successTimerRef.current)
    }
  }, [])

  useEffect(() => {
    loadBrands()
  }, [workspaceId])

  async function loadBrands() {
    try {
      setLoading(true)
      setError(null)
      const data = await getBrands(workspaceId)
      setBrands(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('brand.error_load'))
    } finally {
      setLoading(false)
    }
  }

  function openEdit(brand: Brand) {
    setEditingBrand(brand)
    setEditName(brand.name)
    setEditUrl(brand.website_url)
    setSaveError(null)
  }

  function closeEdit() {
    setEditingBrand(null)
    setSaveError(null)
  }

  async function handleSave() {
    if (!editingBrand) return
    if (!editName.trim() || !editUrl.trim()) {
      setSaveError(t('brand.edit_required'))
      return
    }
    setSaving(true)
    setSaveError(null)
    try {
      const updated = await updateBrand(workspaceId, editingBrand.id, {
        name: editName.trim(),
        website_url: editUrl.trim(),
      })
      setBrands(prev => prev.map(b => b.id === updated.id ? updated : b))
      showSuccess(t('brand.edit_saved'))
      closeEdit()
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : t('brand.edit_error'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(brand: Brand) {
    const confirmed = window.confirm(t('brand.delete_confirm', { name: brand.name }))
    if (!confirmed) return

    setDeleting(true)
    setError(null)
    try {
      await deleteBrand(workspaceId, brand.id)
      setBrands(prev => prev.filter(b => b.id !== brand.id))
      showSuccess(t('brand.deleted'))
    } catch (err) {
      setError(err instanceof Error ? err.message : t('brand.delete_error'))
    } finally {
      setDeleting(false)
    }
  }

  async function openCompetitors(brand: Brand) {
    setCompetitorsBrand(brand)
    setCompetitorsError(null)
    setCompetitorsLoading(true)
    setCompetitorList([])
    try {
      const data = await getBrandCompetitors(workspaceId, brand.id)
      setCompetitorList(data)
    } catch (err) {
      setCompetitorsError(err instanceof Error ? err.message : t('brand.competitors_error'))
    } finally {
      setCompetitorsLoading(false)
    }
  }

  async function fetchSearchPage(query: string, offset: number) {
    if (!competitorsBrand) return
    setSearchLoading(true)
    try {
      const res = await searchBrands(workspaceId, query, competitorsBrand.id, offset, PAGE_SIZE)
      const existingIds = new Set(competitorList.map(c => c.competitor_id))
      setSearchResults(res.data.filter(b => !existingIds.has(b.id)))
      setSearchTotal(res.total)
      setSearchOffset(res.offset)
    } catch {
      setSearchResults([])
      setSearchTotal(0)
    } finally {
      setSearchLoading(false)
    }
  }

  // Debounced search via API — filter out current brand + already-added competitors on frontend
  useEffect(() => {
    if (!competitorsBrand || !searchQuery.trim()) {
      setSearchResults([])
      setSearchTotal(0)
      setSearchOffset(0)
      return
    }

    if (searchTimerRef.current) {
      clearTimeout(searchTimerRef.current)
    }

    searchTimerRef.current = setTimeout(() => {
      fetchSearchPage(searchQuery.trim(), 0)
    }, 300)

    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current)
    }
  }, [searchQuery, competitorsBrand, competitorList, workspaceId])

  function goNextPage() {
    if (!competitorsBrand) return
    const nextOffset = searchOffset + PAGE_SIZE
    if (nextOffset < searchTotal) {
      fetchSearchPage(searchQuery.trim(), nextOffset)
    }
  }

  function goPrevPage() {
    if (!competitorsBrand) return
    const prevOffset = Math.max(0, searchOffset - PAGE_SIZE)
    if (prevOffset < searchOffset) {
      fetchSearchPage(searchQuery.trim(), prevOffset)
    }
  }

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Compute available brands for multi-select (exclude current brand + already-added)
  const availableForQuickAdd = competitorsBrand
    ? brands.filter(b =>
        b.id !== competitorsBrand.id &&
        !competitorList.some(c => c.competitor_id === b.id)
      )
    : []

  function toggleQuickAdd(id: string) {
    setSelectedQuickAdd(prev =>
      prev.includes(id) ? prev.filter(c => c !== id) : [...prev, id]
    )
  }

  async function handleAddQuickAdd() {
    if (!competitorsBrand || selectedQuickAdd.length === 0) return
    setAddingQuickAdd(true)
    setAddCompetitorError(null)
    try {
      const currentIds = competitorList.map(c => c.competitor_id)
      await updateBrandCompetitors(workspaceId, competitorsBrand.id, [...currentIds, ...selectedQuickAdd])
      const data = await getBrandCompetitors(workspaceId, competitorsBrand.id)
      setCompetitorList(data)
      setSelectedQuickAdd([])
      showSuccess(t('brand.competitor_added'))
    } catch (err) {
      setAddCompetitorError(err instanceof Error ? err.message : t('brand.competitor_add_error'))
    } finally {
      setAddingQuickAdd(false)
    }
  }

  function closeCompetitors() {
    setCompetitorsBrand(null)
    setCompetitorList([])
    setCompetitorsError(null)
    setAddCompetitorError(null)
    setSearchQuery('')
    setShowDropdown(false)
    setSearchResults([])
    setSearchTotal(0)
    setSearchOffset(0)
    setSelectedQuickAdd([])
  }

  function selectCompetitorBrand(brand: Brand) {
    setSearchQuery(brand.name)
    setShowDropdown(false)
    handleAddCompetitorById(brand.id)
  }

  async function handleAddCompetitorById(competitorId: string) {
    if (!competitorsBrand) return
    setAddingCompetitor(true)
    setAddCompetitorError(null)
    try {
      const currentIds = competitorList.map(c => c.competitor_id)
      await updateBrandCompetitors(workspaceId, competitorsBrand.id, [...currentIds, competitorId])
      const data = await getBrandCompetitors(workspaceId, competitorsBrand.id)
      setCompetitorList(data)
      setSelectedQuickAdd(prev => prev.filter(id => id !== competitorId))
      setSearchQuery('')
      showSuccess(t('brand.competitor_added'))
    } catch (err) {
      setAddCompetitorError(err instanceof Error ? err.message : t('brand.competitor_add_error'))
    } finally {
      setAddingCompetitor(false)
    }
  }

  async function handleAddCompetitor() {
    if (!competitorsBrand) return
    const competitorId = searchQuery.trim()
    if (!competitorId) return
    await handleAddCompetitorById(competitorId)
  }

  async function handleRemoveCompetitor(competitorId: string) {
    if (!competitorsBrand) return
    setRemovingCompetitorId(competitorId)
    setCompetitorsError(null)
    try {
      await deleteBrandCompetitor(workspaceId, competitorsBrand.id, competitorId)
      setCompetitorList(prev => prev.filter(c => c.competitor_id !== competitorId))
      showSuccess(t('brand.competitor_removed'))
    } catch (err) {
      setCompetitorsError(err instanceof Error ? err.message : t('brand.competitor_remove_error'))
    } finally {
      setRemovingCompetitorId(null)
    }
  }

  async function handleAddBrand() {
    if (!addName.trim() || !addUrl.trim()) return
    setAddSaving(true)
    setAddError(null)
    try {
      const created = await createBrand(workspaceId, { name: addName.trim(), website_url: addUrl.trim() })
      setBrands(prev => [...prev, created])
      setShowAdd(false)
      setAddName('')
      setAddUrl('')
      showSuccess(t('wizard.brand_added'))
    } catch (err) {
      setAddError(err instanceof Error ? err.message : t('brand.edit_error'))
    } finally {
      setAddSaving(false)
    }
  }

  if (loading) {
    return <div className="dashboard-loading">{t('brand.loading')}</div>
  }

  if (error) {
    return (
      <div className="dashboard-error">
        <p>{error}</p>
        <button onClick={loadBrands}>{t('dashboard.retry')}</button>
      </div>
    )
  }

  return (
    <div className="brand-management">
      <div className="brand-mgmt-header">
        <h3>{t('brand.title')}</h3>
        <p className="brand-mgmt-desc">{t('brand.desc')}</p>
      </div>

      {successMessage && (
        <div className="wizard-success-banner">
          <span className="wizard-success-icon">✓</span>
          {successMessage}
        </div>
      )}

      <div className="dashboard-filters">
        <button className="refresh-btn" onClick={() => { setShowAdd(true); setAddError(null) }}>
          + {t('brand.add')}
        </button>
      </div>

      {brands.length === 0 ? (
        <div className="brand-mgmt-empty">
          <p>{t('brand.empty')}</p>
        </div>
      ) : (
        <div className="brand-mgmt-list">
          {brands.map(brand => (
            <div key={brand.id} className="brand-mgmt-card">
              <div className="brand-mgmt-card-info">
                <span className="brand-mgmt-card-name">{brand.name}</span>
                <span className="brand-mgmt-card-url">{brand.website_url}</span>
              </div>
              <div className="brand-mgmt-card-actions">
                <button
                  className="brand-mgmt-competitors-btn"
                  onClick={() => openCompetitors(brand)}
                  title={t('brand.view_competitors')}
                >
                  {t('brand.competitors')}
                </button>
                <button
                  className="brand-mgmt-edit-btn"
                  onClick={() => openEdit(brand)}
                >
                  {t('brand.edit')}
                </button>
                <button
                  className="brand-mgmt-delete-btn"
                  onClick={() => handleDelete(brand)}
                  disabled={deleting}
                >
                  {t('brand.delete')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Competitors Dialog */}
      {competitorsBrand && (
        <div className="brand-mgmt-overlay" onClick={closeCompetitors}>
          <div className="brand-mgmt-dialog" onClick={e => e.stopPropagation()} style={{ maxWidth: '480px' }}>
            <button className="brand-mgmt-dialog-close" onClick={closeCompetitors}>
              &times;
            </button>
            <h3>{t('brand.competitors_title', { name: competitorsBrand.name })}</h3>

            {/* Add Competitor Form — Autocomplete */}
            <div className="brand-mgmt-competitor-add" ref={searchRef}>
              <div className="brand-mgmt-autocomplete">
                <input
                  className="wizard-input"
                  value={searchQuery}
                  onChange={e => {
                    setSearchQuery(e.target.value)
                    setShowDropdown(true)
                  }}
                  onFocus={() => setShowDropdown(true)}
                  placeholder={t('brand.competitor_add_placeholder')}
                  disabled={addingCompetitor}
                />
                {showDropdown && searchLoading && (
                  <div className="brand-mgmt-autocomplete-dropdown">
                    <div className="brand-mgmt-autocomplete-empty">
                      {t('brand.competitors_loading')}
                    </div>
                  </div>
                )}
                {showDropdown && !searchLoading && searchResults.length > 0 && (
                  <div className="brand-mgmt-autocomplete-dropdown">
                    <div className="brand-mgmt-autocomplete-items">
                      {searchResults.map(b => (
                        <button
                          key={b.id}
                          className="brand-mgmt-autocomplete-item"
                          onClick={() => selectCompetitorBrand(b)}
                          type="button"
                        >
                          <span className="brand-mgmt-autocomplete-name">{b.name}</span>
                          <span className="brand-mgmt-autocomplete-id">{b.id}</span>
                        </button>
                      ))}
                    </div>
                    {/* Pagination info and controls */}
                    <div className="brand-mgmt-autocomplete-footer">
                      <span className="brand-mgmt-autocomplete-page-info">
                        {t('brand.search_pagination', {
                          current: Math.floor(searchOffset / PAGE_SIZE) + 1,
                          total: Math.max(1, Math.ceil(searchTotal / PAGE_SIZE)),
                          count: searchTotal,
                        })}
                      </span>
                      <div className="brand-mgmt-autocomplete-page-actions">
                        <button
                          className="brand-mgmt-page-btn"
                          onClick={goPrevPage}
                          disabled={searchOffset <= 0 || searchLoading}
                        >
                          ‹
                        </button>
                        <button
                          className="brand-mgmt-page-btn"
                          onClick={goNextPage}
                          disabled={searchOffset + PAGE_SIZE >= searchTotal || searchLoading}
                        >
                          ›
                        </button>
                      </div>
                    </div>
                  </div>
                )}
                {showDropdown && !searchLoading && searchQuery.trim() !== '' && searchResults.length === 0 && (
                  <div className="brand-mgmt-autocomplete-dropdown">
                    <div className="brand-mgmt-autocomplete-empty">
                      {t('brand.competitor_search_empty')}
                    </div>
                  </div>
                )}
              </div>
              <button
                className="brand-mgmt-competitor-add-btn"
                onClick={handleAddCompetitor}
                disabled={addingCompetitor || !searchQuery.trim()}
                title={!searchQuery.trim() ? t('brand.competitor_select_hint') : ''}
              >
                {addingCompetitor ? t('brand.adding') : t('brand.add')}
              </button>
            </div>
            {addCompetitorError && <div className="brand-mgmt-error">{addCompetitorError}</div>}

            {/* Quick Add — Multi-select checkboxes */}
            {availableForQuickAdd.length > 0 && (
              <div className="brand-mgmt-quick-add">
                <label className="brand-mgmt-quick-add-label">{t('brand.quick_add_label', { count: availableForQuickAdd.length })}</label>
                <div className="brand-mgmt-quick-add-list">
                  {availableForQuickAdd.map(b => (
                    <label key={b.id} className="brand-mgmt-quick-add-item">
                      <input
                        type="checkbox"
                        checked={selectedQuickAdd.includes(b.id)}
                        onChange={() => toggleQuickAdd(b.id)}
                        disabled={addingQuickAdd}
                      />
                      <span className="brand-mgmt-quick-add-name">{b.name}</span>
                    </label>
                  ))}
                </div>
                <button
                  className="wizard-btn-secondary"
                  onClick={handleAddQuickAdd}
                  disabled={addingQuickAdd || selectedQuickAdd.length === 0}
                  style={{ alignSelf: 'flex-end', marginTop: '0.25rem' }}
                >
                  {addingQuickAdd ? t('brand.adding') : t('brand.add_selected', { count: selectedQuickAdd.length })}
                </button>
              </div>
            )}

            {competitorsLoading ? (
              <div className="brand-mgmt-competitors-loading">{t('brand.competitors_loading')}</div>
            ) : competitorsError ? (
              <div className="brand-mgmt-error">{competitorsError}</div>
            ) : competitorList.length === 0 ? (
              <div className="brand-mgmt-competitors-empty">
                <p>{t('brand.competitors_empty')}</p>
              </div>
            ) : (
              <div className="brand-mgmt-competitors-list">
                {competitorList.map(c => (
                  <div key={c.competitor_id} className="brand-mgmt-competitor-item">
                    <div className="brand-mgmt-competitor-info">
                      <span className="brand-mgmt-competitor-name">{c.competitor_name}</span>
                      <span className="brand-mgmt-competitor-id">{c.competitor_id}</span>
                    </div>
                    <button
                      className="brand-mgmt-competitor-remove-btn"
                      onClick={() => handleRemoveCompetitor(c.competitor_id)}
                      disabled={removingCompetitorId === c.competitor_id}
                      title={t('brand.competitor_remove')}
                    >
                      {removingCompetitorId === c.competitor_id ? '...' : '×'}
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="brand-mgmt-dialog-actions">
              <button className="wizard-btn-secondary" onClick={closeCompetitors}>
                {t('brand.close')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Dialog */}
      {editingBrand && (
        <div className="brand-mgmt-overlay" onClick={closeEdit}>
          <div className="brand-mgmt-dialog" onClick={e => e.stopPropagation()}>
            <button className="brand-mgmt-dialog-close" onClick={closeEdit}>
              &times;
            </button>
            <h3>{t('brand.edit_title')}</h3>
            <p className="brand-mgmt-dialog-id">
              {t('brand.brand_id')}: <code>{editingBrand.id}</code>
            </p>

            <div className="brand-mgmt-form">
              <label>{t('brand.name')}</label>
              <input
                className="wizard-input"
                value={editName}
                onChange={e => setEditName(e.target.value)}
                disabled={saving}
                placeholder={t('brand.name_placeholder')}
              />

              <label>{t('brand.website_url')}</label>
              <input
                className="wizard-input"
                value={editUrl}
                onChange={e => setEditUrl(e.target.value)}
                disabled={saving}
                placeholder={t('brand.url_placeholder')}
              />
            </div>

            {saveError && <div className="brand-mgmt-error">{saveError}</div>}

            <div className="brand-mgmt-dialog-actions">
              <button
                className="wizard-btn-secondary"
                onClick={closeEdit}
                disabled={saving}
              >
                {t('brand.cancel')}
              </button>
              <button
                className="wizard-btn"
                onClick={handleSave}
                disabled={saving || !editName.trim() || !editUrl.trim()}
              >
                {saving ? t('brand.saving') : t('brand.save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Brand Dialog */}
      {showAdd && (
        <div className="brand-mgmt-overlay" onClick={() => setShowAdd(false)}>
          <div className="brand-mgmt-dialog" onClick={e => e.stopPropagation()}>
            <button className="brand-mgmt-dialog-close" onClick={() => setShowAdd(false)}>
              &times;
            </button>
            <h3>{t('brand.add')}</h3>

            <div className="brand-mgmt-form">
              <label>{t('brand.name')}</label>
              <input className="wizard-input" value={addName} onChange={e => setAddName(e.target.value)} disabled={addSaving} placeholder={t('brand.name_placeholder')} />
              <label>{t('brand.website_url')}</label>
              <input className="wizard-input" value={addUrl} onChange={e => setAddUrl(e.target.value)} disabled={addSaving} placeholder={t('brand.url_placeholder')} />
            </div>

            {addError && <div className="brand-mgmt-error">{addError}</div>}

            <div className="brand-mgmt-dialog-actions">
              <button className="wizard-btn-secondary" onClick={() => setShowAdd(false)} disabled={addSaving}>
                {t('brand.cancel')}
              </button>
              <button className="wizard-btn" onClick={handleAddBrand} disabled={addSaving || !addName.trim() || !addUrl.trim()}>
                {addSaving ? t('brand.saving') : t('brand.add')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
