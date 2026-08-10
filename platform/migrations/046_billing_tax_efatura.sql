-- 046_billing_tax_efatura.sql
-- FR-A6 (HT2): TR özel vergi ve e-Fatura/e-Arşiv altyapısı
-- Mevcut billing.invoices tablosuna KDV ve e-Fatura/e-Arşiv alanları eklenir.
-- Gerçek GİB entegrasyonu kimlik bilgisi gerektirdiğinden kod tarafında
-- mock/sandbox mod desteği sunulur; bu alanlar üretimde GİB ile senkronu taşır.

ALTER TABLE billing.invoices
    -- KDV (KDV hesaplama: subtotal + vat_amount = amount_total)
    ADD COLUMN IF NOT EXISTS subtotal        BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS vat_rate        INT    NOT NULL DEFAULT 0, -- izinli: 0, 1, 10, 20
    ADD COLUMN IF NOT EXISTS vat_amount      BIGINT NOT NULL DEFAULT 0,

    -- Fatura tipi: standard (Stripe), efatura, earsiv
    ADD COLUMN IF NOT EXISTS invoice_type    TEXT   NOT NULL DEFAULT 'standard',

    -- e-Fatura/e-Arşiv zorunlu müşteri alanları
    ADD COLUMN IF NOT EXISTS customer_name      TEXT,
    ADD COLUMN IF NOT EXISTS customer_tax_no    TEXT,
    ADD COLUMN IF NOT EXISTS customer_identity  TEXT,
    ADD COLUMN IF NOT EXISTS customer_address   TEXT,

    -- GİB durumu ve belge kimliği
    ADD COLUMN IF NOT EXISTS gib_status         TEXT NOT NULL DEFAULT 'none', -- none, pending, accepted, rejected
    ADD COLUMN IF NOT EXISTS document_id         TEXT,
    ADD COLUMN IF NOT EXISTS gib_response_id     TEXT,
    ADD COLUMN IF NOT EXISTS efatura_sent_at     TIMESTAMPTZ;

-- e-Fatura gönderilen faturaları bulmak için indeks
CREATE INDEX IF NOT EXISTS idx_invoices_efatura
    ON billing.invoices(gib_status, invoice_type);
