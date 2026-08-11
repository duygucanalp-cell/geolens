import { lazy, Suspense, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { SectionNav } from './SectionNav'
import type { Brand } from '../types'

const ReplayPanel = lazy(() => import('./ReplayPanel').then(m => ({ default: m.ReplayPanel })))
const ArchivePanel = lazy(() => import('./ArchivePanel').then(m => ({ default: m.ArchivePanel })))

interface Props {
  workspaceId: string
  brands: Brand[]
}

// Birleşik "Konuşma Verileri" sayfası. Tek ortak marka seçici ve yenile butonu
// her iki bölüm arasında paylaşılır — paneller kendi seçicilerini gizler,
// ortak durumu kullanır (bölümler arası paylaşılan state).
export function MergedReplayTab({ workspaceId, brands }: Props) {
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
        <MergedHeaderWithControls icon="🗨️" title={t('tab.replay')} description={t('merged.replay_desc')} />
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
        icon="🗨️"
        title={t('tab.replay')}
        description={t('merged.replay_desc')}
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
          { id: 'replay-snapshots', label: t('replay.title') },
          { id: 'replay-archive', label: t('archive.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="replay-snapshots" className="merged-section">
          <ReplayPanel workspaceId={workspaceId} brands={brands} embedded brandId={brandId} refreshTick={refreshTick} />
        </section>
        <section id="replay-archive" className="merged-section">
          <ArchivePanel workspaceId={workspaceId} brands={brands} embedded brandId={brandId} refreshTick={refreshTick} />
        </section>
      </Suspense>
    </div>
  )
}
