-- 004_prompt_sets.sql
-- Prompt Set şablonları — tekrar kullanılabilir prompt metinleri
-- NOT: config.prompt_sets tablosu 001_initial.sql'de oluşturulmuştur.
-- Bu migration sadece 001'de eksik olan kolonları ve indexleri ekler.

-- Yeni kolonlar (001'de yok)
ALTER TABLE config.prompt_sets ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE config.prompt_sets ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;

-- Indexes (001'de yok)
CREATE INDEX IF NOT EXISTS idx_prompt_sets_tenant ON config.prompt_sets(tenant_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_prompt_sets_active ON config.prompt_sets(tenant_id, is_active) WHERE is_active = true;

-- RLS: 001'de zaten etkin, tekrar gerekmez
-- Audit trigger: 001'de zaten var, tekrar gerekmez
