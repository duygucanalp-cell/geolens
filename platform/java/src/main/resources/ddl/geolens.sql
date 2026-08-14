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

-- ============================================================================
-- Kontrolör/service katmanı tabloları (ADR-014 v3.0: tip güvenli DSL dönüşümü)
-- ============================================================================
-- Not: measure.citations ve identity.user_tenants kasıtlı olarak YOKTUR —
-- spike sorguları bu tablolar için production migration'larıyla uyuşmayan
-- kolonlar/tablo kullandığından ilgili sorgular plain SQL olarak kalır.

CREATE SCHEMA IF NOT EXISTS privacy;
CREATE SCHEMA IF NOT EXISTS usage;
CREATE SCHEMA IF NOT EXISTS version;
CREATE SCHEMA IF NOT EXISTS delivery;

-- 001_initial.sql
CREATE TABLE identity.users (
    id            VARCHAR(65535) PRIMARY KEY,
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    email         VARCHAR(65535) NOT NULL,
    password_hash VARCHAR(65535) NOT NULL,
    role          VARCHAR(65535) NOT NULL DEFAULT 'member',
    full_name     VARCHAR(65535) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, email)
);

-- 001_initial.sql
CREATE TABLE config.memberships (
    id           VARCHAR(65535) PRIMARY KEY,
    workspace_id VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    user_id      VARCHAR(65535) NOT NULL REFERENCES identity.users(id),
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    role         VARCHAR(65535) NOT NULL DEFAULT 'member',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(workspace_id, user_id)
);

-- 001_initial.sql + 004_prompt_sets.sql
CREATE TABLE config.prompt_sets (
    id           VARCHAR(65535) PRIMARY KEY,
    workspace_id VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    name         VARCHAR(65535) NOT NULL,
    prompt_text  VARCHAR(65535) NOT NULL,
    category     VARCHAR(65535),
    description  VARCHAR(65535),
    version      INT NOT NULL DEFAULT 1,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 005_panels.sql
CREATE TABLE config.panels (
    id               VARCHAR(65535) PRIMARY KEY,
    workspace_id     VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    tenant_id        VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    name             VARCHAR(65535) NOT NULL,
    description      VARCHAR(65535),
    prompt_set_id    VARCHAR(65535) REFERENCES config.prompt_sets(id),
    schedule_cron    VARCHAR(65535),
    is_active        BOOLEAN NOT NULL DEFAULT true,
    last_measured_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 005_panels.sql
CREATE TABLE config.panel_brands (
    panel_id     VARCHAR(65535) NOT NULL REFERENCES config.panels(id),
    brand_id     VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    workspace_id VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (panel_id, brand_id)
);

-- 042_brand_competitors.sql
CREATE TABLE config.brand_competitors (
    id            VARCHAR(65535) PRIMARY KEY,
    brand_id      VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    competitor_id VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(brand_id, competitor_id)
);

-- 012_invitations.sql
CREATE TABLE identity.invitations (
    id          VARCHAR(65535) PRIMARY KEY,
    tenant_id   VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    workspace_id VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    invited_by  VARCHAR(65535) NOT NULL REFERENCES identity.users(id),
    email       VARCHAR(65535) NOT NULL,
    role        VARCHAR(65535) NOT NULL DEFAULT 'member',
    token       VARCHAR(65535) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 017_api_keys.sql (allowed_ips TEXT[] kolonu Java spike'ında kullanılmadığından alınmadı)
CREATE TABLE identity.api_keys (
    id           VARCHAR(65535) PRIMARY KEY,
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    name         VARCHAR(65535) NOT NULL,
    key_hash     VARCHAR(65535) NOT NULL,
    key_prefix   VARCHAR(65535) NOT NULL,
    role         VARCHAR(65535) NOT NULL DEFAULT 'viewer',
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 016_reports.sql
CREATE TABLE measure.reports (
    id            VARCHAR(65535) PRIMARY KEY,
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    workspace_id  VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    report_type   VARCHAR(65535) NOT NULL,
    brand_id      VARCHAR(65535) REFERENCES config.brands(id),
    status        VARCHAR(65535) NOT NULL DEFAULT 'pending',
    file_path     VARCHAR(65535),
    file_name     VARCHAR(65535),
    file_size     BIGINT,
    error_message VARCHAR(65535),
    params        JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 006_governance.sql
CREATE TABLE governance.audit_log (
    id            VARCHAR(65535) PRIMARY KEY,
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    user_id       VARCHAR(65535),
    event_type    VARCHAR(65535) NOT NULL,
    resource_type VARCHAR(65535) NOT NULL,
    resource_id   VARCHAR(65535),
    action        VARCHAR(65535) NOT NULL,
    metadata      JSONB,
    ip_address    VARCHAR(65535),
    user_agent    VARCHAR(65535),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 015_alert_rules.sql
CREATE TABLE governance.alert_rules (
    id             VARCHAR(65535) PRIMARY KEY,
    tenant_id      VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    brand_id       VARCHAR(65535) NOT NULL REFERENCES config.brands(id),
    name           VARCHAR(65535) NOT NULL,
    metric         VARCHAR(65535) NOT NULL,
    condition      VARCHAR(65535) NOT NULL,
    threshold      DOUBLE PRECISION NOT NULL,
    channel        VARCHAR(65535) NOT NULL DEFAULT 'email',
    channel_config JSONB,
    enabled        BOOLEAN NOT NULL DEFAULT true,
    cooldown_min   INT NOT NULL DEFAULT 60,
    last_fired_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 006_governance.sql
CREATE TABLE governance.rate_limit_buckets (
    id           VARCHAR(65535) PRIMARY KEY,
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    bucket_name  VARCHAR(65535) NOT NULL,
    max_tokens   BIGINT NOT NULL,
    tokens_used  BIGINT NOT NULL DEFAULT 0,
    window_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, bucket_name, window_start)
);

-- 006_governance.sql
CREATE TABLE governance.usage_records (
    id            VARCHAR(65535) PRIMARY KEY,
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    metric_name   VARCHAR(65535) NOT NULL,
    metric_value  BIGINT NOT NULL DEFAULT 1,
    resource_type VARCHAR(65535),
    resource_id   VARCHAR(65535),
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 009_kvkk_delete.sql
CREATE TABLE privacy.deletion_requests (
    id           VARCHAR(65535) PRIMARY KEY,
    tenant_id    VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    requested_by VARCHAR(65535) NOT NULL REFERENCES identity.users(id),
    status       VARCHAR(65535) NOT NULL DEFAULT 'pending',
    reason       VARCHAR(65535),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    processed_by VARCHAR(65535),
    notes        VARCHAR(65535),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 033_usage_analytics.sql (id DEFAULT gen_ulid() alınmadı — codegen için gerekli değil)
CREATE TABLE usage.metrics (
    id            VARCHAR(65535) PRIMARY KEY,
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    endpoint      VARCHAR(65535) NOT NULL DEFAULT '',
    method        VARCHAR(65535) NOT NULL DEFAULT 'GET',
    status_code   INTEGER NOT NULL DEFAULT 200,
    latency_ms    INTEGER NOT NULL DEFAULT 0,
    user_id       VARCHAR(65535) NOT NULL DEFAULT '',
    ip_address    VARCHAR(65535) NOT NULL DEFAULT '',
    user_agent    VARCHAR(65535) NOT NULL DEFAULT '',
    request_size  INTEGER NOT NULL DEFAULT 0,
    response_size INTEGER NOT NULL DEFAULT 0,
    metadata      JSONB NOT NULL DEFAULT '{}',
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 035_version_tracking.sql (id DEFAULT gen_ulid() alınmadı — codegen için gerekli değil)
CREATE TABLE version.entries (
    id            VARCHAR(65535) PRIMARY KEY,
    tenant_id     VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    entity_type   VARCHAR(65535) NOT NULL DEFAULT '',
    entity_id     VARCHAR(65535) NOT NULL DEFAULT '',
    entity_name   VARCHAR(65535) NOT NULL DEFAULT '',
    old_version   VARCHAR(65535) NOT NULL DEFAULT '',
    new_version   VARCHAR(65535) NOT NULL DEFAULT '',
    change_notes  VARCHAR(65535) NOT NULL DEFAULT '',
    changed_by    VARCHAR(65535) NOT NULL DEFAULT '',
    metadata      JSONB NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 052_in_app_notifications.sql
CREATE TABLE delivery.notifications (
    id           VARCHAR(65535) PRIMARY KEY,
    tenant_id    VARCHAR(65535) NOT NULL,
    workspace_id VARCHAR(65535) NOT NULL,
    user_id      VARCHAR(65535) NOT NULL DEFAULT '',
    type         VARCHAR(65535) NOT NULL,
    title        VARCHAR(65535) NOT NULL,
    body         VARCHAR(65535) NOT NULL,
    data         JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_read      BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 008_notification_settings.sql + 050_notification_webhook.sql
CREATE TABLE delivery.notification_settings (
    workspace_id     VARCHAR(65535) NOT NULL REFERENCES config.workspaces(id),
    tenant_id        VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    email_address    VARCHAR(65535) NOT NULL DEFAULT '',
    digest_enabled   BOOLEAN NOT NULL DEFAULT true,
    digest_day       VARCHAR(65535) NOT NULL DEFAULT 'monday',
    digest_time      VARCHAR(65535) NOT NULL DEFAULT '09:00',
    digest_format    VARCHAR(65535) NOT NULL DEFAULT 'email',
    notify_on_drop   BOOLEAN NOT NULL DEFAULT true,
    drop_threshold   INTEGER NOT NULL DEFAULT 10,
    webhook_url      VARCHAR(65535) NOT NULL DEFAULT '',
    webhook_kind     VARCHAR(65535) NOT NULL DEFAULT 'generic',
    webhook_active   BOOLEAN NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, tenant_id)
);

-- 003_outbox.sql
CREATE SCHEMA IF NOT EXISTS public;
CREATE TABLE public.event_outbox (
    id              VARCHAR(65535) PRIMARY KEY,
    event_type      VARCHAR(65535) NOT NULL,
    stream          VARCHAR(65535) NOT NULL DEFAULT 'q:measure',
    payload         JSONB NOT NULL,
    tenant_id       VARCHAR(65535) NOT NULL REFERENCES identity.tenants(id),
    idempotency_key VARCHAR(65535),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at   TIMESTAMPTZ
);
