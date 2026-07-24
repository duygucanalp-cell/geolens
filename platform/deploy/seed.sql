-- seed.sql — GeoLens Demo seed verisi
-- Bu script migration'lar çalıştıktan sonra bir kere çalıştırılır:
--   docker compose -f deploy/docker-compose.demo.yml exec -T postgres psql -U geolens < deploy/seed.sql

BEGIN;

-- ============================================================================
-- 1. Kiracı (Tenant)
-- ============================================================================
INSERT INTO identity.tenants (id, name, slug, tier)
VALUES ('T01', 'Acme Corp', 'acme-corp', 'pro')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. Çalışma Alanı (Workspace)
-- ============================================================================
INSERT INTO config.workspaces (id, tenant_id, name, slug)
VALUES ('WS01', 'T01', 'Ana Çalışma Alanı', 'ana-calisma')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. Kullanıcı
-- ============================================================================
INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name)
VALUES ('U01', 'T01', 'demo@acme.example.com',
        '$2a$06$ZnrycdBTB5QJgz7Q0KFmaeStV37cvzbf/2qr4vJsII9bwiZKXUGNS',  -- BCrypt hash (şifre: demo1234)
        'admin', 'Demo Kullanıcı')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 4. Üyelik (Membership) — Bu olmazsa workspace_access_denied hatası alınır
-- ============================================================================
INSERT INTO config.memberships (id, workspace_id, user_id, tenant_id, role)
VALUES ('M01', 'WS01', 'U01', 'T01', 'admin')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 5. Markalar
-- ============================================================================
INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url)
VALUES
    ('B01', 'WS01', 'T01', 'Acme',     'https://acme.example.com'),
    ('B02', 'WS01', 'T01', 'BetaCorp', 'https://beta.example.com'),
    ('B03', 'WS01', 'T01', 'GammaInc', 'https://gamma.example.com')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 6. Prompt Setleri
-- ============================================================================
INSERT INTO config.prompt_sets (id, workspace_id, tenant_id, name, prompt_text, category)
VALUES
    ('PS01', 'WS01', 'T01', 'Genel Görünürlük',
     '{brand_name} markası hakkında ne biliyorsun? Sektördeki konumu, yenilikleri ve rakiplerine göre farklılaştığı noktaları kaynak göstererek anlat.',
     'genel'),
    ('PS02', 'WS01', 'T01', 'Teknoloji Algısı',
     '{brand_name} hangi teknolojik yeniliklerle tanınıyor? Sektöre katkıları ve gelecek vizyonu hakkında detaylı bilgi ver.',
     'teknoloji')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 7. Paneller
-- ============================================================================
INSERT INTO config.panels (id, workspace_id, tenant_id, name, description, prompt_set_id, schedule_cron, is_active)
VALUES
    ('P01', 'WS01', 'T01', 'Haftalık Takip',
     'Ana markaların haftalık görünürlük takibi',
     'PS01', '0 8 * * 1', true),
    ('P02', 'WS01', 'T01', 'Aylık Rapor',
     'Detaylı aylık görünürlük raporu',
     'PS02', '0 9 1 * *', true)
ON CONFLICT (id) DO NOTHING;

-- Panel-Marka ilişkileri
INSERT INTO config.panel_brands (panel_id, brand_id, workspace_id, tenant_id)
VALUES
    ('P01', 'B01', 'WS01', 'T01'),
    ('P01', 'B02', 'WS01', 'T01'),
    ('P01', 'B03', 'WS01', 'T01'),
    ('P02', 'B01', 'WS01', 'T01'),
    ('P02', 'B02', 'WS01', 'T01')
ON CONFLICT (panel_id, brand_id) DO NOTHING;

COMMIT;

-- Seed tamamlandı mesajı
\t on
SELECT 'Seed verisi başarıyla yüklendi.' AS mesaj;
