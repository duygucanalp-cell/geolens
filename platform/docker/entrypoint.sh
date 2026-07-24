#!/bin/sh
# ==============================================================================
# GeoLens Docker Entrypoint
# ==============================================================================
# Bu script:
#   1. SOPS+Age ile şifrelenmiş .env.secrets.enc dosyasını arar
#   2. AGE_KEY ortam değişkeni (veya /etc/geolens/secrets/age-key.txt) varsa,
#      dosyayı çözer ve env var olarak export eder
#   3. Mevcut env var'ları ezmez (sadece boş olanları doldurur)
#   4. Ana uygulamayı (exec) başlatır
#
# Öncelik sırası (yüksekten düşüğe):
#   a) docker-compose.yml'de tanımlı env var (en yüksek öncelik)
#   b) .env.secrets.enc'den çözülen değerler
#   c) Go tarafındaki varsayılan değerler (config.LoadFromEnv)
# ==============================================================================
set -e

SECRETS_FILE="${SECRETS_FILE:-/etc/geolens/secrets/.env.secrets.enc}"

# --- Age private key'i al ---
if [ -z "${AGE_KEY:-}" ]; then
    if [ -f /etc/geolens/secrets/age-key.txt ]; then
        AGE_KEY=$(cat /etc/geolens/secrets/age-key.txt | grep -v '^#' | tr -d '\n\r')
    fi
fi

# --- Secrets dosyası varsa ve AGE_KEY tanımlıysa, çöz ---
if [ -f "$SECRETS_FILE" ]; then
    if [ -n "${AGE_KEY:-}" ]; then
        echo "[entrypoint] .env.secrets.enc bulundu, çözülüyor..."
        export SOPS_AGE_KEY="$AGE_KEY"

        DECRYPTED=$(sops --decrypt "$SECRETS_FILE" 2>/dev/null) || {
            echo "[entrypoint] UYARI: Secrets çözülemedi! Hata: $?"
            DECRYPTED=""
        }

        if [ -n "$DECRYPTED" ]; then
            # .env formatını satır satır işle
            # Desteklenen formatlar:
            #   KEY=value
            #   KEY="value with spaces"
            #   KEY='value with spaces'
            #   KEY=value=with=equals (ikinci ='den sonrası value'ya dahil)
            echo "$DECRYPTED" | while IFS='=' read -r key rest; do
                # Boş satır ve yorumları atla
                case "$key" in
                    ''|\#*) continue ;;
                esac

                # rest'ten tırnak işaretlerini temizle
                value="$rest"
                case "$value" in
                    \"*\") value=$(printf '%s' "$value" | sed 's/^"//;s/"$//') ;;
                    \'*\') value=$(printf '%s' "$value" | sed -e "s/^'//" -e "s/'$//") ;;
                esac

                # Sadece mevcut env var'da değer yoksa veya boşsa set et
                current=$(eval "printf '%s' \"\${${key}-}\"")
                if [ -z "$current" ] && [ -n "$value" ]; then
                    export "${key}=${value}"
                fi
            done
            echo "[entrypoint] Secrets başarıyla yüklendi"
        fi
    else
        echo "[entrypoint] UYARI: .env.secrets.enc bulundu ama AGE_KEY tanımlı değil"
        echo "[entrypoint] Secrets çözülemedi, docker-compose default'ları kullanılacak"
    fi
fi

# Ana uygulamayı çalıştır
exec "$@"
