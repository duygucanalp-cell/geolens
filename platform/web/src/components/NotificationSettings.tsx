import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import { getNotificationSettings, updateNotificationSettings, sendTestEmail } from '../api/client'
import type { NotificationSettings as Settings } from '../types'

interface Props {
  workspaceId: string
}

export function NotificationSettings({ workspaceId }: Props) {
  const { t } = useTranslation()
  const DAYS = [
    { value: 'monday', label: t('day.monday') },
    { value: 'tuesday', label: t('day.tuesday') },
    { value: 'wednesday', label: t('day.wednesday') },
    { value: 'thursday', label: t('day.thursday') },
    { value: 'friday', label: t('day.friday') },
    { value: 'saturday', label: t('day.saturday') },
    { value: 'sunday', label: t('day.sunday') },
  ]

  const FORMATS = [
    { value: 'email', label: t('format.email') },
    { value: 'pdf', label: t('format.pdf') },
    { value: 'both', label: t('format.both') },
  ]
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
      setError(err instanceof Error ? err.message : t('notif.load_error'))
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
      setSuccess(t('notif.saved'))
      setTimeout(() => setSuccess(null), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('notif.save_error'))
    } finally {
      setSaving(false)
    }
  }

  async function handleTestEmail() {
    if (!settings?.email_address) {
      setTestEmailStatus(t('notif.test_first'))
      return
    }
    try {
      setTestEmailStatus(t('notif.test_sending'))
      const result = await sendTestEmail(workspaceId, settings.email_address)
      setTestEmailStatus(t('notif.test_sent', { to: result.to }))
      setTimeout(() => setTestEmailStatus(null), 5000)
    } catch (err) {
      setTestEmailStatus(`❌ ${err instanceof Error ? err.message : t('notif.send_failed')}`)
    }
  }

  function update<K extends keyof Settings>(key: K, value: Settings[K]) {
    if (!settings) return
    setSettings({ ...settings, [key]: value })
  }

  if (loading) {
    return <div className="notif-settings-loading">{t('notif.loading')}</div>
  }

  if (error && !settings) {
    return <div className="notif-settings-error">{error}</div>
  }

  if (!settings) return null

  return (
    <div className="notif-settings">
      <div className="notif-settings-header">
        <h3>{t('notif.title')}</h3>
        <p className="notif-settings-desc">
          {t('notif.desc')}
        </p>
      </div>

      {error && <div className="notif-settings-error-msg">{error}</div>}
      {success && <div className="notif-settings-success-msg">{success}</div>}

      <div className="notif-setting-row">
        <label className="notif-setting-label">
          <span>{t('notif.email')}</span>
          <input
            type="email"
            className="notif-input"
            value={settings.email_address}
            onChange={(e) => update('email_address', e.target.value)}
            placeholder={t('notif.email_placeholder')}
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
          <span>{t('notif.enable_digest')}</span>
        </label>
      </div>

      {settings.digest_enabled && (
        <div className="notif-setting-details">
          <div className="notif-setting-row-inline">
            <label className="notif-setting-label">
              <span>{t('notif.day')}</span>
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
              <span>{t('notif.time')}</span>
              <input
                type="time"
                className="notif-input notif-input-sm"
                value={settings.digest_time}
                onChange={(e) => update('digest_time', e.target.value)}
              />
            </label>
            <label className="notif-setting-label">
              <span>{t('notif.format')}</span>
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
        <h4>{t('notif.score_drops')}</h4>
        <div className="notif-setting-row">
          <label className="notif-setting-checkbox">
            <input
              type="checkbox"
              checked={settings.notify_on_drop}
              onChange={(e) => update('notify_on_drop', e.target.checked)}
            />
            <span>{t('notif.notify_on_drop')}</span>
          </label>
        </div>
        {settings.notify_on_drop && (
          <div className="notif-setting-row">
            <label className="notif-setting-label">
              <span>{t('notif.threshold')}</span>
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
        <h4>{t('notif.test')}</h4>
        <p className="notif-setting-hint">
          {t('notif.test_hint')}
        </p>
        <button
          className="notif-test-btn"
          onClick={handleTestEmail}
          disabled={!settings.email_address}
        >
          {t('notif.test_send')}
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
          {saving ? t('notif.saving') : t('notif.save')}
        </button>
      </div>
    </div>
  )
}
