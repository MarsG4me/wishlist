# One image: built SPA + Spring Boot backend + the nginx that fronts both.
# Build from the repository root:  docker build -f deploy/Dockerfile -t wishlist .
# Postgres and Keycloak are external; TLS and Keycloak rate limiting live in the outer nginx.

# --- frontend ---------------------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# No VITE_* vars here on purpose: API and Keycloak coordinates are injected at container
# start via /config.js, so the same bundle runs in every environment.
RUN npm run build

# --- backend ----------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q package -DskipTests

# --- runtime ----------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache nginx supervisor tini curl \
 && addgroup -S app && adduser -S -u 10001 -G app app

COPY deploy/nginx.conf        /etc/nginx/nginx.conf
COPY deploy/proxy_params.conf /etc/nginx/proxy_params.conf
COPY deploy/security_headers.conf /etc/nginx/security_headers.conf
COPY deploy/supervisord.conf  /etc/supervisord.conf
COPY deploy/entrypoint.sh     /usr/local/bin/entrypoint.sh
COPY --from=frontend /app/dist /srv/www
COPY --from=backend  /app/target/*.jar /opt/wishlist/app.jar

# /srv/www: the entrypoint rewrites config.js in it. /tmp/nginx: nginx runs unprivileged.
# /var/log/nginx: target of /var/lib/nginx/logs, the compile-time default error log that
# nginx opens before it reads the config — unwritable it alerts on every start.
RUN chmod +x /usr/local/bin/entrypoint.sh \
 && mkdir -p /tmp/nginx \
 && chown -R app:app /srv/www /tmp/nginx /opt/wishlist /var/lib/nginx /var/log/nginx

ENV SERVER_PORT=6543 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

USER app
EXPOSE 8080

# probes the backend directly: nginx answering while the API is down is not "healthy"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=4 \
  CMD curl -fsS http://127.0.0.1:6543/health || exit 1

ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/entrypoint.sh"]
