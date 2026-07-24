#!/bin/sh
# ==============================================================================
# GeoLens — Secrets Decrypt Utility
# ==============================================================================
# Kullanım:
#   eval $(docker/entrypoint.decrypt-only.sh)
#   docker run --env-file <(docker/entrypoint.decrypt-only.sh) ...
#
# Bu script:
#   - SOPS_AGE_KEY env var'ından veya AGE_KEY'den age private key alır
#   - .env.secrets.enc dosyasını çözer
#   - stdout'a "export KEY=VALUE" satırları basar
# ==============================================================================
set -e

SECRETS_FILE="${SECRETS_FILE:-./.env.secrets.enc}"
AGE_KEY="${AGE_KEY:-${SOPS_AGE_KEY:-}}"

# AGE_KEY yoksa, age-key.txt'den oku
if [ -z "$AGE_KEY" ] && [ -f ./docker/age-key.txt ]; then
    AGE_KEY=$(cat ./docker/age-key.txt | grep -v '^#' | tr -d '\n\r')
fi

if [ ! -f "$SECRETS_FILE" ]; then
    echo "# Secret file not found: $SECRETS_FILE" >&2
    exit 1
fi

if [ -z "$AGE_KEY" ]; then
    echo "# AGE_KEY not set" >&2
    exit 1
fi

export SOPS_AGE_KEY="$AGE_KEY"
DECRYPTED=$(sops --decrypt "$SECRETS_FILE" 2>/dev/null) || {
    echo "# Decryption failed" >&2
    exit 1
}

echo "# Decrypted secrets from $SECRETS_FILE"
echo "$DECRYPTED" | while IFS='=' read -r key value; do
    case "$key" in
        ''|\#*) continue ;;
    esac
    # Tırnak temizleme (çift tırnak, tek tırnak)
    case "$value" in
        \"*\") value=$(printf '%s' "$value" | sed 's/^"//;s/"$//') ;;
        \'*\') value=$(printf '%s' "$value" | sed -e "s/^'//" -e "s/'$//") ;;
    esac
    echo "export ${key}=${value}"
done
