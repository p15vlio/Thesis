#!/usr/bin/env bash
# Guest Desk, step 2 of 3 (Linux/macOS): builds the images and starts the complete Docker deployment,
# then waits for http://localhost:8080/api/health and http://localhost/api/health.
# The first build takes about 3-5 minutes; later builds reuse the Docker and Gradle caches.
# Usage: bash steps/02-build-and-start.sh [timeout-seconds]   (default 180)
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TIMEOUT="${1:-180}"
echo "Guest Desk - step 2/3: build and start"

[ -f "$ENV_FILE" ] || die "_Deployment/docker/.env does not exist. Run bash steps/01-check-dependencies.sh (or bash start-all.sh) first."
docker info >/dev/null 2>&1 || die "The Docker engine is not running. Start it and try again."
have curl || die "curl is needed for the health checks (e.g. sudo apt install curl)."

# ---------------------------------------------------------------- ports
step "Ports 80, 8080 and 5432"
own_ports="$(docker ps --filter 'label=com.docker.compose.project=guestdesk' --format '{{.Ports}}' || true)"
busy=""
for port in 80 8080 5432; do
    if printf '%s' "$own_ports" | grep -q ":$port->"; then continue; fi
    if [ "$(port_in_use "$port")" = "yes" ]; then busy="$busy $port"; fi
done
if [ -n "$busy" ]; then
    others="$(docker ps --format '{{.Names}} ({{.Ports}})' | grep -E ':(80|8080|5432)->' | grep -v '^guestdesk-' || true)"
    [ -z "$others" ] || warn "Other running containers publish these ports: $others (stop them with: docker stop <name>)"
    die "Ports needed by Guest Desk are in use:$busy. Free them and run again."
fi
ok "The ports are free (or already used by this stack)."

# ---------------------------------------------------------------- build + start
step "docker compose up --build -d   (first run: about 3-5 minutes)"
started=$(date +%s)
if ! compose up --build -d; then
    warn "docker compose up failed. Container status and the last backend log lines:"
    compose ps -a || true
    compose logs --tail 60 backend || true
    die "docker compose up --build -d failed. See the messages above."
fi
ok "Containers started in $(( $(date +%s) - started )) s."

# ---------------------------------------------------------------- health
step "Health checks"
if ! wait_http "http://localhost:8080/api/health" "$TIMEOUT"; then
    compose ps -a || true
    compose logs --tail 80 backend || true
    die "The backend did not answer on http://localhost:8080/api/health within $TIMEOUT s (log above)."
fi
ok "Backend: http://localhost:8080/api/health -> 200"
if ! wait_http "http://localhost/api/health" 60; then
    compose ps -a || true
    compose logs --tail 40 frontend || true
    die "The web panel (nginx) did not answer on http://localhost/api/health (log above)."
fi
ok "Web panel and API proxy: http://localhost/api/health -> 200"

printf '\nStep 2/3 done: the stack is running.\n'
