#!/usr/bin/env bash
# In-app bildirim kanalı E2E doğrulaması (052 migration + API endpoint)
set -e
cd "$(dirname "$0")/.."

# Port 8900 cakismasini onle: platform ml-serving'i durdur (deploy stack kullanir)
docker compose stop ml-serving >/dev/null 2>&1 || true

docker compose -f deploy/docker-compose.demo.yml up -d ml-serving api worker 2>&1 | tail -2
sleep 12

echo "--- api health ---"
curl -s http://localhost:8080/health
echo

echo "--- in-app bildirim ekle (DB direkt) ---"
docker compose -f deploy/docker-compose.demo.yml exec -T postgres psql -U geolens -c "INSERT INTO delivery.notifications (id, tenant_id, workspace_id, user_id, type, title, body, data) VALUES ('n_test1', 'T01', 'WS01', 'U01', 'score_drop', 'Test Bildirimi', 'Acme skoru dustu', '{\"brand\":\"Acme\"}'::jsonb)" 2>&1 | tail -1

echo "--- DB'de kayit ---"
docker compose -f deploy/docker-compose.demo.yml exec -T postgres psql -U geolens -c "SELECT id, title, is_read FROM delivery.notifications ORDER BY created_at DESC LIMIT 2" 2>&1 | tail -5
