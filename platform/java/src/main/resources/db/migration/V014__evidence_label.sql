-- 014_evidence_label.sql
-- M10: Kanıt etiketi kolonu recommendation.results tablosuna eklenir

ALTER TABLE recommendation.results
    ADD COLUMN IF NOT EXISTS evidence TEXT DEFAULT '';
