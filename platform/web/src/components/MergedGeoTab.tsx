import { lazy, Suspense, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { SectionNav } from './SectionNav'
import type { Brand } from '../types'

const TechnicalGeoPanel = lazy(() => import('./TechnicalGeoPanel').then(m => ({ default: m.TechnicalGeoPanel })))
const ContentGeoPanel = lazy(() => import('./ContentGeoPanel').then(m => ({ default: m.ContentGeoPanel })))

interface Props {
  workspaceId: string
  brands: Brand[]
}

// Birleşik "GEO Analizleri" sayfası. Tek ortak marka seçici ve yenile butonu
// her iki bölüm arasında paylaşılır — paneller kendi seçicilerini gizler,
// ortak durumu kullanır (bölümler arası paylaşılan state).
export function MergedGeoTab({ workspaceId, brands }: Props) {
  const { t } = useTranslation()
  const { value: brandId, setValue: setBrandId, refreshTick, refresh } = useSharedPageControls(brands[0]?.id ?? '')

  // Markalar güncellenirse geçerli seçim korunur; silinmişse ilk markaya dön
  useEffect(() => {
    if (brands.length === 0) {
      // Liste boşaldıysa stale brandId ile API çağrısı yapılmasın
      if (brandId !== '') setBrandId('')
    } else if (!brands.some(b => b.id === brandId)) {
      setBrandId(brands[0].id)
    }
  }, [brands, brandId])

  // Marka yoksa paneller (sonsuz iskelet yerine) boş durum gösterir
  if (brands.length === 0) {
    return (
      <div className="merged-tab">
        <MergedHeaderWithControls icon="🌐" title={t('tab.geo')} description={t('merged.geo_desc')} />
        <div className="rec-empty">
          <div className="rec-empty-icon">🏷️</div>
          <h4>{t('brand.empty')}</h4>
        </div>
      </div>
    )
  }

  return (
    <div className="merged-tab">
      <MergedHeaderWithControls
        icon="🌐"
        title={t('tab.geo')}
        description={t('merged.geo_desc')}
        onRefresh={refresh}
      >
        <select
          className="filter-select"
          value={brandId}
          onChange={e => setBrandId(e.target.value)}
          aria-label={t('merged.brand_label')}
          title={t('merged.brand_label')}
        >
          {brands.map(b => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
      </MergedHeaderWithControls>

      <SectionNav
        items={[
          { id: 'geo-technical', label: t('technical.title') },
          { id: 'geo-content', label: t('contentgeo.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="geo-technical" className="merged-section">
          <TechnicalGeoPanel workspaceId={workspaceId} brands={brands} embedded brandId={brandId} refreshTick={refreshTick} />
        </section>
        <section id="geo-content" className="merged-section">
          <ContentGeoPanel workspaceId={workspaceId} brands={brands} embedded brandId={brandId} refreshTick={refreshTick} />
        </section>
      </Suspense>
    </div>
  )
}
