-- 038_conversation_replay.sql
-- Bu migration, 0312 Conversation Replay & Response Archive dokümanı için replay/archive schema'larını oluşturur.
-- Eklenen FR: FR-D12 (conversation replay), FR-D13 (response archive)

CREATE SCHEMA IF NOT EXISTS replay;
CREATE SCHEMA IF NOT EXISTS archive;

-- Conversation snapshots (FR-D12)
CREATE TABLE replay.conversation_snapshots (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    prompt_text       TEXT NOT NULL,
    engine_name       TEXT NOT NULL,
    response_preview  TEXT NOT NULL,     -- İlk 500 karakter
    response_full     TEXT,              -- Tam yanıt (opsiyonel, büyük metinler için)
    content_hash      TEXT NOT NULL,     -- SHA-256 bütünlük kontrolü
    s3_ref            TEXT,              -- S3'te tam metin referansı (varsa)
    measurement_job_id TEXT REFERENCES measure.measurement_jobs(id),  -- bağlantı
    raw_response_id   TEXT,              -- raw_responses.id bağlantısı
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE replay.conversation_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON replay.conversation_snapshots
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_replay_brand ON replay.conversation_snapshots(brand_id, created_at DESC);
CREATE INDEX idx_replay_tenant ON replay.conversation_snapshots(tenant_id);
CREATE INDEX idx_replay_hash ON replay.conversation_snapshots(content_hash);

-- Response archive entries (FR-D13)
CREATE TABLE archive.response_entries (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    engine_name       TEXT NOT NULL,
    prompt_text       TEXT NOT NULL DEFAULT '',
    response_preview  TEXT NOT NULL,     -- İlk 1000 karakter
    response_full     TEXT NOT NULL,     -- Tam yanıt
    version           INT NOT NULL DEFAULT 1,
    content_hash      TEXT NOT NULL,     -- SHA-256 bütünlük kontrolü
    s3_ref            TEXT,              -- S3'te tam referans (varsa)
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(brand_id, engine_name, version)
);

ALTER TABLE archive.response_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON archive.response_entries
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_archive_brand ON archive.response_entries(brand_id, engine_name, created_at DESC);
CREATE INDEX idx_archive_tenant ON archive.response_entries(tenant_id);
CREATE INDEX idx_archive_version ON archive.response_entries(brand_id, engine_name, version DESC);
