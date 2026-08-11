import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { PeriodSelect, type Period } from './PeriodSelect'
import { SectionNav } from './SectionNav'

const CostPanel = lazy(() => import('./CostPanel').then(m => ({ default: m.CostPanel })))
const UsagePanel = lazy(() => import('./UsagePanel').then(m => ({ default: m.UsagePanel })))
const OptimizationPanel = lazy(() => import('./OptimizationPanel').then(m => ({ default: m.OptimizationPanel })))

interface Props {
  workspaceId: string
}

// Birleşik "Maliyet & Kullanım" sayfası. Tek ortak dönem seçici ve yenile
// butonu tüm bölümler arasında paylaşılır — her panel kendi kontrolünü
// göstermez, ortak durumu kullanır (bölümler arası paylaşılan state).
export function MergedCostsTab({ workspaceId }: Props) {
  const { t } = useTranslation()
  const { value: period, setValue: setPeriod, refreshTick, refresh } = useSharedPageControls<Period>('7d')

  return (
    <div className="merged-tab">
      <MergedHeaderWithControls
        icon="💰"
        title={t('tab.costs')}
        description={t('merged.costs_desc')}
        onRefresh={refresh}
      >
        <PeriodSelect value={period} onChange={setPeriod} label={t('merged.period_label')} />
      </MergedHeaderWithControls>

      <SectionNav
        items={[
          { id: 'costs-cost', label: t('cost.title') },
          { id: 'costs-usage', label: t('usage.title') },
          { id: 'costs-optimization', label: t('optimization.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="costs-cost" className="merged-section">
          <CostPanel workspaceId={workspaceId} embedded period={period} refreshTick={refreshTick} />
        </section>
        <section id="costs-usage" className="merged-section">
          <UsagePanel workspaceId={workspaceId} embedded period={period} refreshTick={refreshTick} />
        </section>
        <section id="costs-optimization" className="merged-section">
          <OptimizationPanel workspaceId={workspaceId} />
        </section>
      </Suspense>
    </div>
  )
}
