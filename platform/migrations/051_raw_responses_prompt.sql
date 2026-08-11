-- 051_raw_responses_prompt.sql
-- raw_responses şema tamamlama + prompt sütunu:
--
-- 1) brand_id / workspace_id eksikliği (002'den beri): DetectHallucinations
--    (JOIN config.brands ON rr.brand_id), worker computeAndEvaluate
--    (WHERE workspace_id/brand_id), AnalyzeSentiment ve replay sorguları
--    bu sütunları kullanıyordu ama migration'larda hiç tanımlanmamıştı —
--    taze DB'lerde "column does not exist" hatası üretiyordu.
-- 2) prompt_text: cross-source hallüsinasyon karşılaştırmasının aynı prompt
--    yanıtlarıyla sınırlandırılması (yanlış pozitif riskini azaltır).
--    DetectHallucinations sorgusu rr.prompt_text'e göre gruplar
--    (bkz. internal/sentiment/engine.go groupByPrompt).

ALTER TABLE measure.raw_responses
    ADD COLUMN IF NOT EXISTS brand_id TEXT REFERENCES config.brands(id),
    ADD COLUMN IF NOT EXISTS workspace_id TEXT REFERENCES config.workspaces(id),
    ADD COLUMN IF NOT EXISTS prompt_text TEXT NOT NULL DEFAULT '';

-- Mevcut satırları measurement_jobs üzerinden geriye dönük doldur
-- (her raw_response bir measurement_job'a bağlıdır — job_id üzerinden hizalanır).
UPDATE measure.raw_responses rr
SET brand_id     = mj.brand_id,
    workspace_id = mj.workspace_id,
    prompt_text  = CASE WHEN rr.prompt_text = '' THEN mj.prompt_text ELSE rr.prompt_text END
FROM measure.measurement_jobs mj
WHERE mj.id = rr.job_id
  AND (rr.brand_id IS NULL OR rr.workspace_id IS NULL OR rr.prompt_text = '');

-- Tenant + marka + prompt bazlı gruplama indexi: DetectHallucinations sorgusu
-- (tenant_id, brand_id) filtresiyle çalışır ve prompt sütunu ayırt edicidir.
CREATE INDEX IF NOT EXISTS idx_rr_prompt
    ON measure.raw_responses (tenant_id, brand_id, prompt_text);
