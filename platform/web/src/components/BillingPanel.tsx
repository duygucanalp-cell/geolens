import { useTranslation } from 'react-i18next'
import { useEffect, useState } from 'react'
import {
  getSubscription,
  listBillingInvoices,
  createCheckoutSession,
  createBillingPortalSession,
  submitEFatura,
  downloadBillingFile,
  eFaturaXMLDownloadUrl,
  invoicePDFDownloadUrl,
} from '../api/client'
import { PanelSkeleton } from './PanelSkeleton'
import type { BillingInvoice } from '../api/client'

interface Props {
  workspaceId: string
}

type Tier = 'free' | 'pro' | 'business' | 'enterprise'

interface PlanInfo {
  tier: Tier
  label: string
  desc: string
  price: string
}

export function BillingPanel({ workspaceId: _ws }: Props) {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language?.startsWith('en') ? 'en-US' : 'tr-TR'

  const [subscription, setSubscription] = useState<{ tenant_id: string; tier: string; updated_at: string } | null>(null)
  const [invoices, setInvoices] = useState<BillingInvoice[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [checkoutLoading, setCheckoutLoading] = useState(false)
  const [portalLoading, setPortalLoading] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  // e-Fatura/e-Arşiv form durumu
  const [efaturaInvoiceId, setEfaturaInvoiceId] = useState<string | null>(null)
  const [efaturaForm, setEfaturaForm] = useState<{
    invoice_type: 'efatura' | 'earsiv'
    vat_rate: number
    customer_name: string
    customer_tax_no: string
    customer_identity: string
    customer_address: string
  }>({
    invoice_type: 'efatura',
    vat_rate: 20,
    customer_name: '',
    customer_tax_no: '',
    customer_identity: '',
    customer_address: '',
  })
  const [submittingEFatura, setSubmittingEFatura] = useState(false)
  const [eFaturaError, setEFaturaError] = useState<string | null>(null)

  useEffect(() => {
    loadData()
  }, [])

  async function loadData() {
    try {
      setLoading(true)
      setError(null)
      const [sub, inv] = await Promise.all([getSubscription(), listBillingInvoices()])
      setSubscription(sub)
      setInvoices(inv.invoices)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('billing.load_error'))
    } finally {
      setLoading(false)
    }
  }

  const plans: PlanInfo[] = [
    { tier: 'pro', label: t('billing.plan_pro'), desc: t('billing.plan_pro_desc'), price: t('billing.plan_pro_price') },
    { tier: 'business', label: t('billing.plan_business'), desc: t('billing.plan_business_desc'), price: t('billing.plan_business_price') },
    { tier: 'enterprise', label: t('billing.plan_enterprise'), desc: t('billing.plan_enterprise_desc'), price: t('billing.plan_enterprise_price') },
  ]

  async function handleUpgrade(tier: Tier) {
    try {
      setCheckoutLoading(true)
      setError(null)
      setNotice(null)
      const success = `${window.location.origin}${window.location.pathname}#billing`
      const cancel = `${window.location.origin}${window.location.pathname}`
      const res = await createCheckoutSession({ tier, success_url: success, cancel_url: cancel })
      if (res.url) {
        window.location.href = res.url
      } else {
        setNotice(`${t('billing.checkout_mock')} (${tier})`)
        loadData()
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('billing.checkout_error'))
    } finally {
      setCheckoutLoading(false)
    }
  }

  async function handlePortal() {
    try {
      setPortalLoading(true)
      setError(null)
      const returnUrl = `${window.location.origin}${window.location.pathname}`
      const res = await createBillingPortalSession(returnUrl)
      if (res.url) {
        window.location.href = res.url
      } else {
        setNotice(t('billing.portal_mock'))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('billing.portal_error'))
    } finally {
      setPortalLoading(false)
    }
  }

  function openEFaturaForm(inv: BillingInvoice) {
    setEFaturaError(null)
    setEfaturaForm({
      invoice_type: 'efatura',
      vat_rate: 20,
      customer_name: inv.customer_name || '',
      customer_tax_no: inv.customer_tax_no || '',
      customer_identity: inv.customer_identity || '',
      customer_address: inv.customer_address || '',
    })
    setEfaturaInvoiceId(inv.id)
  }

  async function handleSubmitEFatura(inv: BillingInvoice) {
    try {
      setSubmittingEFatura(true)
      setEFaturaError(null)
      await submitEFatura(inv.id, {
        invoice_type: efaturaForm.invoice_type,
        vat_rate: efaturaForm.vat_rate,
        customer_name: efaturaForm.customer_name,
        customer_tax_no: efaturaForm.customer_tax_no || undefined,
        customer_identity: efaturaForm.customer_identity || undefined,
        customer_address: efaturaForm.customer_address || undefined,
      })
      setEfaturaInvoiceId(null)
      setNotice(t('billing.efatura_success'))
      await loadData()
    } catch (err) {
      setEFaturaError(err instanceof Error ? err.message : t('billing.efatura_error'))
    } finally {
      setSubmittingEFatura(false)
    }
  }

  async function handleDownload(path: string, name: string) {
    try {
      setError(null)
      await downloadBillingFile(path, name)
    } catch (err) {
      setError(err instanceof Error ? err.message : t('billing.download_error'))
    }
  }

  const TIER_LABELS: Record<string, string> = {
    free: t('billing.tier_free'),
    pro: t('billing.tier_pro'),
    business: t('billing.tier_business'),
    enterprise: t('billing.tier_enterprise'),
  }

  const STATUS_LABELS: Record<string, string> = {
    draft: t('billing.invoice_draft'),
    open: t('billing.invoice_open'),
    paid: t('billing.invoice_paid'),
    void: t('billing.invoice_void'),
    uncollectible: t('billing.invoice_uncollectible'),
  }

  const INVOICE_TYPE_LABELS: Record<string, string> = {
    standard: t('billing.invoice_standard'),
    efatura: t('billing.invoice_efatura'),
    earsiv: t('billing.invoice_earsiv'),
  }

  const GIB_LABELS: Record<string, string> = {
    none: t('billing.gib_none'),
    pending: t('billing.gib_pending'),
    accepted: t('billing.gib_accepted'),
    rejected: t('billing.gib_rejected'),
  }

  function formatAmount(amount: number, currency: string): string {
    const value = amount / 100
    const symbol = currency === 'usd' ? '$' : currency === 'eur' ? '€' : '₺'
    return `${symbol}${value.toLocaleString(dateLocale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  }

  if (loading) return <PanelSkeleton message={t('billing.loading')} />
  if (error && !subscription) return <div className="dashboard-error"><p>{error}</p><button onClick={loadData}>{t('common.retry')}</button></div>

  return (
    <div className="monitoring-panel">
      <div className="monitoring-header">
        <h3>💳 {t('billing.title')}</h3>
        <p className="monitoring-desc">{t('billing.desc')}</p>
      </div>

      {error && <div className="audit-error">{error}</div>}
      {notice && <div className="audit-info">{notice}</div>}

      {/* Mevcut abonelik */}
      {subscription && (
        <div style={{ background: 'var(--surface-2)', padding: '1.25rem', borderRadius: '10px', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-faint)' }}>{t('billing.current_plan')}</span>
              <div style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--text-strong)' }}>
                {TIER_LABELS[subscription.tier] || subscription.tier}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-faint)', marginTop: '0.25rem' }}>
                {t('billing.updated_at')}: {new Date(subscription.updated_at).toLocaleDateString(dateLocale)}
              </div>
            </div>
            <button className="audit-btn" onClick={handlePortal} disabled={portalLoading}>
              {portalLoading ? t('billing.portal_loading') : t('billing.manage_subscription')}
            </button>
          </div>
        </div>
      )}

      {/* Paket seçimi */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
        {t('billing.plans_title')}
      </h4>
      <div className="monitoring-quick-stats" style={{ marginBottom: '1.5rem' }}>
        {plans.map((plan) => {
          const isCurrent = subscription?.tier === plan.tier
          return (
            <div key={plan.tier} className="quick-stat" style={{ padding: '1rem' }}>
              <span className="quick-stat-label" style={{ fontWeight: 700, fontSize: '0.95rem', color: 'var(--text-strong)' }}>
                {plan.label}
              </span>
              <span style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--accent)', marginTop: '0.25rem' }}>
                {plan.price}
              </span>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-faint)', marginTop: '0.25rem' }}>{plan.desc}</span>
              {isCurrent ? (
                <span className="rec-category-badge" style={{ alignSelf: 'center', marginTop: '0.5rem' }}>
                  {t('billing.current')}
                </span>
              ) : (
                <button
                  className="audit-btn"
                  style={{ marginTop: '0.5rem', alignSelf: 'center' }}
                  onClick={() => handleUpgrade(plan.tier)}
                  disabled={checkoutLoading}
                >
                  {checkoutLoading ? t('billing.checkout_loading') : t('billing.upgrade')}
                </button>
              )}
            </div>
          )
        })}
      </div>

      {/* Faturalar */}
      <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
        {t('billing.invoices_title')} ({invoices.length})
      </h4>
      {invoices.length === 0 ? (
        <div className="rec-empty">
          <div className="rec-empty-icon">🧾</div>
          <h4>{t('billing.invoices_empty')}</h4>
          <p>{t('billing.invoices_empty_desc')}</p>
        </div>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
              <th style={{ padding: '0.5rem' }}>{t('billing.invoice_number')}</th>
              <th style={{ padding: '0.5rem' }}>{t('billing.invoice_status')}</th>
              <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('billing.invoice_amount')}</th>
              <th style={{ padding: '0.5rem' }}>{t('billing.invoice_period')}</th>
              <th style={{ padding: '0.5rem' }}>{t('billing.invoice_efatura_type')}</th>
              <th style={{ padding: '0.5rem', textAlign: 'right' }}>{t('billing.invoice_actions')}</th>
            </tr>
          </thead>
          <tbody>
            {invoices.map((inv) => (
              <tr key={inv.id} style={{ borderBottom: '1px solid var(--border)' }}>
                <td style={{ padding: '0.5rem', fontWeight: 600 }}>{inv.number || inv.stripe_invoice_id}</td>
                <td style={{ padding: '0.5rem' }}>
                  <span className={`rec-category-badge ${inv.status === 'paid' ? '' : 'benchmark-trend-down'}`}>
                    {STATUS_LABELS[inv.status] || inv.status}
                  </span>
                </td>
                <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 600 }}>
                  {formatAmount(inv.amount_total, inv.currency)}
                  {inv.vat_rate > 0 && (
                    <div style={{ fontSize: '0.72rem', color: 'var(--text-faint)', fontWeight: 400 }}>
                      KDV %{inv.vat_rate}: {formatAmount(inv.vat_amount, inv.currency)}
                    </div>
                  )}
                </td>
                <td style={{ padding: '0.5rem', color: 'var(--text-faint)', fontSize: '0.8rem' }}>
                  {inv.period_start ? new Date(inv.period_start).toLocaleDateString(dateLocale) : '-'}
                  {inv.period_end ? ` → ${new Date(inv.period_end).toLocaleDateString(dateLocale)}` : ''}
                </td>
                <td style={{ padding: '0.5rem' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                    <span className="rec-category-badge">
                      {INVOICE_TYPE_LABELS[inv.invoice_type] || inv.invoice_type}
                    </span>
                    {inv.invoice_type !== 'standard' && (
                      <span className={`rec-category-badge ${inv.gib_status === 'accepted' ? '' : 'benchmark-trend-down'}`}>
                        {GIB_LABELS[inv.gib_status] || inv.gib_status}
                      </span>
                    )}
                  </div>
                </td>
                <td style={{ padding: '0.5rem', textAlign: 'right' }}>
                  <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                    <button
                      className="link-btn"
                      onClick={() => handleDownload(invoicePDFDownloadUrl(inv.id), `invoice-${inv.number || inv.id}.pdf`)}
                    >
                      {t('billing.invoice_pdf')}
                    </button>
                    {inv.hosted_invoice_url && (
                      <a className="link-btn" href={inv.hosted_invoice_url} target="_blank" rel="noreferrer">
                        {t('billing.invoice_view')}
                      </a>
                    )}
                    {inv.invoice_type !== 'standard' && inv.document_id && (
                      <button
                        className="link-btn"
                        onClick={() => handleDownload(eFaturaXMLDownloadUrl(inv.id), `${inv.number || inv.id}.xml`)}
                      >
                        {t('billing.invoice_xml')}
                      </button>
                    )}
                    {inv.invoice_type === 'standard' && (
                      <button className="link-btn" onClick={() => openEFaturaForm(inv)}>
                        {t('billing.efatura_send')}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* e-Fatura/e-Arşiv formu */}
      {efaturaInvoiceId && (
        <div style={{ background: 'var(--surface-2)', padding: '1.25rem', borderRadius: '10px', marginTop: '1.5rem' }}>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '0.75rem', color: 'var(--text-strong)' }}>
            🧾 {t('billing.efatura_form_title')}
          </h4>
          {eFaturaError && <div className="audit-error">{eFaturaError}</div>}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div>
              <label className="notif-label">{t('billing.efatura_invoice_type')}</label>
              <select
                className="filter-select"
                style={{ width: '100%' }}
                value={efaturaForm.invoice_type}
                onChange={(e) => setEfaturaForm({ ...efaturaForm, invoice_type: e.target.value as 'efatura' | 'earsiv' })}
              >
                <option value="efatura">{t('billing.invoice_efatura')}</option>
                <option value="earsiv">{t('billing.invoice_earsiv')}</option>
              </select>
            </div>
            <div>
              <label className="notif-label">{t('billing.efatura_vat_rate')}</label>
              <select
                className="filter-select"
                style={{ width: '100%' }}
                value={efaturaForm.vat_rate}
                onChange={(e) => setEfaturaForm({ ...efaturaForm, vat_rate: Number(e.target.value) })}
              >
                <option value={20}>%20</option>
                <option value={10}>%10</option>
                <option value={1}>%1</option>
                <option value={0}>%0</option>
              </select>
            </div>
            <div>
              <label className="notif-label">{t('billing.efatura_customer_name')}</label>
              <input
                className="notif-input"
                style={{ width: '100%' }}
                value={efaturaForm.customer_name}
                onChange={(e) => setEfaturaForm({ ...efaturaForm, customer_name: e.target.value })}
                required
              />
            </div>
            <div>
              <label className="notif-label">{t('billing.efatura_customer_tax_no')}</label>
              <input
                className="notif-input"
                style={{ width: '100%' }}
                value={efaturaForm.customer_tax_no}
                onChange={(e) => setEfaturaForm({ ...efaturaForm, customer_tax_no: e.target.value })}
              />
            </div>
            <div>
              <label className="notif-label">{t('billing.efatura_customer_identity')}</label>
              <input
                className="notif-input"
                style={{ width: '100%' }}
                value={efaturaForm.customer_identity}
                onChange={(e) => setEfaturaForm({ ...efaturaForm, customer_identity: e.target.value })}
              />
            </div>
            <div>
              <label className="notif-label">{t('billing.efatura_customer_address')}</label>
              <input
                className="notif-input"
                style={{ width: '100%' }}
                value={efaturaForm.customer_address}
                onChange={(e) => setEfaturaForm({ ...efaturaForm, customer_address: e.target.value })}
              />
            </div>
          </div>
          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem' }}>
            <button
              className="audit-btn"
              onClick={() => handleSubmitEFatura(invoices.find((i) => i.id === efaturaInvoiceId)!)}
              disabled={submittingEFatura}
            >
              {submittingEFatura ? t('billing.efatura_submitting') : t('billing.efatura_submit')}
            </button>
            <button className="logout-btn" onClick={() => setEfaturaInvoiceId(null)} disabled={submittingEFatura}>
              {t('billing.efatura_cancel')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default BillingPanel
