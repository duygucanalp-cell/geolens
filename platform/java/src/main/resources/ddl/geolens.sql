-- GeoLens JOOQ codegen şeması (DDLDatabase kaynağı).
--
-- Bu dosya YALNIZCA jOOQ kod üretimi içindir (pom.xml → jooq-codegen-maven → DDLDatabase).
-- Üretim şemasının tek kaynağı platform/migrations/*.sql'tir; bu dosya, DAO'ların
-- (JooqScoreDao / JooqRecommendationDao / JooqSentimentDao) kullandığı tabloların
-- migration'ların ULAŞTIĞI FİNAL HALİNİ kopyalar.
--
-- Neden birebir migration dosyaları değil: OSS jOOQ parser'ı `CREATE OR REPLACE FUNCTION`
-- (identity.get_tenant_id) ve benzeri ifadeleri çözemez ("feature only supported in pro
-- edition"). RLS/function/extension/index/policy ifadeleri buraya alınmaz — kod üretimi
-- için yalnızca tablo/kolon/tip/kısıt gerekir.
--
-- Migration'lar değiştiğinde bu dosya senkron tutulmalıdır (ADR-014).

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS config;
CREATE SCHEMA IF NOT EXISTS measure;
CREATE SCHEMA IF NOT EXISTS governance;
CREATE SCHEMA IF NOT EXISTS recommendation;
CREATE SCHEMA IF NOT EXISTS analysis;
CREATE SCHEMA IF NOT EXISTS replay;
CREATE SCHEMA IF NOT EXISTS archive;

-- 001_initial.sql
CREATE TABLE identity.tenants (
    id         VARCHAR(65535) PRIMARY KEY,
    name       VARCHAR(65535) NOT NULL,
    slug       VARCHAR(65535) NOT NULL UNIQUE,
    tier       VARCHAR(65535) NOT NULL DEFAULT 'free',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 001_initial.sql + 018_archive.sql
CREATE TABLE config.workspaces (
    id          VARCHAR(65535) PRIMARY KEY,
    tenant_id   VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    name        VARCHAR(65535) NOT NULL,
    slug        VARCHAR(65535) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

-- 001_initial.sql + 018_archive.sql + 049_benchmark_sector.sql
CREATE TABLE config.brands (
    id           VARCHAR(65535) PRIMARY KEY,
    workspace_id VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    name         VARCHAR(65535) NOT NULL,
    website_url  VARCHAR(65535) NOT NULL,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    sector       VARCHAR(65535) NOT NULL DEFAULT '',
    archived_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 002_measure.sql
CREATE TABLE measure.measurement_jobs (
    id              VARCHAR(65535) PRIMARY KEY,
    tenant_id       VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    workspace_id    VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    brand_id        VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    panel_id        VARCHAR(65535),
    engine_name     VARCHAR(65535) NOT NULL,
    status          VARCHAR(65535) NOT NULL DEFAULT 'pending',
    prompt_text     VARCHAR(65535) NOT NULL,
    sample_count    INT NOT NULL DEFAULT 3,
    idempotency_key VARCHAR(65535) NOT NULL UNIQUE,
    error_message   VARCHAR(65535),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 002_measure.sql + 051_raw_responses_prompt.sql
CREATE TABLE measure.raw_responses (
    id           VARCHAR(65535) PRIMARY KEY,
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    job_id       VARCHAR(65535) NOT NULL REFERENCES measure.measurement_jobs(id),
    engine_name  VARCHAR(65535) NOT NULL,
    raw_body     VARCHAR(65535) NOT NULL,
    content_text VARCHAR(65535),
    s3_ref       VARCHAR(65535),
    engine_meta  JSONB,
    brand_id     VARCHAR(65535) REFERENCES config.brands(id),
    workspace_id VARCHAR(65535) REFERENCES config.workspaces(id),
    prompt_text  VARCHAR(65535) NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 002_measure.sql
CREATE TABLE measure.scores (
    id                 VARCHAR(65535) PRIMARY KEY,
    tenant_id          VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    workspace_id       VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    brand_id           VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    panel_id           VARCHAR(65535),
    calculation_run_id VARCHAR(65535) NOT NULL,
    value              DOUBLE PRECISION NOT NULL,
    ci_low             DOUBLE PRECISION,
    ci_high            DOUBLE PRECISION,
    fidelity_label     VARCHAR(65535) NOT NULL,
    engine_breakdown   JSONB,
    panel_version      VARCHAR(65535) NOT NULL DEFAULT '1.0',
    freshness_at       TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 002_measure.sql
CREATE TABLE measure.calculation_runs (
    id                VARCHAR(65535) PRIMARY KEY,
    tenant_id         VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    panel_id          VARCHAR(65535),
    algorithm_version VARCHAR(65535) NOT NULL DEFAULT '1.0',
    component_values  JSONB NOT NULL,
    input_snapshot    JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 007_audit_results.sql
CREATE TABLE governance.audit_results (
    id            VARCHAR(65535) PRIMARY KEY,
    brand_id      VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    workspace_id  VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    brand_name    VARCHAR(65535) NOT NULL DEFAULT '',
    website_url   VARCHAR(65535) NOT NULL DEFAULT '',
    overall_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    robots_txt    JSONB,
    bot_access    JSONB,
    ssr           JSONB,
    ssrf          JSONB,
    issues        JSONB DEFAULT '[]'::jsonb,
    raw_result    JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 010_recommendation_results.sql + 014_evidence_label.sql
CREATE TABLE recommendation.results (
    id           VARCHAR(65535) PRIMARY KEY,
    brand_id     VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    panel_id     VARCHAR(65535),
    workspace_id VARCHAR(65535) NOT NULL,
    tenant_id    VARCHAR(65535) NOT NULL,
    category     VARCHAR(65535) NOT NULL,
    severity     VARCHAR(65535) NOT NULL,
    title        VARCHAR(65535) NOT NULL,
    detail       VARCHAR(65535) NOT NULL,
    action_url   VARCHAR(65535),
    confidence   NUMERIC(5,2) NOT NULL DEFAULT 75.00,
    applied      BOOLEAN NOT NULL DEFAULT false,
    dismissed    BOOLEAN NOT NULL DEFAULT false,
    evidence     VARCHAR(65535) DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    applied_at   TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

-- 037_sentiment_hallucination.sql
CREATE TABLE analysis.sentiment_scores (
    id                VARCHAR(65535) PRIMARY KEY,
    brand_id          VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    engine_name       VARCHAR(65535) NOT NULL,
    overall_sentiment REAL NOT NULL DEFAULT 0.5,
    positive_score    REAL NOT NULL DEFAULT 0.0,
    neutral_score     REAL NOT NULL DEFAULT 0.0,
    negative_score    REAL NOT NULL DEFAULT 0.0,
    mention_count     INT NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(65535) NOT NULL,
    workspace_id      VARCHAR(65535) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    analyzed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 038_conversation_replay.sql (037'nin FK referansı için önce tanımlanır)
CREATE TABLE replay.conversation_snapshots (
    id                 VARCHAR(65535) PRIMARY KEY,
    brand_id           VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    prompt_text        VARCHAR(65535) NOT NULL,
    engine_name        VARCHAR(65535) NOT NULL,
    response_preview   VARCHAR(65535) NOT NULL,
    response_full      VARCHAR(65535),
    content_hash       VARCHAR(65535) NOT NULL,
    s3_ref             VARCHAR(65535),
    measurement_job_id VARCHAR(65535) REFERENCES measure.measurement_jobs(id),
    raw_response_id    VARCHAR(65535),
    tenant_id          VARCHAR(65535) NOT NULL,
    workspace_id       VARCHAR(65535) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 037_sentiment_hallucination.sql
CREATE TABLE analysis.hallucination_flags (
    id                VARCHAR(65535) PRIMARY KEY,
    brand_id          VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    engine_name       VARCHAR(65535) NOT NULL,
    hallucination_type VARCHAR(65535) NOT NULL,
    severity          VARCHAR(65535) NOT NULL,
    description       VARCHAR(65535) NOT NULL,
    confidence        REAL NOT NULL DEFAULT 0.0,
    verified          BOOLEAN,
    replay_id         VARCHAR(65535) REFERENCES replay.conversation_snapshots(id),
    tenant_id         VARCHAR(65535) NOT NULL,
    workspace_id      VARCHAR(65535) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 038_conversation_replay.sql
CREATE TABLE archive.response_entries (
    id               VARCHAR(65535) PRIMARY KEY,
    brand_id         VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    engine_name      VARCHAR(65535) NOT NULL,
    prompt_text      VARCHAR(65535) NOT NULL DEFAULT '',
    response_preview VARCHAR(65535) NOT NULL,
    response_full    VARCHAR(65535) NOT NULL,
    version          INT NOT NULL DEFAULT 1,
    content_hash     VARCHAR(65535) NOT NULL,
    s3_ref           VARCHAR(65535),
    tenant_id        VARCHAR(65535) NOT NULL,
    workspace_id     VARCHAR(65535) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(brand_id, engine_name, version)
);
