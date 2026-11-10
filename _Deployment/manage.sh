#!/usr/bin/env bash
# Guest Desk - Docker Compose helper for Linux and macOS.
# For the first start use start-all.sh, which also creates docker/.env and seeds the data.
#
# Usage: bash manage.sh [up|down|status|logs|reset]
#   up     : builds and starts all services in the background
#   down   : stops and removes the containers; the data volume is kept
#   status : shows the status of the containers
#   logs   : follows the logs of all services, Ctrl+C to exit
#   reset  : stops the stack and DELETES the database volume
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/docker"

case "${1:-}" in
    up)     echo "[INFO] Building and starting all services..."; docker compose up --build -d ;;
    down)   echo "[INFO] Stopping all services, the database volume is kept..."; docker compose down ;;
    status) docker compose ps -a ;;
    logs)   echo "[INFO] Showing logs, press Ctrl+C to exit..."; docker compose logs -f ;;
    reset)
        echo "[WARN] This stops the stack and DELETES the database volume with all data."
        read -r -p "Type YES to continue: " confirm
        if [ "$confirm" = "YES" ]; then docker compose down -v; else echo "[INFO] Cancelled."; fi
        ;;
    *)
        echo "Usage: bash manage.sh [up|down|status|logs|reset]" >&2
        exit 1
        ;;
esac
