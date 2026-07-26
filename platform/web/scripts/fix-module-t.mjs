import { readFileSync, writeFileSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const componentsDir = join(__dirname, '..', 'src', 'components')

// File-specific fixes: replace module-level t('key') with original Turkish text
const fixes = {
  'AgentTracePanel.tsx': [
    [/t\('agenttrace\.status_running'\)/g, "'Çalışıyor'"],
    [/t\('agenttrace\.status_completed'\)/g, "'Tamamlandı'"],
    [/t\('agenttrace\.status_failed'\)/g, "'Başarısız'"],
    [/t\('guardrails\.cancel'\)/g, "'İptal'"],
  ],
  'BiasPanel.tsx': [
    [/t\('bias\.metric_demographic_parity'\)/g, "'Demografik Parite'"],
    [/t\('bias\.metric_equal_opportunity'\)/g, "'Eşit Fırsat'"],
    [/t\('bias\.metric_disparate_impact'\)/g, "'Farklı Etki'"],
  ],
  'DiscoveryPanel.tsx': [
    [/t\('discovery\.risk_critical'\)/g, "'Kritik'"],
    [/t\('discovery\.risk_high'\)/g, "'Yüksek'"],
    [/t\('discovery\.risk_medium'\)/g, "'Orta'"],
    [/t\('discovery\.risk_low'\)/g, "'Düşük'"],
  ],
  'GuardrailsPanel.tsx': [
    [/t\('policy\.framework_custom'\)/g, "'Özel'"],
  ],
  'OptimizationPanel.tsx': [
    [/t\('severity\.high'\)/g, "'Yüksek'"],
    [/t\('severity\.medium'\)/g, "'Orta'"],
    [/t\('severity\.low'\)/g, "'Düşük'"],
    [/t\('optimization\.status_pending'\)/g, "'Bekliyor'"],
    [/t\('optimization\.status_implemented'\)/g, "'Uygulandı'"],
    [/t\('optimization\.status_dismissed'\)/g, "'Reddedildi'"],
  ],
  'PolicyPacksPanel.tsx': [
    [/t\('policy\.framework_eu_ai_act'\)/g, "'EU AI Act'"],
    [/t\('policy\.framework_nist_ai_rmf'\)/g, "'NIST AI RMF'"],
    [/t\('policy\.framework_kvkk'\)/g, "'KVKK'"],
    [/t\('policy\.framework_iso_42001'\)/g, "'ISO 42001'"],
    [/t\('policy\.framework_custom'\)/g, "'Özel'"],
    [/t\('policy\.status_pending'\)/g, "'Bekliyor'"],
    [/t\('policy\.status_passed'\)/g, "'Geçti'"],
    [/t\('policy\.status_not_applicable'\)/g, "'Uygun Değil'"],
  ],
  'IncidentPanel.tsx': [
    [/t\('incident\.status_open'\)/g, "'Açık'"],
    [/t\('incident\.status_investigating'\)/g, "'İnceleniyor'"],
    [/t\('incident\.status_mitigated'\)/g, "'Hafifletildi'"],
    [/t\('incident\.status_resolved'\)/g, "'Çözüldü'"],
    [/t\('incident\.status_closed'\)/g, "'Kapatıldı'"],
  ],
}

let totalFixed = 0

for (const [file, replacements] of Object.entries(fixes)) {
  const filePath = join(componentsDir, file)
  try {
    let content = readFileSync(filePath, 'utf-8')
    let changed = false

    for (const [pattern, replacement] of replacements) {
      const newContent = content.replace(pattern, replacement)
      if (newContent !== content) {
        totalFixed += (content.match(pattern) || []).length
        content = newContent
        changed = true
      }
    }

    if (changed) {
      writeFileSync(filePath, content, 'utf-8')
      console.log(`✅ ${file}: fixed module-level t() calls`)
    } else {
      console.log(`⏭️ ${file}: no changes needed`)
    }
  } catch (err) {
    console.log(`❌ ${file}: ${err.message}`)
  }
}

console.log(`\n📊 Total module-level fixes: ${totalFixed}`)
