import { useState } from 'react'

interface MetricService {
  title: string
  value: string
  desc: string
  icon: string
  color: string
  url?: string
}

const METRICS: MetricService[] = [
  { title: 'Prometheus', value: '9090', desc: 'Metrik toplama ve sorgulama', icon: '📊', color: '#e6522c', url: 'http://localhost:9090' },
  { title: 'Grafana', value: '4000', desc: 'Metrik görselleştirme ve alarm panosu', icon: '📈', color: '#f46800', url: 'http://localhost:4000' },
  { title: 'API Metrikleri', value: '/metrics', desc: 'Ham Prometheus metrik endpoint\'i', icon: '🔢', color: '#6366f1' },
  { title: 'Health Check', value: '/health', desc: 'API sağlık kontrolü', icon: '💚', color: '#22c55e' },
]

const ALARM_LIST = [
  { name: 'Motor Hatası Alarmı', severity: 'critical', desc: 'Son 5 dakikada >3 engine çağrısı başarısız olursa tetiklenir', rule: 'rate(geolens_engine_calls_failed_total[5m]) > 3' },
  { name: 'DLQ Büyüme Alarmı', severity: 'high', desc: 'Dead letter queue\'da mesaj birikirse tetiklenir', rule: 'geolens_queue_dead_letter_size > 10' },
  { name: 'Kuyruk Tıkanıklığı', severity: 'high', desc: 'Kuyruk derinliği 100\'ü aşarsa tetiklenir', rule: 'geolens_queue_depth > 100' },
  { name: 'Yüksek Hata Oranı', severity: 'medium', desc: 'HTTP 5xx oranı %5\'i aşarsa tetiklenir', rule: 'rate(geolens_http_requests_total{status=~"5.."}[5m]) / rate(geolens_http_requests_total[5m]) > 0.05' },
  { name: 'Yavaş Motor Yanıtı', severity: 'medium', desc: 'Ortalama motor yanıt süresi 30sn\'yi aşarsa tetiklenir', rule: 'histogram_quantile(0.95, rate(geolens_engine_call_duration_seconds_bucket[5m])) > 30' },
]

interface MonitoringPanelProps {
  workspaceId?: string
}

export function MonitoringPanel({ workspaceId: _workspaceId }: MonitoringPanelProps = {}) {
  const [showAlarms, setShowAlarms] = useState(false)

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>📡 İzleme ve Metrikler</h3>
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
          {showAlarms ? '🔽' : '🔼'} Alarm Seti ({ALARM_LIST.length} tanım)
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
