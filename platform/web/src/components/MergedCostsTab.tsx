import { lazy, Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PanelSkeleton } from './PanelSkeleton'
import { PeriodSelect, type Period } from './PeriodSelect'

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
  const [period, setPeriod] = useState<Period>('7d')
  const [refreshTick, setRefreshTick] = useState(0)

  return (
    <div className="merged-tab">
      <div className="merged-header merged-header-with-actions">
        <div>
          <h2>💰 {t('tab.costs')}</h2>
          <p>{t('merged.costs_desc')}</p>
        </div>
        <div className="merged-controls">
          <PeriodSelect value={period} onChange={setPeriod} label={t('merged.period_label')} />
          <button
            className="refresh-btn"
            onClick={() => setRefreshTick(v => v + 1)}
            title={t('common.refresh')}
          >
            <span aria-hidden="true">⟳</span> {t('common.refresh')}
          </button>
        </div>
      </div>

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <CostPanel workspaceId={workspaceId} embedded period={period} refreshTick={refreshTick} />
        <UsagePanel workspaceId={workspaceId} embedded period={period} refreshTick={refreshTick} />
        <OptimizationPanel workspaceId={workspaceId} />
      </Suspense>
    </div>
  )
}
