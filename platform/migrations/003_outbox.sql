-- 003_outbox.sql
-- Transactional Outbox tablosu (0305 §3)

CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE public.event_outbox (
    id              TEXT PRIMARY KEY,          -- ULID
    event_type      TEXT NOT NULL,              -- measurement.completed, score.calculated, vb.
    stream          TEXT NOT NULL DEFAULT 'q:measure', -- Redis Stream adı
    payload         JSONB NOT NULL,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    idempotency_key TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at   TIMESTAMPTZ               -- NULL = pending, set = dispatched
);

-- Indexes
CREATE INDEX idx_outbox_pending ON public.event_outbox(dispatched_at, created_at)
    WHERE dispatched_at IS NULL;
CREATE UNIQUE INDEX idx_outbox_idempotency ON public.event_outbox(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_outbox_tenant ON public.event_outbox(tenant_id);
