#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# GeoLens Demo — Tek komutla demo ortamı
# =============================================================================
# Kullanım:
#   1. export PERPLEXITY_API_KEY="your-key-here"   (opsiyonel, worker için)
#   2. ./deploy/demo.sh
#
# Bu script:
#   - Docker imajlarını build eder
#   - Tüm servisleri ayağa kaldırır
#   - Migration'ları otomatik çalıştırır
#   - Seed verisini yükler
#   - MinIO bucket'ını oluşturur
#   - Kullanım talimatlarını gösterir
# =============================================================================

echo "╔══════════════════════════════════════════════════╗"
echo "║         GeoLens Demo — Hazırlık Başlıyor        ║"
echo "╚══════════════════════════════════════════════════╝"

# 1. Build
echo ""
echo "▸ Docker imajları build ediliyor..."
docker compose -f deploy/docker-compose.demo.yml build

# 2. Start
echo ""
echo "▸ Servisler başlatılıyor..."
docker compose -f deploy/docker-compose.demo.yml up -d

# 3. Wait for postgres
echo ""
echo "▸ PostgreSQL hazır olana kadar bekleniyor..."
until docker compose -f deploy/docker-compose.demo.yml exec -T postgres pg_isready -U geolens >/dev/null 2>&1; do
  sleep 1
done
echo "  ✓ PostgreSQL hazır"

# 4. Run seed data
echo ""
echo "▸ Seed verisi yükleniyor..."
docker compose -f deploy/docker-compose.demo.yml exec -T postgres psql -U geolens < deploy/seed.sql
echo "  ✓ Seed verisi yüklendi"

# 5. Create MinIO bucket
echo ""
echo "▸ MinIO bucket oluşturuluyor..."
docker compose -f deploy/docker-compose.demo.yml exec -T minio \
  mc alias set local http://localhost:9000 minioadmin minioadmin
docker compose -f deploy/docker-compose.demo.yml exec -T minio \
  mc mb local/geolens-snapshots --ignore-existing
echo "  ✓ MinIO bucket hazır"

# 6. Show summary
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║         GeoLens Demo — Hazır                     ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║                                                  ║"
echo "║  Web UI:      http://localhost:3000              ║"
echo "║  API:         http://localhost:8080              ║"
echo "║  MinIO Konsol: http://localhost:9001             ║"
echo "║  PostgreSQL:  localhost:5432 (geolens/geolens)   ║
echo "║  Redis:       localhost:6379                     ║
echo "║                                                  ║
echo "║  Demo Giriş:                                     ║
echo "║    E-posta:  demo@acme.example.com               ║
echo "║    Şifre:    (kayıt olmanız gerekiyor)           ║
echo "║                                                  ║
echo "║  Kullanım:                                       ║
echo "║    1. http://localhost:3000 adresine git          ║
echo "║    2. Kayıt ol (demo@acme.example.com)            ║
echo "║    3. Panel oluştur veya varolanı kullan          ║
echo "║    4. Ölçüm başlat                                ║
echo "║    5. Skorları gör                                ║
echo "║                                                    ║
echo "╚══════════════════════════════════════════════════╝"
