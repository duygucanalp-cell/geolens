-- 036_incident_management.sql
-- R15: Incident Management — AI olay/kesinti takibi

CREATE SCHEMA IF NOT EXISTS incident;

CREATE TYPE incident.severity AS ENUM ('critical', 'high', 'medium', 'low', 'info');
CREATE TYPE incident.category AS ENUM ('outage', 'degradation', 'bias', 'injection', 'data_leak', 'policy_violation', 'other');
CREATE TYPE incident.incident_status AS ENUM ('open', 'investigating', 'mitigated', 'resolved', 'closed');

CREATE TABLE IF NOT EXISTS incident.events (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    severity        incident.severity NOT NULL DEFAULT 'medium',
    category        incident.category NOT NULL DEFAULT 'other',
    title           TEXT NOT NULL DEFAULT '',
    description     TEXT NOT NULL DEFAULT '',
    status          incident.incident_status NOT NULL DEFAULT 'open',
    source          TEXT NOT NULL DEFAULT '', -- guardrail, audit, manual, gate, etc.
    entity_id       TEXT NOT NULL DEFAULT '',
    assigned_to     TEXT NOT NULL DEFAULT '',
    resolution      TEXT NOT NULL DEFAULT '',
    severity_score  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_incident_events_tenant ON incident.events(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_incident_events_status ON incident.events(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_incident_events_severity ON incident.events(tenant_id, severity);
