import { useTranslation } from 'react-i18next'
import type { ReactNode } from 'react'

interface Props {
  icon: string
  title: string
  description: string
  /** Ortak yenileme: sağlanırsa sağ tarafa yenile butonu eklenir */
  onRefresh?: () => void
  /** Birincil kontrol (arama / dönem / marka seçici) */
  children?: ReactNode
}

// Birleşik sayfaların ortak başlık kartı: başlık + açıklama solda, birincil
// kontrol + yenile butonu sağda. Kontrol yoksa düz başlık kartı render edilir
// (örn. marka listesi boşken).
//
// Not: title EMOJI İÇERMEMELİDİR — ikon ayrı `icon` prop'u ile verilir.
// (tab.guardrails gibi emoji içeren başlıklar bu bileşene taşınmadan önce
// emojileri icon'a ayrılmalıdır.)
export function MergedHeaderWithControls({ icon, title, description, onRefresh, children }: Props) {
  const { t } = useTranslation()
  const hasControls = onRefresh != null || children != null

  return (
    <div className={hasControls ? 'merged-header merged-header-with-actions' : 'merged-header'}>
      <div>
        <h2>{icon} {title}</h2>
        <p>{description}</p>
      </div>
      {hasControls && (
        <div className="merged-controls">
          {children}
          {onRefresh && (
            <button className="refresh-btn" onClick={onRefresh} title={t('common.refresh')}>
              <span aria-hidden="true">⟳</span> {t('common.refresh')}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
