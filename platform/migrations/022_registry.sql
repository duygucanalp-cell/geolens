-- 022_registry.sql
-- R1: AI Registry — model/agent/application envanteri

CREATE SCHEMA IF NOT EXISTS registry;

CREATE TYPE registry.entity_type AS ENUM ('model', 'agent', 'application', 'dataset');
CREATE TYPE registry.lifecycle_state AS ENUM ('development', 'staging', 'production', 'deprecated', 'retired');
CREATE TYPE registry.risk_class AS ENUM ('low', 'medium', 'high', 'critical');

CREATE TABLE IF NOT EXISTS registry.entities (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_type     registry.entity_type NOT NULL,
    name            TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    version         TEXT NOT NULL DEFAULT '1.0.0',
    provider        TEXT NOT NULL DEFAULT '', -- openai, anthropic, custom, etc.
    lifecycle_state registry.lifecycle_state NOT NULL DEFAULT 'development',
    risk_class      registry.risk_class NOT NULL DEFAULT 'medium',
    metadata        JSONB NOT NULL DEFAULT '{}',
    tags            TEXT[] NOT NULL DEFAULT '{}',
    owner           TEXT NOT NULL DEFAULT '',
    documentation_url TEXT NOT NULL DEFAULT '',
    deployed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_registry_tenant ON registry.entities(tenant_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_registry_lifecycle ON registry.entities(tenant_id, lifecycle_state);
CREATE INDEX IF NOT EXISTS idx_registry_risk ON registry.entities(tenant_id, risk_class);
CREATE INDEX IF NOT EXISTS idx_registry_tags ON registry.entities USING GIN(tags);

-- Risk assessments per entity
CREATE TABLE IF NOT EXISTS registry.risk_assessments (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    entity_id       TEXT NOT NULL REFERENCES registry.entities(id) ON DELETE CASCADE,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    risk_class      registry.risk_class NOT NULL,
    score           NUMERIC(5,2) NOT NULL DEFAULT 0,
    summary         TEXT NOT NULL DEFAULT '',
    assessed_by     TEXT NOT NULL DEFAULT '',
    assessed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_registry_risk_entity ON registry.risk_assessments(entity_id);
