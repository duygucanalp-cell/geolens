import { useTranslation } from 'react-i18next'
import { useState, useEffect } from 'react'

interface MetricService {
  title: string
  value: string
  desc: string
  icon: string
  color: string
  url?: string
}

interface MonitoringPanelProps {
  workspaceId?: string
}

export function MonitoringPanel({ workspaceId: _workspaceId }: MonitoringPanelProps = {}) {
  const { t } = useTranslation()

  const [healthStatus, setHealthStatus] = useState<string | null>(null)
  const [healthLoading, setHealthLoading] = useState(true)

  useEffect(() => {
    checkHealth()
    const interval = setInterval(checkHealth, 30000)
    return () => clearInterval(interval)
  }, [])

  async function checkHealth() {
    try {
      setHealthLoading(true)
      const res = await fetch('/health')
      const data = await res.json()
      setHealthStatus(data.status === 'ok' ? '✅ Healthy' : '⚠️ Degraded')
    } catch {
      setHealthStatus('❌ Unreachable')
    } finally {
      setHealthLoading(false)
    }
  }

  const METRICS: MetricService[] = [
    { title: 'Prometheus', value: '9090', desc: t('monitoring.metrics_desc'), icon: '📊', color: '#e6522c', url: 'http://localhost:9090' },
    { title: 'Grafana', value: '4000', desc: t('monitoring.grafana_desc'), icon: '📈', color: '#f46800', url: 'http://localhost:4000' },
    { title: 'API Metrics', value: '/metrics', desc: t('monitoring.metrics_endpoint_desc'), icon: '🔢', color: '#6366f1' },
    { title: 'Health Check', value: healthLoading ? '...' : (healthStatus || '/health'), desc: t('monitoring.health_desc'), icon: healthLoading ? '⏳' : (healthStatus?.startsWith('✅') ? '💚' : '❤️‍🔥'), color: healthStatus?.startsWith('✅') ? '#22c55e' : (healthStatus?.startsWith('⚠️') ? '#eab308' : '#ef4444') },
  ]

  const ALARM_LIST = [
    { name: t('monitoring.alarm_engine_error'), severity: 'critical', desc: t('monitoring.alarm_engine_error_desc'), rule: 'rate(geolens_engine_calls_failed_total[5m]) > 3' },
    { name: t('monitoring.alarm_dlq_growth'), severity: 'high', desc: t('monitoring.alarm_dlq_desc'), rule: 'geolens_queue_dead_letter_size > 10' },
    { name: t('monitoring.alarm_queue_blocked'), severity: 'high', desc: t('monitoring.alarm_queue_blocked_desc'), rule: 'geolens_queue_depth > 100' },
    { name: t('monitoring.alarm_high_error_rate'), severity: 'medium', desc: t('monitoring.alarm_high_error_rate_desc'), rule: 'rate(geolens_http_requests_total{status=~"5.."}[5m]) / rate(geolens_http_requests_total[5m]) > 0.05' },
    { name: t('monitoring.alarm_slow_engine'), severity: 'medium', desc: t('monitoring.alarm_slow_engine_desc'), rule: 'histogram_quantile(0.95, rate(geolens_engine_call_duration_seconds_bucket[5m])) > 30' },
  ]

  const [showAlarms, setShowAlarms] = useState(false)

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>{t('monitoring.title')}</h3>
        <p className="monitoring-desc">Prometheus metrik altyapısı ile API, kuyruk ve motor performansını izleyin.</p>
      </div>

      {/* Service Cards */}
      <div className="monitoring-services">
        {METRICS.map((m) => {
          const content = (
            <>
              <div className="monitoring-card-icon">{m.icon}</div>
              <div className="monitoring-card-info">
                <h4>{m.title}</h4>
                <p>{m.desc}</p>
                <span className="monitoring-card-value" style={{ color: m.color }}>{m.value}</span>
              </div>
            </>
          )
          return m.url ? (
            <a key={m.title} href={m.url} target="_blank" rel="noopener noreferrer" className="monitoring-card" style={{ borderTopColor: m.color }}>
              {content}
            </a>
          ) : (
            <div key={m.title} className="monitoring-card" style={{ borderTopColor: m.color }}>
              {content}
            </div>
          )
        })}
      </div>

      {/* Quick Stats */}
      <div className="monitoring-section">
        <h4>📊 Hızlı Metrikler</h4>
        <div className="monitoring-quick-stats">
          <div className="quick-stat">
            <span className="quick-stat-label">API Metrikleri</span>
            <ul className="quick-stat-list">
              <li><code>geolens_http_requests_total</code> — Toplam istek sayısı</li>
              <li><code>geolens_http_request_duration_seconds</code> — İstek süresi dağılımı</li>
              <li><code>geolens_http_requests_in_flight</code> — Anlık işlenen istek</li>
            </ul>
          </div>
          <div className="quick-stat">
            <span className="quick-stat-label">Motor Metrikleri</span>
            <ul className="quick-stat-list">
              <li><code>geolens_engine_calls_total</code> — Motor çağrı sayısı</li>
              <li><code>geolens_engine_calls_failed_total</code> — Başarısız çağrı</li>
              <li><code>geolens_engine_call_duration_seconds</code> — Çağrı süresi</li>
            </ul>
          </div>
          <div className="quick-stat">
            <span className="quick-stat-label">Kuyruk Metrikleri</span>
            <ul className="quick-stat-list">
              <li><code>geolens_queue_messages_produced_total</code> — Üretilen mesaj</li>
              <li><code>geolens_queue_messages_consumed_total</code> — Tüketilen mesaj</li>
              <li><code>geolens_queue_depth</code> — Kuyruk derinliği</li>
            </ul>
          </div>
        </div>
      </div>

      {/* Alarm Seti */}
      <div className="monitoring-section">
        <button
          className="monitoring-collapse-btn"
          onClick={() => setShowAlarms(!showAlarms)}
        >
          {showAlarms ? '🔽' : '🔼'} {t('monitoring.alarm_set', { show: showAlarms ? '🔽' : '🔼', count: ALARM_LIST.length })}
        </button>
        {showAlarms && (
          <div className="alarm-list">
            {ALARM_LIST.map((a) => (
              <div key={a.name} className={`alarm-card alarm-${a.severity}`}>
                <div className="alarm-header">
                  <span className={`alarm-severity severity-${a.severity}`}>
                    {a.severity === 'critical' ? '🔴' : a.severity === 'high' ? '🟠' : '🟡'} {a.severity.toUpperCase()}
                  </span>
                  <strong>{a.name}</strong>
                </div>
                <p className="alarm-desc">{a.desc}</p>
                <code className="alarm-rule">{a.rule}</code>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
