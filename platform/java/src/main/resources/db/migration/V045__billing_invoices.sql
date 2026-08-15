-- 045_billing_invoices.sql
-- FR-A6 (HT2): Self-serve ödeme UI — Stripe faturaları
-- Stripe webhook (invoice.created / invoice.paid / invoice.finalized) ile senkronize edilir.
-- Kullanıcıya fatura görüntüleme (otomatik fatura, e-Fatura/e-Arşiv uyum süreci) sağlar.

CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE IF NOT EXISTS billing.invoices (
    id                  TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id           TEXT NOT NULL,

    -- Stripe referansları
    stripe_invoice_id   TEXT NOT NULL UNIQUE,
    stripe_subscription TEXT,

    -- Fatura bilgileri
    number              TEXT,
    status              TEXT NOT NULL DEFAULT 'draft', -- draft, open, paid, void, uncollectible
    amount_total        BIGINT NOT NULL DEFAULT 0,     -- kuruş
    currency            TEXT NOT NULL DEFAULT 'try',

    -- Dönem
    period_start        TIMESTAMPTZ,
    period_end          TIMESTAMPTZ,

    -- Görüntüleme / indirme
    hosted_invoice_url  TEXT,
    invoice_pdf         TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Kiracı bazlı fatura listeleme
CREATE INDEX IF NOT EXISTS idx_invoices_tenant_created
    ON billing.invoices(tenant_id, created_at DESC);

-- RLS: kiracı izolasyonu (ADR-004)
ALTER TABLE billing.invoices ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON billing.invoices
    USING (tenant_id = identity.get_tenant_id());

-- Stripe customer → tenant eşlemesi (webhook olaylarında tenant çözümü için)
CREATE TABLE IF NOT EXISTS billing.stripe_customers (
    tenant_id    TEXT PRIMARY KEY,
    customer_id  TEXT NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE billing.stripe_customers ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON billing.stripe_customers
    USING (tenant_id = identity.get_tenant_id());
