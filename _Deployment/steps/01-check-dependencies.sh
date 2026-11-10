#!/usr/bin/env bash
# Guest Desk, step 1 of 3 (Linux/macOS): checks the dependencies and prepares .env.
#   - Docker CLI, Docker Compose v2 and a running Docker engine (on macOS starts Docker Desktop and waits)
#   - Python 3.8+ and a local virtual environment _Deployment/python/.venv with requirements.txt
#   - _Deployment/docker/.env from .env.example with freshly generated random secrets (an existing .env is kept)
# Usage: bash steps/01-check-dependencies.sh [--yes]
# Docker and Python are never installed by this script; it prints the command to use instead.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

for arg in "$@"; do
    case "$arg" in
        -y|--yes) ASSUME_YES=1 ;;
        *) die "Unknown option: $arg (usage: $0 [--yes])" ;;
    esac
done

OS="$(uname -s)"
echo "Guest Desk - step 1/3: dependencies"

# ---------------------------------------------------------------- Docker
step "Docker"
if ! have docker; then
    if [ "$OS" = "Darwin" ]; then
        die "Docker is not installed. Install Docker Desktop: https://www.docker.com/products/docker-desktop/ (or: brew install --cask docker), start it once, then run this script again."
    fi
    die "Docker is not installed. Install Docker Engine and the compose plugin: https://docs.docker.com/engine/install/ (e.g. Ubuntu: sudo apt install docker.io docker-compose-v2), then run this script again."
fi
ok "Docker CLI: $(docker --version)"

docker compose version >/dev/null 2>&1 \
    || die "Docker Compose v2 (\"docker compose\") is not available. Install the compose plugin: https://docs.docker.com/compose/install/linux/"
ok "Docker Compose: $(docker compose version --short)"

if ! docker info >/dev/null 2>&1; then
    if [ "$OS" = "Darwin" ] && [ -d "/Applications/Docker.app" ]; then
        info "The Docker engine is not running: starting Docker Desktop (this can take a minute) ..."
        open -a Docker
        deadline=$(( $(date +%s) + 180 ))
        until docker info >/dev/null 2>&1; do
            [ "$(date +%s)" -lt "$deadline" ] || die "Docker Desktop did not start within 180 s. Start it by hand and run this script again."
            sleep 3
        done
    elif docker info 2>&1 | grep -qi "permission denied"; then
        die "No permission to use Docker. Add your user to the docker group (sudo usermod -aG docker \$USER, then log out and in) or run the script with sudo."
    else
        die "The Docker engine is not running. Start it (Linux: sudo systemctl start docker; macOS: open Docker Desktop) and run this script again."
    fi
fi
ok "Docker engine is running."

# ---------------------------------------------------------------- Python
step "Python 3 (for the seed scripts)"
PY="$(find_python || true)"
if [ -z "$PY" ]; then
    if [ "$OS" = "Darwin" ]; then
        die "Python 3.8+ was not found. Install it (brew install python, or https://www.python.org/downloads/) and run this script again."
    fi
    die "Python 3.8+ was not found. Install it (e.g. Ubuntu/Debian: sudo apt install python3 python3-venv; Fedora: sudo dnf install python3) and run this script again."
fi
ok "Python: $("$PY" --version 2>&1) (command: $PY)"

# ---------------------------------------------------------------- virtual environment + packages
step "Python packages (local virtual environment _Deployment/python/.venv)"
if [ ! -x "$VENV_PYTHON" ]; then
    info "Creating the virtual environment $VENV_DIR ..."
    if ! "$PY" -m venv "$VENV_DIR"; then
        rm -rf "$VENV_DIR"
        die "Could not create the virtual environment. On Ubuntu/Debian install the venv module: sudo apt install python3-venv"
    fi
fi
if "$VENV_PYTHON" -c "import requests, bcrypt, psycopg2" >/dev/null 2>&1; then
    ok "requests, bcrypt and psycopg2-binary are installed."
else
    info "Missing packages. Installing into _Deployment/python/.venv only (not system-wide):"
    info "  pip install -r python/requirements.txt   (requests, bcrypt, psycopg2-binary)"
    "$VENV_PYTHON" -m pip install --disable-pip-version-check -q -r "$REQUIREMENTS" \
        || die "pip could not install the packages. Check the internet connection and try again."
    "$VENV_PYTHON" -c "import requests, bcrypt, psycopg2" >/dev/null 2>&1 \
        || die "The packages were installed but cannot be imported."
    ok "Packages installed."
fi

# ---------------------------------------------------------------- .env
step "Configuration (_Deployment/docker/.env)"
if [ -f "$ENV_FILE" ]; then
    ok ".env exists and is left untouched."
else
    [ -f "$ENV_EXAMPLE" ] || die ".env.example is missing."
    jwt="$(random_hex)"
    settings="$(random_hex)"
    [ ${#jwt} -ge 64 ] && [ ${#settings} -ge 64 ] || die "Could not generate random secrets."
    (umask 077 && sed -e "s/^JWT_SECRET=.*/JWT_SECRET=$jwt/" -e "s/^SETTINGS_SECRET=.*/SETTINGS_SECRET=$settings/" \
        "$ENV_EXAMPLE" | tr -d '\r' > "$ENV_FILE")
    ok ".env created from .env.example with newly generated random JWT_SECRET and SETTINGS_SECRET."
    info "For a kiosk tablet, set PUBLIC_API_URL in .env to the LAN address of this computer (see the Start Guide)."
fi
for key in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD JWT_SECRET SETTINGS_SECRET; do
    [ -n "$(env_value "$key")" ] || die "$key is empty or missing in _Deployment/docker/.env. Set it (or delete .env so a new one is generated)."
done
ok "Required variables are set."

printf '\nStep 1/3 done: all dependencies are in place.\n'
