import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { SearchInput } from './SearchInput'
import { SectionNav } from './SectionNav'

const BiasPanel = lazy(() => import('./BiasPanel').then(m => ({ default: m.BiasPanel })))
const ExplainPanel = lazy(() => import('./ExplainPanel').then(m => ({ default: m.ExplainPanel })))

interface Props {
  workspaceId: string
}

// Birleşik "Sonuç Analizi" sayfası. Ortak arama kutusu her iki bölümü de
// filtreler; ortak yenile butonu iki listeyi birden tazeler.
export function MergedResultsTab({ workspaceId }: Props) {
  const { t } = useTranslation()
  const { value: search, setValue: setSearch, refreshTick, refresh } = useSharedPageControls<string>('')

  return (
    <div className="merged-tab">
      <MergedHeaderWithControls
        icon="🔬"
        title={t('tab.results')}
        description={t('merged.results_desc')}
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
          { id: 'results-bias', label: t('bias.title') },
          { id: 'results-explain', label: t('explain.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="results-bias" className="merged-section">
          <BiasPanel workspaceId={workspaceId} embedded searchQuery={search} refreshTick={refreshTick} />
        </section>
        <section id="results-explain" className="merged-section">
          <ExplainPanel workspaceId={workspaceId} searchQuery={search} refreshTick={refreshTick} />
        </section>
      </Suspense>
    </div>
  )
}
