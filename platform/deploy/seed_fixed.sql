BEGIN;
INSERT INTO identity.tenants (id, name, slug, tier) VALUES ('T01', 'Acme Corp', 'acme-corp', 'pro');
INSERT INTO config.workspaces (id, tenant_id, name, slug) VALUES ('WS01', 'T01', 'Ana Çalışma Alanı', 'ana-calisma');
INSERT INTO identity.users (id, tenant_id, email, password_hash, role, full_name) VALUES ('U01', 'T01', 'demo@acme.example.com', '$2a$06$ZnrycdBTB5QJgz7Q0KFmaeStV37cvzbf/2qr4vJsII9bwiZKXUGNS', 'admin', 'Demo Kullanıcı');
INSERT INTO config.memberships (id, workspace_id, user_id, tenant_id, role) VALUES ('M01', 'WS01', 'U01', 'T01', 'admin');
INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url) VALUES ('B01', 'WS01', 'T01', 'Acme', 'https://acme.example.com'), ('B02', 'WS01', 'T01', 'BetaCorp', 'https://beta.example.com'), ('B03', 'WS01', 'T01', 'GammaInc', 'https://gamma.example.com');
INSERT INTO config.prompt_sets (id, workspace_id, tenant_id, name, prompt_text, category) VALUES ('PS01', 'WS01', 'T01', 'Genel Görünürlük', '{brand_name} markası hakkında ne biliyorsun? Sektördeki konumu, yenilikleri ve rakiplerine göre farklılaştığı noktaları kaynak göstererek anlat.', 'genel'), ('PS02', 'WS01', 'T01', 'Teknoloji Algısı', '{brand_name} hangi teknolojik yeniliklerle tanınıyor? Sektöre katkıları ve gelecek vizyonu hakkında detaylı bilgi ver.', 'teknoloji');
INSERT INTO config.panels (id, workspace_id, tenant_id, name, description, prompt_set_id, schedule_cron, is_active) VALUES ('P01', 'WS01', 'T01', 'Haftalık Takip', 'Ana markaların haftalık görünürlük takibi', 'PS01', '0 8 * * 1', true), ('P02', 'WS01', 'T01', 'Aylık Rapor', 'Detaylı aylık görünürlük raporu', 'PS02', '0 9 1 * *', true);

INSERT INTO config.panel_brands (panel_id, brand_id, workspace_id, tenant_id) VALUES ('P01', 'B01', 'WS01', 'T01'), ('P01', 'B02', 'WS01', 'T01'), ('P01', 'B03', 'WS01', 'T01'), ('P02', 'B01', 'WS01', 'T01'), ('P02', 'B02', 'WS01', 'T01');
COMMIT;
