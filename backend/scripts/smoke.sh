#!/usr/bin/env bash
# End-to-end smoke test against a running dev stack (docker compose + backend).
# Walks: wishlist -> wish -> group -> invite -> join -> share -> claim -> co-claim
# -> owner-invisibility -> capacity -> quantity grandfathering -> confirmed delete.
set -euo pipefail

API=${API:-http://localhost:6543}
KC=${KC:-http://localhost:8180/realms/wishlist-dev/protocol/openid-connect/token}

token() {
  curl -s "$KC" -d client_id=wishlist-cli -d grant_type=password \
    -d "username=$1" -d password=test | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
}

fail() { echo "FAIL: $1" >&2; exit 1; }
jsget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

ALICE=$(token alice@test.local)
BOB=$(token bob@test.local)
CAROL=$(token carol@test.local)
DAVE=$(token admin@test.local) # 4th participant; admin role irrelevant here

req() { # req TOKEN METHOD PATH [JSON]
  local tok=$1 method=$2 path=$3 body=${4:-}
  if [ -n "$body" ]; then
    curl -s -X "$method" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$body" "$API$path"
  else
    curl -s -X "$method" -H "Authorization: Bearer $tok" "$API$path"
  fi
}

echo "== alice creates wishlist + wish (quantity 2)"
LIST_ID=$(req "$ALICE" POST /v1/wishlists '{"name":"Birthday"}' | jsget "['id']")
WISH_ID=$(req "$ALICE" POST /v1/wishes "{\"wishlist_id\":\"$LIST_ID\",\"name\":\"Lego set\",\"description\":\"big one\",\"quantity\":2,\"links\":[{\"url\":\"https://example.com/lego\",\"label\":\"shop\"}]}" | jsget "['id']")

echo "== alice creates group + invite; bob and carol join"
GROUP_ID=$(req "$ALICE" POST /v1/groups '{"name":"Family","description":"the fam"}' | jsget "['id']")
CODE=$(req "$ALICE" POST "/v1/groups/$GROUP_ID/invites" '{"max_uses":5}' | jsget "['code']")
req "$BOB" POST /v1/memberships/join "{\"code\":\"$CODE\"}" | jsget "['id']" > /dev/null
req "$CAROL" POST /v1/memberships/join "{\"code\":\"$CODE\"}" | jsget "['id']" > /dev/null
req "$DAVE" POST /v1/memberships/join "{\"code\":\"$CODE\"}" | jsget "['id']" > /dev/null

echo "== alice shares the list into the group"
req "$ALICE" POST "/v1/groups/$GROUP_ID/shares" "{\"wishlist_id\":\"$LIST_ID\"}" > /dev/null

echo "== bob claims; carol joins bob's claim (co-claim)"
CLAIM_ID=$(req "$BOB" POST /v1/claims "{\"wish_id\":\"$WISH_ID\"}" | jsget "['claim_id']")
PARTICIPANTS=$(req "$CAROL" POST "/v1/claims/$CLAIM_ID/participants" | jsget "['participants']" | tr -d " '")
echo "   participants: $PARTICIPANTS"

echo "== owner view must contain no claim data"
ALICE_VIEW=$(req "$ALICE" GET "/v1/wishes?wishlist_id=$LIST_ID")
echo "$ALICE_VIEW" | grep -q claim && fail "owner view leaks claims: $ALICE_VIEW"

echo "== bob's shared view shows claim participants"
BOB_VIEW=$(req "$BOB" GET "/v1/wishes?wishlist_id=$LIST_ID")
echo "$BOB_VIEW" | grep -q 'Carol' || fail "shared view missing co-claimer: $BOB_VIEW"

echo "== second independent claim by carol is blocked (she already participates)"
req "$CAROL" POST /v1/claims "{\"wish_id\":\"$WISH_ID\"}" | grep -q ALREADY_CLAIMED || fail "expected ALREADY_CLAIMED"

echo "== dave takes the second unit (claim 2/2)"
req "$DAVE" POST /v1/claims "{\"wish_id\":\"$WISH_ID\"}" | jsget "['claim_id']" > /dev/null

echo "== carol leaves the co-claim and tries an own claim -> WISH_FULLY_CLAIMED"
req "$CAROL" DELETE "/v1/claims/$CLAIM_ID/participants/me" > /dev/null
req "$CAROL" POST /v1/claims "{\"wish_id\":\"$WISH_ID\"}" | grep -q WISH_FULLY_CLAIMED || fail "expected WISH_FULLY_CLAIMED"

echo "== alice lowers quantity to 1 -> existing_claims_affected (2 claims > 1)"
req "$ALICE" PATCH "/v1/wishes/$WISH_ID" '{"name":"Lego set","quantity":1,"links":[]}' \
  | grep -q '"existing_claims_affected":true' || fail "expected existing_claims_affected=true"

echo "== delete without confirm -> WISH_HAS_CLAIMS; with confirm -> gone"
req "$ALICE" DELETE "/v1/wishes/$WISH_ID" | grep -q WISH_HAS_CLAIMS || fail "expected WISH_HAS_CLAIMS"
req "$ALICE" DELETE "/v1/wishes/$WISH_ID?confirm=true" > /dev/null
req "$ALICE" GET "/v1/wishes/$WISH_ID" | grep -q WISH_NOT_FOUND || fail "wish should be soft-deleted"

echo "== bob's claims overview no longer lists the deleted wish"
req "$BOB" GET /v1/claims | grep -q "$WISH_ID" && fail "claims overview lists deleted wish"

echo "ALL SMOKE CHECKS PASSED"
