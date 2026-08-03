-- 037_sentiment_hallucination.sql
-- Bu migration, 0416 Sentiment/Hallucination dokümanı için analysis schema'sını oluşturur.
-- Eklenen FR: FR-D7 (sentiment), FR-D8 (hallüsinasyon)

CREATE SCHEMA IF NOT EXISTS analysis;

-- Sentiment skorları
CREATE TABLE analysis.sentiment_scores (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    engine_name       TEXT NOT NULL,
    overall_sentiment REAL NOT NULL DEFAULT 0.5,  -- 0.0 (negatif) .. 1.0 (pozitif)
    positive_score    REAL NOT NULL DEFAULT 0.0,
    neutral_score     REAL NOT NULL DEFAULT 0.0,
    negative_score    REAL NOT NULL DEFAULT 0.0,
    mention_count     INT NOT NULL DEFAULT 0,
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    analyzed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE analysis.sentiment_scores ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analysis.sentiment_scores
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_sentiment_brand ON analysis.sentiment_scores(brand_id, analyzed_at DESC);
CREATE INDEX idx_sentiment_tenant ON analysis.sentiment_scores(tenant_id);

-- Hallüsinasyon işaretleri
CREATE TABLE analysis.hallucination_flags (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    engine_name       TEXT NOT NULL,
    hallucination_type TEXT NOT NULL,    -- T1 (yanlış bilgi) / T2 (uydurma kaynak) / T3 (sayısal) / T4 (olumsuz) / T5 (bağlam)
    severity          TEXT NOT NULL,     -- critical / high / medium / low
    description       TEXT NOT NULL,
    confidence        REAL NOT NULL DEFAULT 0.0,  -- 0.0 .. 1.0
    verified          BOOLEAN,           -- NULL: bekliyor, true: doğrulandı, false: yanlış pozitif
    replay_id         TEXT REFERENCES replay.conversation_snapshots(id),  -- opsiyonel bağlantı
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE analysis.hallucination_flags ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analysis.hallucination_flags
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_hallucination_brand ON analysis.hallucination_flags(brand_id, created_at DESC);
CREATE INDEX idx_hallucination_severity ON analysis.hallucination_flags(severity);
CREATE INDEX idx_hallucination_tenant ON analysis.hallucination_flags(tenant_id);
