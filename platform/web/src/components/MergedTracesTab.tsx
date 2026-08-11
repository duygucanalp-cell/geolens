import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { SearchInput } from './SearchInput'
import { SectionNav } from './SectionNav'

const AgentTracePanel = lazy(() => import('./AgentTracePanel').then(m => ({ default: m.AgentTracePanel })))
const PromptAuditPanel = lazy(() => import('./PromptAuditPanel'))

interface Props {
  workspaceId: string
}

// Birleşik "İz & Denetim" sayfası. Ortak arama kutusu her iki bölümü de
// filtreler; ortak yenile butonu iki listeyi birden tazeler. Bölümler
// kendi filtre/eylemlerini korur, yalnızca tekrarlanan kontroller paylaşılır.
export function MergedTracesTab({ workspaceId }: Props) {
  const { t } = useTranslation()
  const { value: search, setValue: setSearch, refreshTick, refresh } = useSharedPageControls<string>('')

  return (
    <div className="merged-tab">
      <MergedHeaderWithControls
        icon="🕵️"
        title={t('tab.traces')}
        description={t('merged.traces_desc')}
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
          { id: 'traces-agent', label: t('agenttrace.title') },
          { id: 'traces-audit', label: t('promptaudit.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="traces-agent" className="merged-section">
          <AgentTracePanel workspaceId={workspaceId} embedded searchQuery={search} refreshTick={refreshTick} />
        </section>
        <section id="traces-audit" className="merged-section">
          <PromptAuditPanel workspaceId={workspaceId} embedded searchQuery={search} refreshTick={refreshTick} />
        </section>
      </Suspense>
    </div>
  )
}
