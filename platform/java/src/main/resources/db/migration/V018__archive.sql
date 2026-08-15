-- 018_archive.sql
-- H4: Müşteri arşivleme ve devretme

ALTER TABLE config.workspaces ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
ALTER TABLE config.brands ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
