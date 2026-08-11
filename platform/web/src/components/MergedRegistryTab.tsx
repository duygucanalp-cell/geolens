import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { SearchInput } from './SearchInput'
import { SectionNav } from './SectionNav'

const RegistryPanel = lazy(() => import('./RegistryPanel').then(m => ({ default: m.RegistryPanel })))
const PolicyPacksPanel = lazy(() => import('./PolicyPacksPanel').then(m => ({ default: m.PolicyPacksPanel })))

interface Props {
  workspaceId: string
}

// Birleşik "Model & Politika Kaydı" sayfası. Ortak arama kutusu her iki
// bölümü de filtreler; ortak yenile butonu iki listeyi birden tazeler.
export function MergedRegistryTab({ workspaceId }: Props) {
  const { t } = useTranslation()
  const { value: search, setValue: setSearch, refreshTick, refresh } = useSharedPageControls<string>('')

  return (
    <div className="merged-tab">
      <MergedHeaderWithControls
        icon="🗂️"
        title={t('tab.registry')}
        description={t('merged.registry_desc')}
        onRefresh={refresh}
      >
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder={t('merged.search_placeholder')}
          label={t('merged.search_label')}
        />
      </MergedHeaderWithControls>

      <SectionNav
        items={[
          { id: 'registry-models', label: t('registry.title') },
          { id: 'registry-policy', label: t('policy.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="registry-models" className="merged-section">
          <RegistryPanel workspaceId={workspaceId} embedded searchQuery={search} refreshTick={refreshTick} />
        </section>
        <section id="registry-policy" className="merged-section">
          <PolicyPacksPanel workspaceId={workspaceId} embedded searchQuery={search} refreshTick={refreshTick} />
        </section>
      </Suspense>
    </div>
  )
}
