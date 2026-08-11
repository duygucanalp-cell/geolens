import { useTranslation } from 'react-i18next'

export interface SectionNavItem {
  id: string
  label: string
}

interface Props {
  items: SectionNavItem[]
}

// Birleşik sayfalarda bölümler arası hızlı gezinme: her bölüm bir anchor
// hedefi (id) taşır, çipler tıklandığında sayfa içinde yumuşak kaydırma yapar.
export function SectionNav({ items }: Props) {
  const { t } = useTranslation()

  if (items.length === 0) return null

  function jump(e: React.MouseEvent<HTMLAnchorElement>, id: string) {
    e.preventDefault()
    const el = document.getElementById(id)
    if (!el) return
    // Hareket azaltma tercihi varsa anında atla (CSS guard JS kaydırmayı kapsamaz)
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    el.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
    // Klavye / ekran okuyucu kullanıcıları için odak da hedefe taşınır
    el.tabIndex = -1
    el.focus({ preventScroll: true })
  }

  return (
    <nav className="section-nav" aria-label={t('merged.section_nav_label')}>
      {items.map(item => (
        <a
          key={item.id}
          href={`#${item.id}`}
          className="section-nav-link"
          onClick={(e) => jump(e, item.id)}
        >
          {item.label}
        </a>
      ))}
    </nav>
  )
}
