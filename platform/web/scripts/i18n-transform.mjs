import { readFileSync, writeFileSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const srcDir = join(__dirname, '..', 'src', 'components')

// Turkish → translation key mapping
const TR_KEY_MAP = {
  // ScoreDashboard
  'Yükleniyor...': 'dashboard.loading',
  'Veriler yüklenemedi': 'dashboard.error_load',
  'Tekrar Dene': 'dashboard.retry',
  'Henüz skor yok': 'dashboard.empty_title',
  'Bir marka ekleyip ölçüm başlatarak görünürlük skorunuzu görebilirsiniz.': 'dashboard.empty_desc',
  'Bileşen yükleniyor...': 'dashboard.component_loading',
  'Tüm Paneller': 'dashboard.filter_all_panels',
  'Tüm Motorlar': 'dashboard.filter_all_engines',
  'Motor Kırılımı': 'dashboard.engine_breakdown',
  'Skorlar': 'tab.scores',
  'Site Denetim': 'tab.audit',
  'Raporlar': 'tab.reports',
  'Bildirimler': 'tab.notifications',
  'Öneriler': 'tab.recommendations',
  'İzleme': 'tab.monitoring',
  'Maliyet': 'tab.cost',
  'Kullanım': 'tab.usage',
  'Optimizasyon': 'tab.optimization',
  'Versiyon': 'tab.version',
  'Görünürlük Panosu': 'dashboard.title',
  'Yenile': 'dashboard.refresh',
  'Tekrar Dene': 'dashboard.retry',

  // ScoreCard
  'Motor Kırılımı': 'dashboard.engine_breakdown',
  'Son güncelleme:': null, // special handling

  // TrendChart
  'Trend verisi yok': 'score.trend_empty',
  'Görünürlük Trendi': null, // part of brand title
  'Tarih:': null, // part of tooltip
  'Skor': 'score.trend_tooltip_score',

  // EngineComparison
  'Motor Karşılaştırması': 'score.engine_comparison',
  'Henüz motor verisi yok': 'score.engine_empty',

  // Audit
  'Site Denetimi': 'audit.title',
  'Marka seçin': 'audit.select_brand',
  'Denetim Başlat': 'audit.start',
  'Denetleniyor...': 'audit.running',
  'Denetim başarısız': 'audit.failed',
  '/100 — Denetim Skoru': 'audit.score',
  'Tüm botlar engellenmiş': 'audit.robots_blocked',
  'AI botlarına izin veriyor': 'audit.robots_ai_allowed',
  'robots.txt bulunamadı': 'audit.robots_not_found',
  'Bot Erişimi': 'audit.bot_access',
  'Erişilemez': 'audit.bot_inaccessible',
  'SSR Sinyalleri': 'audit.ssr_signals',
  'Sinyal yok': 'audit.ssr_none',
  'Güvenlik': 'audit.security',
  'Koruma yok': 'audit.security_none',
  'Bulgular (': null,
  'Sorunlar': 'audit.issues',

  // Notifications
  'Bildirim Ayarları': 'notif.title',
  'Ayarlar yükleniyor...': 'notif.loading',
  'Ayarlar yüklenemedi': 'notif.load_error',
  'Kaydedilemedi': 'notif.save_error',
  'Ayarlar kaydedildi': 'notif.saved',
  'Kaydediliyor...': 'notif.saving',
  'Ayarları Kaydet': 'notif.save',
  'E-posta Adresi': 'notif.email',
  'ornek@email.com': 'notif.email_placeholder',
  'Haftalık özet e-postalarını etkinleştir': 'notif.enable_digest',
  'Gün': 'notif.day',
  'Saat': 'notif.time',
  'Format': 'notif.format',
  'Skor Düşüş Bildirimleri': 'notif.score_drops',
  'Skor düşüşlerinde e-posta bildirimi gönder': 'notif.notify_on_drop',
  'Eşik (% düşüş)': 'notif.threshold',
  'Test E-postası Gönder': 'notif.test_send',
  'Lütfen önce bir e-posta adresi girin': 'notif.test_first',
  'Gönderiliyor...': 'notif.test_sending',
  'Gönderilemedi': 'notif.send_failed',
  'Ayarlar': 'notif.title',

  // Reports
  'Haftalık özet e-postaları ve skor düşüş bildirimlerini yapılandırın.': 'notif.desc',
  'Rapor oluşturuluyor...': 'reports.generating_status',
  'Rapor indiriliyor...': 'reports.downloading',
  'Rapor oluşturulamadı': 'reports.generate_failed',
  'Marka Skor Kartı': 'reports.scorecard_title',
  'Çok Yakında': 'reports.coming_soon',

  // Recommendations
  'AI Önerileri': 'rec.title',
  'Öneriler yükleniyor...': 'rec.loading',
  'Öneriler yüklenemedi': 'rec.load_error',
  'Öneri uygulanamadı': 'rec.apply_error',
  'Öneri gizlenemedi': 'rec.dismiss_error',
  'Tüm Markalar': 'rec.filter_all_brands',
  'Henüz öneri yok': 'rec.empty_title',
  'Uygulandı': 'rec.applied',
  'Uygulandı olarak işaretle': 'rec.apply',
  'Öneriyi gizle': 'rec.dismiss',
  'Kritik': 'severity.critical',
  'Yüksek': 'severity.high',
  'Orta': 'severity.medium',
  'Düşük': 'severity.low',
  'Toplam': 'rec.summary_total',
  'güven': null,

  // Cost
  'Maliyet verileri yükleniyor...': 'cost.loading',
  'Maliyet verileri yüklenemedi': 'cost.load_error',
  'Son 24 Saat': 'cost.filter_1d',
  'Son 7 Gün': 'cost.filter_7d',
  'Son 30 Gün': 'cost.filter_30d',
  'Son 90 Gün': 'cost.filter_90d',
  'Henüz maliyet kaydı yok': 'cost.empty_title',
  'Model': 'cost.table_model',
  'İşlem': 'cost.table_operation',
  'Token': 'cost.table_tokens',
  'Maliyet': 'cost.table_cost',
  'Tarih': 'cost.table_date',
  'Toplam Maliyet': 'cost.total_cost',
  'Toplam Token': 'cost.total_tokens',
  'Motor Bazında': 'cost.engine_breakdown',

  // Usage
  'Kullanım verileri yükleniyor...': 'usage.loading',
  'Kullanım verileri yüklenemedi': 'usage.load_error',
  'Toplam İstek': 'usage.total_requests',
  'Hata Oranı': 'usage.error_rate',
  'Ortalama Gecikme': 'usage.avg_latency',
  'Durum': 'usage.table_status',
  'Endpoint': 'usage.table_endpoint',
  'Method': 'usage.table_method',
  'İstek': 'usage.table_hits',
  'Gecikme': 'usage.table_latency',
  'Henüz kullanım verisi yok': 'usage.empty_title',

  // Optimization
  'Optimizasyon önerileri yükleniyor...': 'optimization.loading',
  'Öneriler yüklenemedi': 'optimization.load_error',
  'Öneri Oluştur': 'optimization.generate',
  'Oluşturuluyor...': 'optimization.generating',
  'Öneriler oluşturulamadı': 'optimization.generate_error',
  'Durum güncellenemedi': 'optimization.status_update_error',
  'Henüz optimizasyon önerisi yok': 'optimization.empty_title',
  'Bekliyor': 'optimization.status_pending',
  'Uygulandı': 'optimization.status_implemented',
  'Reddedildi': 'optimization.status_dismissed',
  'Reddet': 'optimization.dismiss',
  'puan potansiyeli': null,

  // Version
  'Versiyon geçmişi yükleniyor...': 'version.loading',
  'Versiyon verileri yüklenemedi': 'version.load_error',
  'Tüm Tipler': 'version.filter_all',
  '← Listeye Dön': 'version.back_list',
  'Varlık Tipi:': 'version.entity_type',
  'Eski Versiyon:': 'version.old_version',
  'Yeni Versiyon:': 'version.new_version',
  'Değişiklik Notu:': 'version.change_notes',
  'Değiştiren:': 'version.changed_by',
  'Tarih:': 'version.date',
  'Henüz versiyon kaydı yok': 'version.empty_title',
  'Varlık': 'version.table_entity',
  'Tip': 'version.table_type',
  'Eski': 'version.table_old',
  'Yeni': 'version.table_new',
  'Detay →': 'version.table_detail',

  // Incident
  'Henüz incident kaydı yok': 'incident.empty_title',
  'Incident verileri yükleniyor...': 'incident.loading',
  'Incident verileri yüklenemedi': 'incident.load_error',

  // Guardrails
  'Guardrail kuralları yükleniyor...': 'guardrails.loading',
  'Kurallar yüklenemedi': 'guardrails.load_error',
  'Henüz guardrail kuralı yok': 'guardrails.empty_title',
  'Kural Ekle': 'guardrails.add_rule',
  'İptal': 'guardrails.cancel',
  'Kural oluşturulamadı': 'guardrails.create_error',
  'Kural güncellenemedi': 'guardrails.toggle_error',
  'Kural silinemedi': 'guardrails.delete_error',
  'Kural Adı': 'guardrails.table_name',
  'Kategori': 'guardrails.table_category',
  'Aksiyon': 'guardrails.table_action',
  'Aktif': 'guardrails.enabled',
  'Pasif': 'guardrails.disabled',

  // Agent Trace
  'Trace bilgileri yükleniyor...': 'agenttrace.loading',
  'Trace verileri yüklenemedi': 'agenttrace.load_error',
  'Trace Başlat': 'agenttrace.start_trace',
  'Trace başlatılamadı': 'agenttrace.create_error',
  'Henüz trace kaydı yok': 'agenttrace.empty_title',
  '← Listeye Dön': 'agenttrace.back',
  'Agent': 'agenttrace.table_agent',
  'Süre': 'agenttrace.table_duration',
  'Adımlar': 'agenttrace.detail_steps',
  'Girdi': 'agenttrace.detail_input',
  'Çıktı': 'agenttrace.detail_output',
  'Hata': 'agenttrace.detail_error',
  'Tamamlandı': 'agenttrace.status_completed',
  'Çalışıyor': 'agenttrace.status_running',
  'Başarısız': 'agenttrace.status_failed',
  'Tümü': 'agenttrace.filter_all',

  // Registry
  'Registry yükleniyor...': 'registry.loading',
  'Yüklenemedi': 'registry.load_error',
  'Oluşturulamadı': 'registry.create_error',
  'Silinemedi': 'registry.delete_error',
  'Varlık Ekle': 'registry.add',
  'Oluştur': 'registry.create',
  'Henüz varlık kaydı yok': 'registry.empty_title',
  'Ad': 'registry.name_placeholder',
  'Açıklama': 'registry.desc_placeholder',
  'Sağlayıcı (opsiyonel)': 'registry.provider_placeholder',
  'Model': 'registry.type_model',
  'Agent': 'registry.type_agent',
  'Uygulama': 'registry.type_application',
  'Dataset': 'registry.type_dataset',
  'Düşük Risk': 'registry.risk_low',
  'Orta Risk': 'registry.risk_medium',
  'Yüksek Risk': 'registry.risk_high',
  'Sil': 'registry.delete',

  // Policy
  'Policy paketleri yükleniyor...': 'policy.loading',
  'Kontroller yüklenemedi': 'policy.controls_error',
  'Güncellenemedi': 'policy.update_error',
  '← Paket Listesi': 'policy.back',
  'Henüz policy paketi yok': 'policy.empty_title',
  'Henüz kontrol yok': 'policy.empty_controls',
  'EU AI Act': 'policy.framework_eu_ai_act',
  'NIST AI RMF': 'policy.framework_nist_ai_rmf',
  'KVKK': 'policy.framework_kvkk',
  'ISO 42001': 'policy.framework_iso_42001',
  'Özel': 'policy.framework_custom',
  'Geçti': 'policy.status_passed',
  'Uygun Değil': 'policy.status_not_applicable',

  // Bias
  'Bias testleri yükleniyor...': 'bias.loading',
  'Değerlendirme hatası': 'bias.eval_error',
  'Yeni Değerlendirme': 'bias.new_eval',
  'Değerlendir': 'bias.evaluate',
  'Değerlendiriliyor...': 'bias.evaluating',
  'Geçersiz JSON verisi': 'bias.invalid_json',
  'Henüz test yok': 'bias.empty_title',
  'Toplam Test': 'bias.summary_total',
  'Ort. Fairness': 'bias.summary_fairness',
  'Bias Tespit': 'bias.summary_bias',
  'Bias Tespit Edildi': 'bias.result_bias_detected',
  'Bias Tespit Edilmedi': 'bias.result_no_bias',
  'Bias Var': 'bias.tag_bias',
  'Adil': 'bias.tag_fair',
  'Model ID': 'bias.model_id_placeholder',
  'Demografik Parite': 'bias.metric_demographic_parity',
  'Eşit Fırsat': 'bias.metric_equal_opportunity',
  'Farklı Etki': 'bias.metric_disparate_impact',

  // Explain
  'Analizler yükleniyor...': 'explain.loading',
  'Açıklama hatası': 'explain.error',
  "Varlık ID (Registry'den)": 'explain.entity_placeholder',
  'Açıkla': 'explain.analyze',
  'Analiz Ediliyor...': 'explain.analyzing',
  'Henüz analiz yok': 'explain.empty_title',

  // Discovery
  'Sonuç alınamadı': 'discovery.load_error',
  'Tarama başlatılamadı': 'discovery.start_error',
  'API Taraması': 'discovery.scan_type_api',
  'Tam Tarama': 'discovery.scan_type_full',
  'Cloud Kaynakları': 'discovery.scan_type_cloud',
  'Tarama Başlat': 'discovery.start',
  'Taranıyor...': 'discovery.scanning',
  'Henüz tarama yapılmadı': 'discovery.empty_title',
  'Shadow AI bulunamadı': 'discovery.no_findings',
  'Bulunan Kaynak': 'discovery.summary_found',

  // Gate
  'Gate check hatası': 'gate.check_error',
  'Geçmiş yüklenemedi': 'gate.history_error',
  'Entity ID veya adı': 'gate.entity_placeholder',
  'Kontrol Ediliyor...': 'gate.checking',
  'Geçmişi Gizle': 'gate.hide_history',
  'Henüz kontrol yapılmadı': 'gate.empty_title',
  'Henüz geçmiş kaydı yok': 'gate.empty_history',
  'Development': 'gate.env_development',
  'Staging': 'gate.env_staging',
  'Production': 'gate.env_production',

  // Monitoring
  'Takip et': null,
}

const files = [
  'ScoreDashboard.tsx', 'ScoreCard.tsx', 'TrendChart.tsx', 'EngineComparison.tsx',
  'AuditPanel.tsx', 'NotificationSettings.tsx', 'ReportsPanel.tsx', 'RecommendationsPanel.tsx',
  'CostPanel.tsx', 'UsagePanel.tsx', 'OptimizationPanel.tsx', 'VersionPanel.tsx',
  'IncidentPanel.tsx', 'GuardrailsPanel.tsx', 'AgentTracePanel.tsx', 'MonitoringPanel.tsx',
  'RegistryPanel.tsx', 'PolicyPacksPanel.tsx', 'BiasPanel.tsx', 'ExplainPanel.tsx',
  'DiscoveryPanel.tsx', 'GatePanel.tsx',
]

let totalReplacements = 0

for (const file of files) {
  const filePath = join(srcDir, file)
  let content

  try {
    content = readFileSync(filePath, 'utf-8')
  } catch {
    console.log(`⚠️ Cannot read ${file}`)
    continue
  }

  // Add const { t } = useTranslation() after the function declaration line
  // Find "export function" or "function" lines
  let modified = content

  // Replace Turkish strings with t() calls
  for (const [trText, key] of Object.entries(TR_KEY_MAP)) {
    if (!key) continue
    // Build regex to find the Turkish text in string literals
    const escaped = trText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const regex = new RegExp(`'${escaped}'`, 'g')
    const replacement = `t('${key}')`
    const before = modified
    modified = modified.replace(regex, replacement)
    const count = (before.match(regex) || []).length
    if (count > 0) {
      totalReplacements += count
    }
  }

  if (modified !== content) {
    writeFileSync(filePath, modified, 'utf-8')
    console.log(`✅ ${file}: translations applied`)
  } else {
    console.log(`⏭️ ${file}: no changes`)
  }
}

console.log(`\n📊 Total replacements: ${totalReplacements}`)
