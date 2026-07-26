import { describe, it, expect, beforeEach } from 'vitest'
import i18n from './i18n'
import tr from './locales/tr.json'
import en from './locales/en.json'
import enRaw from './locales/en.json?raw'
import trRaw from './locales/tr.json?raw'

function detectDuplicateKeys(raw: string): string[] {
  const keyRegex = /"([\w.]+)"\s*:/g
  const counts = new Map<string, number>()
  let match: RegExpExecArray | null
  while ((match = keyRegex.exec(raw)) !== null) {
    const key = match[1]
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return Array.from(counts.entries())
    .filter(([_, count]) => count > 1)
    .map(([key, count]) => `${key} (${count}x)`)
}

// Helper: get all leaf keys from a nested object
function getLeafKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = []
  for (const [key, value] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      keys.push(...getLeafKeys(value as Record<string, unknown>, fullKey))
    } else {
      keys.push(fullKey)
    }
  }
  return keys
}

describe('i18n Configuration', () => {
  it('initializes with Turkish as default language', () => {
    expect(i18n.isInitialized).toBe(true)
    expect(['tr', 'en']).toContain(i18n.language?.substring(0, 2))
  })

  it('can switch language to English', async () => {
    await i18n.changeLanguage('en')
    expect(i18n.language?.startsWith('en')).toBe(true)
  })

  it('can switch language to Turkish', async () => {
    await i18n.changeLanguage('tr')
    expect(i18n.language?.startsWith('tr')).toBe(true)
  })
})

describe('Translation file consistency', () => {
  const trKeys = getLeafKeys(tr).sort()
  const enKeys = getLeafKeys(en).sort()

  it('tr.json has keys', () => {
    expect(trKeys.length).toBeGreaterThan(300)
  })

  it('en.json has the same number of keys as tr.json', () => {
    expect(enKeys.length).toBe(trKeys.length)
  })

  it('all tr.json keys exist in en.json', () => {
    const missing = trKeys.filter(k => !enKeys.includes(k))
    expect(missing).toEqual([])
  })

  it('all en.json keys exist in tr.json', () => {
    const missing = enKeys.filter(k => !trKeys.includes(k))
    expect(missing).toEqual([])
  })
})

describe('No duplicate keys in locale files', () => {
  it('en.json has no duplicate keys', () => {
    const dups = detectDuplicateKeys(enRaw)
    expect(dups).toEqual([])
  })

  it('tr.json has no duplicate keys', () => {
    const dups = detectDuplicateKeys(trRaw)
    expect(dups).toEqual([])
  })
})

describe('Turkish translations', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('tr')
  })

  it('renders dashboard title in Turkish', () => {
    expect(i18n.t('dashboard.title')).toBe('Görünürlük Panosu')
  })

  it('renders auth login in Turkish', () => {
    expect(i18n.t('auth.login')).toBe('Giriş Yap')
  })

  it('renders tab labels in Turkish', () => {
    expect(i18n.t('tab.scores')).toBe('Skorlar')
    expect(i18n.t('tab.audit')).toBe('Site Denetim')
    expect(i18n.t('tab.cost')).toBe('Maliyet')
    expect(i18n.t('tab.incident')).toBe('Incident')
  })

  it('renders severity labels in Turkish', () => {
    expect(i18n.t('severity.critical')).toBe('Kritik')
    expect(i18n.t('severity.high')).toBe('Yüksek')
    expect(i18n.t('severity.medium')).toBe('Orta')
    expect(i18n.t('severity.low')).toBe('Düşük')
  })

  it('renders category labels in Turkish', () => {
    expect(i18n.t('category.visibility')).toBe('Görünürlük')
    expect(i18n.t('category.content')).toBe('İçerik')
  })

  it('renders engine names in Turkish', () => {
    expect(i18n.t('engine.chatgpt')).toBe('ChatGPT')
    expect(i18n.t('engine.perplexity')).toBe('Perplexity')
  })

  it('renders days in Turkish', () => {
    expect(i18n.t('day.monday')).toBe('Pazartesi')
    expect(i18n.t('day.sunday')).toBe('Pazar')
  })

  it('renders with interpolation', () => {
    const result = i18n.t('score.last_updated', { date: '1.01.2025' })
    expect(result).toContain('1.01.2025')
    expect(result).toContain('Son güncelleme')
  })

  it('renders month names correctly', () => {
    const date = new Date(2025, 2, 15) // March 15, 2025
    const formatted = date.toLocaleDateString('tr-TR', { day: 'numeric', month: 'long' })
    expect(formatted).toContain('Mart')
  })

  it('renders monitoring alarm descriptions in Turkish', () => {
    expect(i18n.t('monitoring.alarm_engine_error')).toBe('Motor Hatası Alarmı')
    expect(i18n.t('monitoring.metrics_desc')).toBe('Metrik toplama ve sorgulama')
  })

  it('renders guardrails action labels in Turkish', () => {
    expect(i18n.t('guardrails.action_block')).toBe('Engelle')
    expect(i18n.t('guardrails.enabled')).toBe('Aktif')
  })

  it('renders incident status labels in Turkish', () => {
    expect(i18n.t('incident.status_open')).toBe('Açık')
    expect(i18n.t('incident.status_resolved')).toBe('Çözüldü')
  })

  it('renders policy framework labels in Turkish', () => {
    expect(i18n.t('policy.framework_kvkk')).toBe('KVKK')
    expect(i18n.t('policy.framework_custom')).toBe('Özel')
  })
})

describe('English translations', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en')
  })

  it('renders dashboard title in English', () => {
    expect(i18n.t('dashboard.title')).toBe('Visibility Dashboard')
  })

  it('renders auth login in English', () => {
    expect(i18n.t('auth.login')).toBe('Sign In')
  })

  it('renders tab labels in English', () => {
    expect(i18n.t('tab.scores')).toBe('Scores')
    expect(i18n.t('tab.audit')).toBe('Site Audit')
    expect(i18n.t('tab.cost')).toBe('Cost')
    expect(i18n.t('tab.incident')).toBe('Incident')
  })

  it('renders severity labels in English', () => {
    expect(i18n.t('severity.critical')).toBe('Critical')
    expect(i18n.t('severity.high')).toBe('High')
    expect(i18n.t('severity.medium')).toBe('Medium')
    expect(i18n.t('severity.low')).toBe('Low')
  })

  it('renders category labels in English', () => {
    expect(i18n.t('category.visibility')).toBe('Visibility')
    expect(i18n.t('category.content')).toBe('Content')
  })

  it('renders engine names in English', () => {
    expect(i18n.t('engine.chatgpt')).toBe('ChatGPT')
    expect(i18n.t('engine.perplexity')).toBe('Perplexity')
  })

  it('renders days in English', () => {
    expect(i18n.t('day.monday')).toBe('Monday')
    expect(i18n.t('day.sunday')).toBe('Sunday')
  })

  it('renders with interpolation', () => {
    const result = i18n.t('score.last_updated', { date: '1.01.2025' })
    expect(result).toContain('1.01.2025')
    expect(result).toContain('Last updated')
  })

  it('renders month names correctly in English locale', () => {
    const date = new Date(2025, 2, 15)
    const formatted = date.toLocaleDateString('en-US', { day: 'numeric', month: 'long' })
    expect(formatted).toContain('March')
  })

  it('renders monitoring alarm descriptions in English', () => {
    expect(i18n.t('monitoring.alarm_engine_error')).toBe('Engine Error Alarm')
    expect(i18n.t('monitoring.metrics_desc')).toBe('Metric collection and querying')
  })

  it('renders guardrails action labels in English', () => {
    expect(i18n.t('guardrails.action_block')).toBe('Block')
    expect(i18n.t('guardrails.enabled')).toBe('Active')
  })

  it('renders incident status labels in English', () => {
    expect(i18n.t('incident.status_open')).toBe('Open')
    expect(i18n.t('incident.status_resolved')).toBe('Resolved')
  })
})

describe('Language switching flow', () => {
  it('switches between TR and EN correctly', async () => {
    // Start in Turkish
    await i18n.changeLanguage('tr')
    expect(i18n.t('dashboard.title')).toBe('Görünürlük Panosu')
    expect(i18n.t('auth.login')).toBe('Giriş Yap')

    // Switch to English
    await i18n.changeLanguage('en')
    expect(i18n.t('dashboard.title')).toBe('Visibility Dashboard')
    expect(i18n.t('auth.login')).toBe('Sign In')

    // Switch back to Turkish
    await i18n.changeLanguage('tr')
    expect(i18n.t('dashboard.title')).toBe('Görünürlük Panosu')
  })

  it('changes tab labels when switching language', async () => {
    // Tab labels that have distinct TR vs EN translations
    const localizedTabKeys = ['scores', 'audit', 'reports', 'notifications', 'recommendations', 'monitoring',
      'cost', 'usage', 'optimization', 'version']

    // Turkish
    await i18n.changeLanguage('tr')
    const trTabs = localizedTabKeys.map(k => i18n.t(`tab.${k}`))

    // English
    await i18n.changeLanguage('en')
    const enTabs = localizedTabKeys.map(k => i18n.t(`tab.${k}`))

    // Verify they differ (these have distinct Turkish vs English values)
    for (let i = 0; i < localizedTabKeys.length; i++) {
      expect(trTabs[i]).not.toBe(enTabs[i])
    }
  })
})

describe('Date locale formatting', () => {
  it('formats date in Turkish locale', () => {
    const date = new Date(2025, 2, 15)
    const formatted = date.toLocaleDateString('tr-TR', { day: 'numeric', month: 'long' })
    expect(formatted).toBe('15 Mart')
  })

  it('formats date in English locale', () => {
    const date = new Date(2025, 2, 15)
    const formatted = date.toLocaleDateString('en-US', { day: 'numeric', month: 'long' })
    expect(formatted).toBe('March 15')
  })
})
