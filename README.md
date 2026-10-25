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
./start.sh              # containers + backend + frontend, waits until everything answers
./start.sh --down       # stop backend, frontend and the containers
./start.sh --rebuild    # wipe the database volume first (fresh schema and data)
```

Ctrl+C stops backend and frontend and leaves the containers running.
Logs land in `/tmp/wishlist-dev/`.

Starting the parts by hand instead:

```bash
docker compose up -d                 # Postgres 18 (host port 5433) + Keycloak 26
cd backend && mvn spring-boot:run    # http://localhost:6543, Swagger at /docs/api-ui.html
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Keycloak: http://localhost:8180 (admin/admin). Realm `wishlist-dev` is imported on first start. Self-registration is enabled in the dev realm.

Dev users (password `test`): `alice@test.local`, `bob@test.local`, `carol@test.local`,
`admin@test.local` (has the `admin` realm role).

Get a dev token via password grant:

```bash
TOKEN=$(curl -s http://localhost:8180/realms/wishlist-dev/protocol/openid-connect/token \
  -d client_id=wishlist-cli -d grant_type=password \
  -d username=alice@test.local -d password=test | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:6543/v1/users/me
```

## Deployment

Postgres and Keycloak are hosted externally; everything of ours ships as **one image**
(`deploy/Dockerfile`): the built SPA, the backend, and an nginx that serves the SPA,
proxies `/v1` to `127.0.0.1:6543` and rate-limits the sensitive endpoints. TLS, the public
hostname and Keycloak's rate limiting belong to the **outer** nginx in front of it.

```bash
cd deploy
cp .env.example .env && $EDITOR .env    # external DB + Keycloak coordinates, secrets
docker compose up -d --build            # publishes 127.0.0.1:8080 for the outer nginx
```

| Inside the image | |
|---|---|
| nginx | port 8080; SPA with SPA-fallback, `/v1/**` proxied, `/docs/**` unrouted (falls back to the SPA) |
| Rate limits | 20r/s per client on `/v1/**`; 12r/min on `/v1/users/me/password`, `/v1/users/me/email`, `/v1/memberships/join`, `/v1/invites/**` (incl. the public invite preview) |
| `/health` | private networks + loopback only — do not proxy it from the outer nginx |
| Backend | `SPRING_PROFILES_ACTIVE=prod` (Swagger and the OpenAPI spec off), runs as uid 10001 |

Client addresses come from `X-Forwarded-For`, but only for the proxies listed in
`TRUSTED_PROXY_CIDR` — set it, or the rate limits key on the outer nginx and everyone
shares one bucket.

The SPA is configured at **runtime**: the entrypoint writes `/config.js` from the
container's environment (`KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`,
`PUBLIC_API_URL`, `ADS_ENABLED`), so the same image runs in any environment. The
`VITE_*` vars in `frontend/.env.development` are only the dev-server fallback.

## Configuration (env vars)

| Var | Default |
|-----|---------|
| `SERVER_PORT` | `6543` |
| `DB_URL` | `jdbc:postgresql://localhost:5433/wishlist` (compose maps 5433→5432 to avoid clashing with a system Postgres) |
| `DB_USER` / `DB_PASSWORD` | `wishlist` / `wishlist` |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/wishlist-dev` |
| `KEYCLOAK_ACCOUNT_URL` | `http://localhost:8180/realms/wishlist-dev/account` |
| `FRONTEND_ORIGIN` | `http://localhost:5173` |
| `PURGE_ENABLED` | `true` (scheduled purge of soft-deleted rows > 30 days; `false` disables) |
| `LINK_BLOCKLIST_ENABLED` | `true` (download domain blocklists at start and daily; wish links to listed domains are rejected) |
| `LINK_BLOCKLIST_URLS` | comma-separated hosts-file or domain-per-line lists (default: URLhaus filter + Block List Project phishing/malware/scam/fraud/porn/abuse) |
| `LINK_ALLOWED_DOMAINS` | comma-separated shop domains (and subdomains) that skip all link checks (default: major shops, see `application.yml`) |
| `WEB_RISK_API_KEY` | empty (set to enable Google Web Risk lookups for non-allowlisted links; fails open) |

## Roles

**App admin** (Keycloak realm role `admin`) — sees the *Admin* entry in the menu:
aggregate counts (users / wishlists / wishes), account search **by email only**, and
switching a user's tier between FREE and PREMIUM. Admins get numbers and account data;
they have no access to anyone's wishlists, wishes, groups, or claims.

**Group admin** (`is_admin` on a membership) — manages one group: name/description,
invites (create, list, revoke), promoting/demoting members, removing members, removing
shares, deleting the group. A group can have several admins; an admin may leave whenever
another admin remains — only the **last** admin can neither leave nor be demoted. The
creator is shown as *Owner* for orientation only and is handed over to another admin if
they leave.

## Testing

Three layers — every endpoint is covered, plus a real-browser flow:

| Layer | What | How to run |
|-------|------|------------|
| Unit tests | UUIDv7, limits, claim rules, invite joining | `cd backend && mvn test` |
| Full-flow integration | EVERY `/v1` endpoint in dependency order against real Postgres 18 (Flyway schema, constraints, locks); JWTs injected, no Keycloak needed | CI: `mvn verify -Pit` (Testcontainers). Locally (firewall blocks Testcontainers): `backend/scripts/integration_test_local.sh` (uses the dev-stack PG with a throwaway DB) |
| Browser E2E (Python/Playwright) | Real Keycloak login, wishlist→group→invite→join→share→claim, owner-invisibility check, admin console + non-admin lockout | `cd tests/e2e && pip install -r requirements.txt && playwright install chromium && pytest` (dev stack + backend + frontend must be running) |
| Manual per-endpoint | Swagger UI with bearer auth | `http://localhost:6543/docs/api-ui.html`; scripted variant: `backend/scripts/smoke.sh` |

CI (`.github/workflows/ci.yml`) runs all three layers on every push/PR.

## Conventions

- Every list endpoint answers with the same envelope:
  `{ "total": <matching rows>, "count": <returned>, "limit": <applied>, "offset": <first index>, "items": [...] }`.
  `limit` is the requester's choice and gets **clamped** to 1..100 (default 20) instead of rejected.
  The UI offers 10/20/50/80/100; phones append pages while scrolling, wider screens page explicitly.
- An email belongs to exactly one account, including while that account is soft-deleted:
  signing in with a new identity for a taken address returns `EMAIL_ALREADY_EXISTS` telling
  the user to reactivate instead. Accounts coming from an external provider (Google, Apple)
  carry `identity_provider` on `/v1/users/me`; email and password changes are refused for them
  (`ACCOUNT_MANAGED_EXTERNALLY`) because the provider owns those credentials.
- Leaving a group — or deleting an account — removes that user's shares from the affected groups.

- All endpoints versioned: `/v1/...`
- All IDs are UUIDv7 (custom Hibernate generator; PG 18 `uuidv7()` as column default).
  No `created_at` columns — creation time is extracted from the id.
- DB FK columns: full plural table name + `_id` (`users_id`, `wishlists_id`).
  API JSON: singular snake_case (`user_id`, `wishlist_id`).
- Soft delete: main tables carry `deleted_at`; purge script `backend/scripts/purge_soft_deleted.sql`.
- Every error response: `{ "error_code": "...", "message": "..." }` — message is English for
  direct API users, `error_code` is translated by the frontend (de/en).
