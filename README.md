# Wishlist

Web app to manage and share wishlists. Lists are shared into groups; group members can claim
wishes (reserve them as gifts) so nothing gets gifted twice. The wish owner never sees claims.

## Stack

| Part      | Tech |
|-----------|------|
| Backend   | Spring Boot 4 (Java 25), PostgreSQL 18, Flyway, Keycloak (OIDC resource server) |
| Frontend  | Vite + TypeScript + React + PrimeReact, keycloak-js |
| Dev infra | docker-compose: Postgres 18 + Keycloak 26 (realm auto-import) |

## Local development

```bash
# 1. Infrastructure
docker compose up -d

# 2. Backend (http://localhost:6543, Swagger UI: /docs/api-ui.html)
cd backend && mvn spring-boot:run

# 3. Frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```

Keycloak: http://localhost:8180 (admin/admin). Realm `wishlist-dev` is imported on first start.

Dev users (password `test`): `alice@test.local`, `bob@test.local`, `carol@test.local`,
`admin@test.local` (has the `admin` realm role).

Get a dev token via password grant:

```bash
TOKEN=$(curl -s http://localhost:8180/realms/wishlist-dev/protocol/openid-connect/token \
  -d client_id=wishlist-cli -d grant_type=password \
  -d username=alice@test.local -d password=test | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:6543/v1/users/me
```

## Configuration (env vars)

| Var | Default |
|-----|---------|
| `SERVER_PORT` | `6543` |
| `DB_URL` | `jdbc:postgresql://localhost:5433/wishlist` (compose maps 5433→5432 to avoid clashing with a system Postgres) |
| `DB_USER` / `DB_PASSWORD` | `wishlist` / `wishlist` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/wishlist-dev` |
| `KEYCLOAK_ACCOUNT_URL` | `http://localhost:8180/realms/wishlist-dev/account` |
| `FRONTEND_ORIGIN` | `http://localhost:5173` |
| `PURGE_ENABLED` | `false` (opt-in scheduled purge of soft-deleted rows > 30 days) |

## Testing

Three layers — every endpoint is covered, plus a real-browser flow:

| Layer | What | How to run |
|-------|------|------------|
| Unit tests | UUIDv7, limits, claim rules, invite joining | `cd backend && mvn test` |
| Full-flow integration | EVERY `/v1` endpoint in dependency order against real Postgres 18 (Flyway schema, constraints, locks); JWTs injected, no Keycloak needed | CI: `mvn verify -Pit` (Testcontainers). Locally (firewall blocks Testcontainers): `backend/scripts/integration_test_local.sh` (uses the dev-stack PG with a throwaway DB) |
| Browser E2E (Python/Playwright) | Real Keycloak login, wishlist→group→invite→join→share→claim, owner-invisibility check | `cd tests/e2e && pip install -r requirements.txt && playwright install chromium && pytest` (dev stack + backend + frontend must be running) |
| Manual per-endpoint | Swagger UI with bearer auth | `http://localhost:6543/docs/api-ui.html`; scripted variant: `backend/scripts/smoke.sh` |

CI (`.github/workflows/ci.yml`) runs all three layers on every push/PR.

## Conventions

- All endpoints versioned: `/v1/...`
- All IDs are UUIDv7 (custom Hibernate generator; PG 18 `uuidv7()` as column default).
  No `created_at` columns — creation time is extracted from the id.
- DB FK columns: full plural table name + `_id` (`users_id`, `wishlists_id`).
  API JSON: singular snake_case (`user_id`, `wishlist_id`).
- Soft delete: main tables carry `deleted_at`; purge script `backend/scripts/purge_soft_deleted.sql`.
- Every error response: `{ "error_code": "...", "message": "..." }` — message is English for
  direct API users, `error_code` is translated by the frontend (de/en).
