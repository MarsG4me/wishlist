#!/bin/sh
# Renders the two things that cannot be baked into the image — the SPA's runtime config
# and nginx's trusted-proxy list — then hands over to supervisord (nginx + backend).
set -eu

fail() { echo "entrypoint: $*" >&2; exit 1; }

: "${KEYCLOAK_URL:?KEYCLOAK_URL is required (public base URL of the external Keycloak)}"
: "${KEYCLOAK_REALM:?KEYCLOAK_REALM is required}"
: "${KEYCLOAK_CLIENT_ID:=wishlist-frontend}"
: "${PUBLIC_API_URL:=}"   # empty = same origin, which is how nginx serves it here
: "${ADS_ENABLED:=false}"
: "${TRUSTED_PROXY_CIDR:=}"

case "$ADS_ENABLED" in
  true|false) ;;
  *) fail "ADS_ENABLED must be true or false, got '$ADS_ENABLED'" ;;
esac

mkdir -p /tmp/nginx/client_body /tmp/nginx/proxy /tmp/nginx/fastcgi /tmp/nginx/uwsgi /tmp/nginx/scgi

# quotes and backslashes would break out of the JS string literals below
js_string() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

cat > /srv/www/config.js <<EOF
window.__WISHLIST_CONFIG__ = {
  apiUrl: "$(js_string "$PUBLIC_API_URL")",
  keycloakUrl: "$(js_string "$KEYCLOAK_URL")",
  keycloakRealm: "$(js_string "$KEYCLOAK_REALM")",
  keycloakClientId: "$(js_string "$KEYCLOAK_CLIENT_ID")",
  adsEnabled: $ADS_ENABLED
};
EOF

# TRUSTED_PROXY_CIDR: whitespace- or comma-separated list of the outer nginx addresses.
: > /tmp/nginx/real_ip.conf
if [ -n "$TRUSTED_PROXY_CIDR" ]; then
  for cidr in $(echo "$TRUSTED_PROXY_CIDR" | tr ',' ' '); do
    echo "set_real_ip_from $cidr;" >> /tmp/nginx/real_ip.conf
  done
  {
    echo 'real_ip_header X-Forwarded-For;'
    echo 'real_ip_recursive on;'
  } >> /tmp/nginx/real_ip.conf
else
  echo "entrypoint: WARNING — TRUSTED_PROXY_CIDR is unset; rate limits and the /health" >&2
  echo "entrypoint: allowlist will see the outer proxy's address, not the real client." >&2
fi

exec /usr/bin/supervisord -c /etc/supervisord.conf
