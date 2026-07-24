import { useEffect, useState } from 'react'
import { getNotificationSettings, updateNotificationSettings, sendTestEmail } from '../api/client'
import type { NotificationSettings as Settings } from '../types'

const DAYS = [
  { value: 'monday', label: 'Pazartesi' },
  { value: 'tuesday', label: 'Salı' },
  { value: 'wednesday', label: 'Çarşamba' },
  { value: 'thursday', label: 'Perşembe' },
  { value: 'friday', label: 'Cuma' },
  { value: 'saturday', label: 'Cumartesi' },
  { value: 'sunday', label: 'Pazar' },
]

const FORMATS = [
  { value: 'email', label: 'E-posta' },
  { value: 'pdf', label: 'PDF' },
  { value: 'both', label: 'E-posta + PDF' },
]

interface Props {
  workspaceId: string
}

export function NotificationSettings({ workspaceId }: Props) {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testEmailStatus, setTestEmailStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    loadSettings()
  }, [workspaceId])

  async function loadSettings() {
    try {
      setLoading(true)
      setError(null)
      const data = await getNotificationSettings(workspaceId)
      setSettings(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ayarlar yüklenemedi')
    } finally {
      setLoading(false)
    }
  }

  async function handleSave() {
    if (!settings) return
    try {
      setSaving(true)
      setError(null)
      setSuccess(null)
      const updated = await updateNotificationSettings(workspaceId, settings)
      setSettings(updated)
      setSuccess('Ayarlar kaydedildi')
      setTimeout(() => setSuccess(null), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Kaydedilemedi')
    } finally {
      setSaving(false)
    }
  }

  async function handleTestEmail() {
    if (!settings?.email_address) {
      setTestEmailStatus('Lütfen önce bir e-posta adresi girin')
      return
    }
    try {
      setTestEmailStatus('Gönderiliyor...')
      const result = await sendTestEmail(workspaceId, settings.email_address)
      setTestEmailStatus(`✅ Test e-postası gönderildi: ${result.to}`)
      setTimeout(() => setTestEmailStatus(null), 5000)
    } catch (err) {
      setTestEmailStatus(`❌ ${err instanceof Error ? err.message : 'Gönderilemedi'}`)
    }
  }

  function update<K extends keyof Settings>(key: K, value: Settings[K]) {
    if (!settings) return
    setSettings({ ...settings, [key]: value })
  }

  if (loading) {
    return <div className="notif-settings-loading">Ayarlar yükleniyor...</div>
  }

  if (error && !settings) {
    return <div className="notif-settings-error">{error}</div>
  }

  if (!settings) return null

  return (
    <div className="notif-settings">
      <div className="notif-settings-header">
        <h3>Bildirim Ayarları</h3>
        <p className="notif-settings-desc">
          Haftalık özet e-postaları ve skor düşüş bildirimlerini yapılandırın.
        </p>
      </div>

      {error && <div className="notif-settings-error-msg">{error}</div>}
      {success && <div className="notif-settings-success-msg">{success}</div>}

      <div className="notif-setting-row">
        <label className="notif-setting-label">
          <span>E-posta Adresi</span>
          <input
            type="email"
            className="notif-input"
            value={settings.email_address}
            onChange={(e) => update('email_address', e.target.value)}
            placeholder="ornek@email.com"
          />
        </label>
      </div>

      <div className="notif-setting-row">
        <label className="notif-setting-checkbox">
          <input
            type="checkbox"
            checked={settings.digest_enabled}
            onChange={(e) => update('digest_enabled', e.target.checked)}
          />
          <span>Haftalık özet e-postalarını etkinleştir</span>
        </label>
      </div>

      {settings.digest_enabled && (
        <div className="notif-setting-details">
          <div className="notif-setting-row-inline">
            <label className="notif-setting-label">
              <span>Gün</span>
              <select
                className="notif-select"
                value={settings.digest_day}
                onChange={(e) => update('digest_day', e.target.value)}
              >
                {DAYS.map((d) => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </select>
            </label>
            <label className="notif-setting-label">
              <span>Saat</span>
              <input
                type="time"
                className="notif-input notif-input-sm"
                value={settings.digest_time}
                onChange={(e) => update('digest_time', e.target.value)}
              />
            </label>
            <label className="notif-setting-label">
              <span>Format</span>
              <select
                className="notif-select"
                value={settings.digest_format}
                onChange={(e) => update('digest_format', e.target.value)}
              >
                {FORMATS.map((f) => (
                  <option key={f.value} value={f.value}>{f.label}</option>
                ))}
              </select>
            </label>
          </div>
        </div>
      )}

      <div className="notif-setting-section">
        <h4>Skor Düşüş Bildirimleri</h4>
        <div className="notif-setting-row">
          <label className="notif-setting-checkbox">
            <input
              type="checkbox"
              checked={settings.notify_on_drop}
              onChange={(e) => update('notify_on_drop', e.target.checked)}
            />
            <span>Skor düşüşlerinde e-posta bildirimi gönder</span>
          </label>
        </div>
        {settings.notify_on_drop && (
          <div className="notif-setting-row">
            <label className="notif-setting-label">
              <span>Eşik (% düşüş)</span>
              <div className="notif-threshold-group">
                <input
                  type="range"
                  className="notif-range"
                  min={1}
                  max={50}
                  value={settings.drop_threshold}
                  onChange={(e) => update('drop_threshold', parseInt(e.target.value))}
                />
                <span className="notif-threshold-value">%{settings.drop_threshold}</span>
              </div>
            </label>
          </div>
        )}
      </div>

      <div className="notif-setting-section">
        <h4>Test</h4>
        <p className="notif-setting-hint">
          Ayarları kaydettikten sonra bir test e-postası göndererek bildirim altyapısını test edin.
        </p>
        <button
          className="notif-test-btn"
          onClick={handleTestEmail}
          disabled={!settings.email_address}
        >
          Test E-postası Gönder
        </button>
        {testEmailStatus && (
          <p className={`notif-test-status ${testEmailStatus.startsWith('✅') ? 'success' : ''}`}>
            {testEmailStatus}
          </p>
        )}
      </div>

      <div className="notif-settings-actions">
        <button
          className="notif-save-btn"
          onClick={handleSave}
          disabled={saving}
        >
          {saving ? 'Kaydediliyor...' : 'Ayarları Kaydet'}
        </button>
      </div>
    </div>
  )
}
