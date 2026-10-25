#!/usr/bin/env bash
# Starts the whole dev stack: Postgres + Keycloak (docker), backend, frontend.
# Ctrl+C stops backend and frontend; the containers keep running unless --down is used.
#
#   ./start.sh              start everything, follow the logs
#   ./start.sh --down       stop backend/frontend AND the containers, then exit
#   ./start.sh --rebuild    wipe the database volume before starting (fresh schema + data)
set -euo pipefail

cd "$(dirname "$0")"
LOG_DIR="${TMPDIR:-/tmp}/wishlist-dev"
mkdir -p "$LOG_DIR"

API_URL="http://localhost:6543"
UI_URL="http://localhost:5173"
KEYCLOAK_REALM_URL="http://localhost:8180/realms/wishlist-dev"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

stop_apps() {
  # started in the background below; killed by port so stale runs die too
  fuser -k 6543/tcp 2>/dev/null || true
  fuser -k 5173/tcp 2>/dev/null || true
}

wait_for() { # wait_for <url> <name> <attempts>
  local url=$1 name=$2 attempts=${3:-60}
  for _ in $(seq 1 "$attempts"); do
    if curl -sf --max-time 3 -o /dev/null "$url"; then
      return 0
    fi
    sleep 2
  done
  fail "$name did not come up in time — see $LOG_DIR"
}

if [[ "${1:-}" == "--down" ]]; then
  log "Stopping backend and frontend"
  stop_apps
  log "Stopping containers"
  docker compose down
  log "Everything stopped"
  exit 0
fi

command -v docker >/dev/null || fail "docker is required"
command -v mvn    >/dev/null || fail "maven is required"
command -v npm    >/dev/null || fail "npm is required"

if [[ "${1:-}" == "--rebuild" ]]; then
  log "Removing the database volume (all local data is lost)"
  docker compose down -v
fi

log "Starting Postgres (host port 5433) and Keycloak (8180)"
docker compose up -d

log "Waiting for the Keycloak realm import"
wait_for "$KEYCLOAK_REALM_URL/.well-known/openid-configuration" "Keycloak" 90

# stop leftovers from an earlier run before claiming the ports again
stop_apps

log "Starting backend on $API_URL (log: $LOG_DIR/backend.log)"
(cd backend && SPRING_PROFILES_ACTIVE=dev mvn -q spring-boot:run) > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

if [[ ! -d frontend/node_modules ]]; then
  log "Installing frontend dependencies"
  (cd frontend && npm install)
fi

log "Starting frontend on $UI_URL (log: $LOG_DIR/frontend.log)"
(cd frontend && npx vite --port 5173) > "$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

trap 'echo; log "Shutting down"; stop_apps; wait 2>/dev/null; exit 0' INT TERM

wait_for "$API_URL/health" "Backend" 90
wait_for "$UI_URL" "Frontend" 30

cat <<INFO

  App        $UI_URL
  API docs   $API_URL/docs/api-ui.html
  Keycloak   http://localhost:8180  (admin / admin)

  Dev logins (password "test"):
    alice@test.local   bob@test.local   carol@test.local
    admin@test.local   — has the app-admin role

  Logs: $LOG_DIR/backend.log, $LOG_DIR/frontend.log
  Ctrl+C stops backend and frontend; './start.sh --down' also stops the containers.

INFO

# keep running until Ctrl+C or one of the two processes dies
wait -n "$BACKEND_PID" "$FRONTEND_PID"
log "A process exited — shutting the rest down"
stop_apps
