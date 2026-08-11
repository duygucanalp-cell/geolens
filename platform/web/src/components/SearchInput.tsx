import { useTranslation } from 'react-i18next'

interface Props {
  value: string
  onChange: (v: string) => void
  placeholder: string
  label: string
}

// Birleşik sayfaların ortak arama kutusu: tek giriş tüm bölümleri filtreler.
export function SearchInput({ value, onChange, placeholder, label }: Props) {
  const { t } = useTranslation()
  return (
    <div className="merged-search">
      <span className="merged-search-icon" aria-hidden="true">🔍</span>
      <input
        className="merged-search-input"
        type="search"
        placeholder={placeholder}
        aria-label={label}
        title={label}
        value={value}
        onChange={e => onChange(e.target.value)}
      />
      {value && (
        <button
          className="merged-search-clear"
          onClick={() => onChange('')}
          aria-label={t('merged.search_clear')}
          title={t('merged.search_clear')}
        >
          ✕
        </button>
      )}
    </div>
  )
}
