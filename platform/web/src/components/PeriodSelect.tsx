import { useTranslation } from 'react-i18next'

export type Period = '1d' | '7d' | '30d' | '90d'

interface Props {
  value: Period
  onChange: (p: Period) => void
  label?: string
}

// Ortak dönem seçici — birleşik sayfalarda tek seçiciyle tüm bölümlerin
// aynı dönemi paylaşmasını sağlar (1d/7d/30d/90d).
export function PeriodSelect({ value, onChange, label }: Props) {
  const { t } = useTranslation()
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as Period)}
      className="filter-select"
      aria-label={label}
      title={label}
    >
      <option value="1d">{t('period.24h')}</option>
      <option value="7d">{t('period.7d')}</option>
      <option value="30d">{t('period.30d')}</option>
      <option value="90d">{t('period.90d')}</option>
    </select>
  )
}
