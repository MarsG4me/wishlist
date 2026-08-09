#!/usr/bin/env bash
# Runs the full-flow integration tests against the local dev-stack Postgres.
# Needed on machines whose firewall blocks Testcontainers port mapping
# (CI just runs `mvn verify -Pit` with Testcontainers).
# Uses a throwaway database so the dev data stays untouched.
set -euo pipefail

PGURL="postgresql://wishlist:wishlist@127.0.0.1:5433"

psql "$PGURL/wishlist" -c "DROP DATABASE IF EXISTS wishlist_it;" > /dev/null
psql "$PGURL/wishlist" -c "CREATE DATABASE wishlist_it OWNER wishlist;" > /dev/null

cd "$(dirname "$0")/.."
IT_DB_URL="jdbc:postgresql://127.0.0.1:5433/wishlist_it" mvn verify -Pit
