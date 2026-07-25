#!/usr/bin/env bash
# ==============================================================================
# GeoLens API Benchmark Script
# ==============================================================================
# Kullanım:
#   ./deploy/benchmark.sh                          # Tüm benchmark'ları çalıştır
#   ./deploy/benchmark.sh login                    # Sadece login benchmark
#   ./deploy/benchmark.sh brands                   # Sadece brands benchmark
#   ./deploy/benchmark.sh scores                   # Sadece scores benchmark
# ==============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-demo@acme.example.com}"
PASSWORD="${PASSWORD:-demo1234}"
WORKSPACE_ID="${WORKSPACE_ID:-WS01}"
DURATION="${DURATION:-10s}"
RATE="${RATE:-50}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}╔════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║    GeoLens Performance Baseline            ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════╝${NC}"
echo ""
echo "Base URL:    $BASE_URL"
echo "Duration:    $DURATION"
echo "Rate:        $RATE/sec"
echo "Workspace:   $WORKSPACE_ID"
echo ""

# === Login ve token al ===
echo -e "${YELLOW}▸ Giriş yapılıyor...${NC}"
TOKEN=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo -e "${RED}✗ Giriş başarısız!${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Token alındı: ${TOKEN:0:20}...${NC}"
echo ""

# === Benchmark fonksiyonu ===
run_benchmark() {
  local name="$1"
  local url="$2"
  local method="${3:-GET}"
  
  echo -e "${YELLOW}▸ Benchmark: $name${NC}"
  echo "  URL: $method $url"
  
  hey -z "$DURATION" -q "$RATE" \
    -m "$method" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    "$url" 2>&1 | tail -20
  
  echo ""
}

run_benchmark_body() {
  local name="$1"
  local url="$2"
  local body="$3"
  
  echo -e "${YELLOW}▸ Benchmark: $name${NC}"
  echo "  URL: POST $url"
  
  hey -z "$DURATION" -q "$RATE" \
    -m "POST" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$body" \
    "$url" 2>&1 | tail -20
  
  echo ""
}

# === Run benchmarks ===
run_specific="${1:-all}"

if [ "$run_specific" = "all" ] || [ "$run_specific" = "brands" ]; then
  run_benchmark "Brands (GET)" "$BASE_URL/v1/workspaces/$WORKSPACE_ID/brands"
fi

if [ "$run_specific" = "all" ] || [ "$run_specific" = "panels" ]; then
  run_benchmark "Panels (GET)" "$BASE_URL/v1/workspaces/$WORKSPACE_ID/panels"
fi

if [ "$run_specific" = "all" ] || [ "$run_specific" = "scores" ]; then
  run_benchmark "Scores (GET)" "$BASE_URL/v1/workspaces/$WORKSPACE_ID/scores"
fi

if [ "$run_specific" = "all" ] || [ "$run_specific" = "recommendations" ]; then
  run_benchmark "Recommendations (GET)" "$BASE_URL/v1/workspaces/$WORKSPACE_ID/recommendations"
fi

if [ "$run_specific" = "all" ] || [ "$run_specific" = "measurement" ]; then
  run_benchmark_body "Measurement (POST)" "$BASE_URL/v1/workspaces/$WORKSPACE_ID/measurements" \
    '{"brand_id":"B01","brand_name":"Acme","engine_name":"perplexity","prompt_text":"{brand_name} hakkında ne biliyorsun?"}'
fi

if [ "$run_specific" = "all" ] || [ "$run_specific" = "login" ]; then
  echo -e "${YELLOW}▸ Benchmark: Login (POST)${NC}"
  hey -z "$DURATION" -q "$RATE" \
    -m "POST" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
    "$BASE_URL/v1/auth/login" 2>&1 | tail -20
  echo ""
fi

# === Summary ===
echo -e "${CYAN}════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ Benchmark tamamlandı${NC}"
echo ""
echo "Sonuçları değerlendir:"
echo "  - API <1s (p50):  $(grep -c '200' /dev/null 2>/dev/null || echo 'hey çıktısını kontrol et')"
echo "  - Pano <5s (p50): Yukarıdaki GET benchmark'larını kontrol et"
echo "  - Ölçüm <60s:    Worker log'larını kontrol et"
echo ""
echo "Grafana'da metrikler: http://localhost:9090 (Prometheus)"
echo ""
