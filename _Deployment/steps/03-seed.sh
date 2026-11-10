#!/usr/bin/env bash
# Guest Desk, step 3 of 3 (Linux/macOS): creates the platform administrator and the demo data with the
# Python of _Deployment/python/.venv. Both seed scripts wait for the backend and its tables and are idempotent.
# Usage: bash steps/03-seed.sh [--admin-only]
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

ADMIN_ONLY=0
for arg in "$@"; do
    case "$arg" in
        --admin-only) ADMIN_ONLY=1 ;;
        *) die "Unknown option: $arg (usage: $0 [--admin-only])" ;;
    esac
done

echo "Guest Desk - step 3/3: seed data"
[ -x "$VENV_PYTHON" ] || die "The Python environment _Deployment/python/.venv does not exist. Run bash steps/01-check-dependencies.sh (or bash start-all.sh) first."
[ -f "$ENV_FILE" ] || die "_Deployment/docker/.env is missing. Run bash steps/01-check-dependencies.sh (or bash start-all.sh) first."

# The seed scripts read these; they must match the database of the compose stack.
export DB_HOST=localhost DB_PORT=5432 BASE_URL=http://localhost:8080
DB_NAME="$(env_value POSTGRES_DB)"; export DB_NAME
DB_USER="$(env_value POSTGRES_USER)"; export DB_USER
DB_PASSWORD="$(env_value POSTGRES_PASSWORD)"; export DB_PASSWORD

step "Platform administrator (seed_admin.py)"
"$VENV_PYTHON" "$PYTHON_DIR/seed_admin.py" || die "seed_admin.py failed (see the message above)."

if [ "$ADMIN_ONLY" = "0" ]; then
    step "Demo owners, properties and products (seed_data.py)"
    "$VENV_PYTHON" "$PYTHON_DIR/seed_data.py" || die "seed_data.py failed (see the message above)."
fi

printf '\nStep 3/3 done: seed data is in place.\n'
