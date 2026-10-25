#!/usr/bin/env bash
# Configures Google login in a Keycloak 26 realm via kcadm.sh (needs jq). Safe to re-run.
# Env: KC_URL KC_REALM KC_ADMIN KC_ADMIN_PASSWORD GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET
# KCADM: default kcadm.sh; in docker use "docker exec -i -e KC_CLI_PASSWORD <ctr> /opt/keycloak/bin/kcadm.sh".
set -euo pipefail

: "${KC_URL:?KC_URL is required}" "${KC_REALM:?KC_REALM is required}"
: "${KC_ADMIN:?KC_ADMIN is required}" "${KC_ADMIN_PASSWORD:?KC_ADMIN_PASSWORD is required}"
: "${GOOGLE_CLIENT_ID:?GOOGLE_CLIENT_ID is required}"
: "${GOOGLE_CLIENT_SECRET:?GOOGLE_CLIENT_SECRET is required}"
KCADM="${KCADM:-kcadm.sh}"
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

FLOW_SRC="first broker login"
FLOW_NEW="first broker login - no review"   # Keycloak rejects parentheses in flow names

# kc <args...>: runs kcadm against the target realm (word-splits KCADM on purpose)
kc() { $KCADM "$@"; }
enc() { jq -rn --arg v "$1" '$v|@uri'; }
log() { echo "setup-google: $*"; }

export KC_CLI_PASSWORD="$KC_ADMIN_PASSWORD"
kc config credentials --server "$KC_URL" --realm master --user "$KC_ADMIN" >/dev/null

# 1. flow copy without the review-profile step
if kc get authentication/flows -r "$KC_REALM" | jq -e --arg a "$FLOW_NEW" 'any(.[]; .alias == $a)' >/dev/null; then
  log "flow '$FLOW_NEW' exists"
else
  kc create "authentication/flows/$(enc "$FLOW_SRC")/copy" -r "$KC_REALM" -s newName="$FLOW_NEW" >/dev/null
  log "flow '$FLOW_NEW' created"
fi

EXECS=$(kc get "authentication/flows/$(enc "$FLOW_NEW")/executions" -r "$KC_REALM")

# review-profile off: update the execution's config, creating one if the copy has none
REVIEW_ID=$(jq -r 'map(select(.providerId == "idp-review-profile")) | .[0].id // empty' <<<"$EXECS")
REVIEW_CFG=$(jq -r 'map(select(.providerId == "idp-review-profile")) | .[0].authenticationConfig // empty' <<<"$EXECS")
[[ -n "$REVIEW_ID" ]] || { echo "review-profile execution not found in '$FLOW_NEW'" >&2; exit 1; }
if [[ -n "$REVIEW_CFG" ]]; then
  kc update "authentication/config/$REVIEW_CFG" -r "$KC_REALM" -s 'config."update.profile.on.first.login"=off' >/dev/null
else
  kc create "authentication/executions/$REVIEW_ID/config" -r "$KC_REALM" \
    -s alias="review profile config - no review" -s 'config."update.profile.on.first.login"=off' >/dev/null
fi
log "review profile step off"

# e-mail verification off: an existing account is linked only after password re-authentication
EMAIL_ID=$(jq -r 'map(select(.providerId == "idp-email-verification")) | .[0].id // empty' <<<"$EXECS")
[[ -n "$EMAIL_ID" ]] || { echo "'Verify Existing Account By Email' not found in '$FLOW_NEW'" >&2; exit 1; }
kc update "authentication/flows/$(enc "$FLOW_NEW")/executions" -r "$KC_REALM" \
  -b "{\"id\":\"$EMAIL_ID\",\"requirement\":\"DISABLED\"}" >/dev/null
log "email verification step disabled"

# 2. identity provider
export GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET FLOW_NEW
idp_json() {
  jq -n '{alias: "google", providerId: "google", enabled: true, trustEmail: true,
    firstBrokerLoginFlowAlias: $ENV.FLOW_NEW,
    config: {clientId: $ENV.GOOGLE_CLIENT_ID, clientSecret: $ENV.GOOGLE_CLIENT_SECRET, syncMode: "IMPORT"}}'
}
if kc get identity-provider/instances/google -r "$KC_REALM" >/dev/null 2>&1; then
  idp_json | kc update identity-provider/instances/google -r "$KC_REALM" -f - >/dev/null
  log "identity provider google updated"
else
  idp_json | kc create identity-provider/instances -r "$KC_REALM" -f - >/dev/null
  log "identity provider google created"
fi

# 3. wishlist-cli (direct grant for password checks) must not mint tokens the API accepts
client_id() { kc get clients -r "$KC_REALM" -q "clientId=$1" --fields id | jq -r '.[0].id // empty'; }
CLI_ID=$(client_id wishlist-cli)
if [[ -n "$CLI_ID" ]]; then
  MAPPER_ID=$(kc get "clients/$CLI_ID/protocol-mappers/models" -r "$KC_REALM" \
    | jq -r 'map(select(.name == "wishlist-api audience")) | .[0].id // empty')
  if [[ -n "$MAPPER_ID" ]]; then
    kc delete "clients/$CLI_ID/protocol-mappers/models/$MAPPER_ID" -r "$KC_REALM"
    log "audience mapper removed from wishlist-cli"
  fi
else
  log "client wishlist-cli not found, skipped"
fi

# 4. the SPA reads identity_provider from the token to hide password/email changes for Google users
FE_ID=$(client_id wishlist-frontend)
[[ -n "$FE_ID" ]] || { echo "client wishlist-frontend not found" >&2; exit 1; }
if kc get "clients/$FE_ID/protocol-mappers/models" -r "$KC_REALM" \
  | jq -e 'any(.[]; .protocolMapper == "oidc-usersessionmodel-note-mapper" and .config["user.session.note"] == "identity_provider")' >/dev/null; then
  log "identity_provider mapper exists"
else
  kc create "clients/$FE_ID/protocol-mappers/models" -r "$KC_REALM" \
    -s name="identity provider" -s protocol=openid-connect \
    -s protocolMapper=oidc-usersessionmodel-note-mapper \
    -s 'config."user.session.note"=identity_provider' -s 'config."claim.name"=identity_provider' \
    -s 'config."jsonType.label"=String' -s 'config."access.token.claim"=true' \
    -s 'config."id.token.claim"=true' >/dev/null
  log "identity_provider mapper created"
fi
log "done"
