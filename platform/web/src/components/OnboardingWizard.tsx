import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { getSetupStatus, createBrand, createPanel, createPromptSet, triggerMeasurement, getBrands } from '../api/client'
import type { SetupStatus } from '../types'

interface OnboardingWizardProps {
  workspaceId: string
  onComplete: () => void
}

const STEP_KEYS = ['brand', 'panel', 'prompt_set', 'measurement'] as const
type StepKey = (typeof STEP_KEYS)[number]

const STEP_ICONS: Record<StepKey, string> = {
  brand: '🏷️',
  panel: '📊',
  prompt_set: '💬',
  measurement: '📡',
}

export function OnboardingWizard({ workspaceId, onComplete }: OnboardingWizardProps) {
  const { t } = useTranslation()
  const [status, setStatus] = useState<SetupStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeStep, setActiveStep] = useState<StepKey>('brand')
  const [error, setError] = useState<string | null>(null)

  // Brand form state
  const [brandName, setBrandName] = useState('')
  const [brandUrl, setBrandUrl] = useState('')
  const [creatingBrand, setCreatingBrand] = useState(false)

  // Panel form state
  const [panelName, setPanelName] = useState('')
  const [creatingPanel, setCreatingPanel] = useState(false)

  // Prompt set form state
  const [promptName, setPromptName] = useState('')
  const [promptText, setPromptText] = useState('')
  const [creatingPrompt, setCreatingPrompt] = useState(false)

  // Measurement state
  const [measuring, setMeasuring] = useState(false)
  const [measureDone, setMeasureDone] = useState(false)

  useEffect(() => {
    loadStatus()
  }, [workspaceId])

  useEffect(() => {
    if (status && !status.setup_complete) {
      const nextUndone = status.steps.find(s => !s.done)
      if (nextUndone) {
        setActiveStep(nextUndone.key as StepKey)
      }
    }
  }, [status])

  async function loadStatus() {
    try {
      setLoading(true)
      const s = await getSetupStatus(workspaceId)
      setStatus(s)
      if (s.setup_complete) {
        onComplete()
        return
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('dashboard.error_load'))
    } finally {
      setLoading(false)
    }
  }

  async function refreshStatus() {
    try {
      const s = await getSetupStatus(workspaceId)
      setStatus(s)
      if (s.setup_complete) {
        onComplete()
      }
    } catch {
      // ignore refresh errors
    }
  }

  async function handleCreateBrand() {
    if (!brandName.trim() || !brandUrl.trim()) return
    setCreatingBrand(true)
    setError(null)
    try {
      await createBrand(workspaceId, { name: brandName.trim(), website_url: brandUrl.trim() })
      await refreshStatus()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create brand')
    } finally {
      setCreatingBrand(false)
    }
  }

  async function handleCreatePanel() {
    if (!panelName.trim()) return
    setCreatingPanel(true)
    setError(null)
    try {
      await createPanel(workspaceId, { name: panelName.trim() })
      await refreshStatus()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create panel')
    } finally {
      setCreatingPanel(false)
    }
  }

  async function handleCreatePrompt() {
    if (!promptName.trim() || !promptText.trim()) return
    setCreatingPrompt(true)
    setError(null)
    try {
      await createPromptSet(workspaceId, {
        name: promptName.trim(),
        prompt_text: promptText.trim(),
      })
      await refreshStatus()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create prompt set')
    } finally {
      setCreatingPrompt(false)
    }
  }

  async function handleRunMeasurement() {
    setMeasuring(true)
    setError(null)
    try {
      const b = await getBrands(workspaceId)
      if (b.length === 0) {
        setError('No brands available. Please add a brand first.')
        setMeasuring(false)
        return
      }
      await triggerMeasurement(workspaceId, { brand_id: b[0].id })
      setMeasureDone(true)
      await refreshStatus()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start measurement')
    } finally {
      setMeasuring(false)
    }
  }

  const currentStatus = status

  if (loading) {
    return (
      <div className="wizard-page">
        <div className="wizard-loading">{t('dashboard.loading')}</div>
      </div>
    )
  }

  const isStepDone = (key: string) => currentStatus?.steps.find(s => s.key === key)?.done ?? false
  const isStepActive = (key: string) => activeStep === key

  // Find the current step index for the progress indicator
  const currentStepIndex = STEP_KEYS.indexOf(activeStep)
  const doneCount = currentStatus?.steps.filter(s => s.done).length ?? 0

  return (
    <div className="wizard-page">
      <div className="wizard-card">
        <div className="wizard-header">
          <h1>{t('wizard.title')}</h1>
          <p>{t('wizard.subtitle')}</p>
        </div>

        {/* Progress bar */}
        <div className="wizard-progress">
          <div className="wizard-progress-track">
            <div
              className="wizard-progress-fill"
              style={{ width: `${(doneCount / STEP_KEYS.length) * 100}%` }}
            />
          </div>
          <div className="wizard-step-indicators">
            {STEP_KEYS.map((key, idx) => {
              const done = isStepDone(key)
              const active = isStepActive(key)
              return (
                <div
                  key={key}
                  className={`wizard-step-dot ${done ? 'done' : ''} ${active ? 'active' : ''}`}
                  title={t(`wizard.step_${key}`)}
                >
                  <span className="wizard-step-icon">
                    {done ? '✓' : active ? STEP_ICONS[key] : (idx + 1)}
                  </span>
                </div>
              )
            })}
          </div>
          <div className="wizard-step-labels">
            {STEP_KEYS.map(key => (
              <span
                key={key}
                className={`wizard-step-label ${isStepDone(key) ? 'done' : ''} ${isStepActive(key) ? 'active' : ''}`}
              >
                {t(`wizard.step_${key}_short`)}
              </span>
            ))}
          </div>
        </div>

        {error && (
          <div className="wizard-error">
            {error}
          </div>
        )}

        {/* Step: Add Brand */}
        {activeStep === 'brand' && !isStepDone('brand') && (
          <div className="wizard-step-content">
            <div className="wizard-step-icon-large">{STEP_ICONS.brand}</div>
            <h2>{t('wizard.step_brand')}</h2>
            <p className="wizard-step-desc">{t('wizard.step_brand_desc')}</p>
            <div className="wizard-form">
              <input
                className="wizard-input"
                placeholder={t('wizard.brand_name_placeholder')}
                value={brandName}
                onChange={e => setBrandName(e.target.value)}
                disabled={creatingBrand}
              />
              <input
                className="wizard-input"
                placeholder={t('wizard.brand_url_placeholder')}
                value={brandUrl}
                onChange={e => setBrandUrl(e.target.value)}
                disabled={creatingBrand}
              />
              <button
                className="wizard-btn"
                onClick={handleCreateBrand}
                disabled={creatingBrand || !brandName.trim() || !brandUrl.trim()}
              >
                {creatingBrand ? t('wizard.creating') : t('wizard.add_brand')}
              </button>
            </div>
          </div>
        )}

        {/* Step: Create Panel */}
        {activeStep === 'panel' && !isStepDone('panel') && (
          <div className="wizard-step-content">
            <div className="wizard-step-icon-large">{STEP_ICONS.panel}</div>
            <h2>{t('wizard.step_panel')}</h2>
            <p className="wizard-step-desc">{t('wizard.step_panel_desc')}</p>
            <div className="wizard-form">
              <input
                className="wizard-input"
                placeholder={t('wizard.panel_name_placeholder')}
                value={panelName}
                onChange={e => setPanelName(e.target.value)}
                disabled={creatingPanel}
              />
              <button
                className="wizard-btn"
                onClick={handleCreatePanel}
                disabled={creatingPanel || !panelName.trim()}
              >
                {creatingPanel ? t('wizard.creating') : t('wizard.create_panel')}
              </button>
            </div>
          </div>
        )}

        {/* Step: Create Prompt Set */}
        {activeStep === 'prompt_set' && !isStepDone('prompt_set') && (
          <div className="wizard-step-content">
            <div className="wizard-step-icon-large">{STEP_ICONS.prompt_set}</div>
            <h2>{t('wizard.step_prompt_set')}</h2>
            <p className="wizard-step-desc">{t('wizard.step_prompt_set_desc')}</p>
            <div className="wizard-form">
              <input
                className="wizard-input"
                placeholder={t('wizard.prompt_name_placeholder')}
                value={promptName}
                onChange={e => setPromptName(e.target.value)}
                disabled={creatingPrompt}
              />
              <textarea
                className="wizard-textarea"
                placeholder={t('wizard.prompt_text_placeholder')}
                value={promptText}
                onChange={e => setPromptText(e.target.value)}
                rows={3}
                disabled={creatingPrompt}
              />
              <button
                className="wizard-btn"
                onClick={handleCreatePrompt}
                disabled={creatingPrompt || !promptName.trim() || !promptText.trim()}
              >
                {creatingPrompt ? t('wizard.creating') : t('wizard.create_prompt')}
              </button>
            </div>
          </div>
        )}

        {/* Step: Run Measurement */}
        {activeStep === 'measurement' && !isStepDone('measurement') && (
          <div className="wizard-step-content">
            <div className="wizard-step-icon-large">{STEP_ICONS.measurement}</div>
            <h2>{t('wizard.step_measurement')}</h2>
            <p className="wizard-step-desc">{t('wizard.step_measurement_desc')}</p>
            <div className="wizard-form">
              <button
                className="wizard-btn wizard-btn-primary"
                onClick={handleRunMeasurement}
                disabled={measuring}
              >
                {measuring ? t('wizard.measuring') : measureDone ? t('wizard.measure_done') : t('wizard.run_measurement')}
              </button>
              {measureDone && (
                <p className="wizard-success">{t('wizard.measure_success')}</p>
              )}
            </div>
          </div>
        )}

        {/* All done */}
        {currentStatus?.setup_complete && (
          <div className="wizard-step-content wizard-complete">
            <div className="wizard-step-icon-large">🎉</div>
            <h2>{t('wizard.complete_title')}</h2>
            <p>{t('wizard.complete_desc')}</p>
            <button className="wizard-btn wizard-btn-primary" onClick={onComplete}>
              {t('wizard.go_dashboard')}
            </button>
          </div>
        )}

        {/* Navigation buttons */}
        {!currentStatus?.setup_complete && (
          <div className="wizard-nav">
            {currentStepIndex > 0 && (
              <button
                className="wizard-btn-secondary"
                onClick={() => {
                  setActiveStep(STEP_KEYS[currentStepIndex - 1])
                  setError(null)
                }}
              >
                {t('wizard.back')}
              </button>
            )}
            {isStepDone(activeStep) && currentStepIndex < STEP_KEYS.length - 1 && (
              <button
                className="wizard-btn"
                onClick={() => {
                  setActiveStep(STEP_KEYS[currentStepIndex + 1])
                  setError(null)
                }}
              >
                {t('wizard.next')}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
