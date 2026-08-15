-- 009_kvkk_delete.sql
-- KVKK/GDPR Veri Silme ve Anonimleştirme
-- ============================================================================
-- Bu migration şunları sağlar:
--   1. Veri silme talebi tablosu (audit amaçlı)
--   2. Kiracı verisini anonimleştirme fonksiyonu
--   3. RLS politikaları

CREATE SCHEMA IF NOT EXISTS privacy;

-- ============================================================================
-- KVKK Veri Silme Talepleri
-- ============================================================================
CREATE TABLE privacy.deletion_requests (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    requested_by    TEXT NOT NULL REFERENCES identity.users(id),
    status          TEXT NOT NULL DEFAULT 'pending', -- pending, processing, completed, rejected
    reason          TEXT,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    processed_by    TEXT,                              -- admin user id who processed it
    notes           TEXT,                              -- processing notes
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Kiracı Veri Anonimleştirme Fonksiyonu
-- ============================================================================
-- Bu fonksiyon bir kiracıya ait tüm kişisel verileri anonimleştirir.
-- İş verileri (brands, measurement_jobs, scores) korunur ancak
-- kişisel veriler (email, name, password_hash) anonimleştirilir.
-- S3'teki raw response'lar silinmez ancak tenant artık erişemez (RLS).
CREATE OR REPLACE FUNCTION privacy.anonymize_tenant(p_tenant_id TEXT)
RETURNS void AS $$
DECLARE
    v_count_users INT;
    v_count_brands INT;
BEGIN
    -- Kullanıcıları anonimleştir
    UPDATE identity.users
    SET 
        email = 'deleted-' || substr(id, 1, 8) || '@anonymized.geolens',
        password_hash = 'ANONYMIZED',
        full_name = 'Silinmiş Kullanıcı',
        is_active = false,
        updated_at = now()
    WHERE tenant_id = p_tenant_id;
    
    GET DIAGNOSTICS v_count_users = ROW_COUNT;

    -- Sessionları temizle
    DELETE FROM identity.sessions
    WHERE tenant_id = p_tenant_id;

    -- Tenant'ı devre dışı bırak (veri korunur ama erişilemez)
    UPDATE identity.tenants
    SET 
        name = 'Silinmiş Hesap',
        slug = 'deleted-' || substr(p_tenant_id, 1, 8),
        tier = 'deleted',
        updated_at = now()
    WHERE id = p_tenant_id;

    -- Audit log
    INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata)
    VALUES (
        gen_random_uuid()::text,
        p_tenant_id,
        NULL,
        'privacy.deletion',
        'tenant',
        p_tenant_id,
        'anonymize',
        jsonb_build_object(
            'anonymized_users', v_count_users,
            'function', 'privacy.anonymize_tenant',
            'timestamp', now()
        )
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- RLS Politikaları
-- ============================================================================
ALTER TABLE privacy.deletion_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON privacy.deletion_requests
    USING (tenant_id = identity.get_tenant_id());

-- ============================================================================
-- Indexes
-- ============================================================================
CREATE INDEX idx_deletion_requests_tenant ON privacy.deletion_requests(tenant_id);
CREATE INDEX idx_deletion_requests_status ON privacy.deletion_requests(status);
