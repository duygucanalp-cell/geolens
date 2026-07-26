-- 029_agent_traces.sql
-- R8: Agent Tracing — multi-step agent iş akışı takibi

CREATE SCHEMA IF NOT EXISTS agent;

CREATE TYPE agent.trace_status AS ENUM ('running', 'completed', 'failed', 'cancelled');

CREATE TYPE agent.step_status AS ENUM ('pending', 'running', 'completed', 'failed');

CREATE TABLE IF NOT EXISTS agent.traces (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    agent_name      TEXT NOT NULL DEFAULT '',
    workflow_name   TEXT NOT NULL DEFAULT '',
    status          agent.trace_status NOT NULL DEFAULT 'running',
    total_steps     INTEGER NOT NULL DEFAULT 0,
    completed_steps INTEGER NOT NULL DEFAULT 0,
    total_duration_ms INTEGER NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_traces_tenant ON agent.traces(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_traces_status ON agent.traces(tenant_id, status);

CREATE TABLE IF NOT EXISTS agent.steps (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    trace_id        TEXT NOT NULL REFERENCES agent.traces(id) ON DELETE CASCADE,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    step_name       TEXT NOT NULL DEFAULT '',
    agent_name      TEXT NOT NULL DEFAULT '',
    input           TEXT NOT NULL DEFAULT '',
    output          TEXT NOT NULL DEFAULT '',
    status          agent.step_status NOT NULL DEFAULT 'pending',
    duration_ms     INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT NOT NULL DEFAULT '',
    metadata        JSONB NOT NULL DEFAULT '{}',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_steps_trace ON agent.steps(trace_id, started_at);
