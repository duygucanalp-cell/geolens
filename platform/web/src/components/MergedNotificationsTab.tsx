import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { useSharedPageControls } from '../hooks/useSharedPageControls'
import { MergedHeaderWithControls } from './MergedHeaderWithControls'
import { PanelSkeleton } from './PanelSkeleton'
import { SearchInput } from './SearchInput'
import { SectionNav } from './SectionNav'
import type { Brand } from '../types'

const NotificationSettings = lazy(() => import('./NotificationSettings').then(m => ({ default: m.NotificationSettings })))
const AlertRulesPanel = lazy(() => import('./AlertRulesPanel'))

interface Props {
  workspaceId: string
  brands: Brand[]
}

// Birleşik "Bildirimler & Kurallar" sayfası. Ortak arama kural listesini
// filtreler; ortak yenile butonu kuralları tazeler. (Kanal bölümü ayar
// formudur: arama uygulanmaz ve kaydedilmemiş düzenlemeler kaybolmasın diye
// ortak yenilemeye de bağlanmaz.)
export function MergedNotificationsTab({ workspaceId, brands }: Props) {
  const { t } = useTranslation()
  const { value: search, setValue: setSearch, refreshTick, refresh } = useSharedPageControls<string>('')

  return (
    <div className="merged-tab">
      <MergedHeaderWithControls
        icon="🔔"
        title={t('tab.notifications')}
        description={t('merged.notifications_desc')}
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
          { id: 'notifications-channels', label: t('notif.title') },
          { id: 'notifications-rules', label: t('alertrules.title') },
        ]}
      />

      <Suspense fallback={<PanelSkeleton compact message={t('dashboard.component_loading')} rows={2} />}>
        <section id="notifications-channels" className="merged-section">
          <NotificationSettings workspaceId={workspaceId} />
        </section>
        <section id="notifications-rules" className="merged-section">
          <AlertRulesPanel workspaceId={workspaceId} brands={brands} embedded searchQuery={search} refreshTick={refreshTick} />
        </section>
      </Suspense>
    </div>
  )
}
