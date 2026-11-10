#!/usr/bin/env bash
# Shared helpers for the Guest Desk deployment scripts (Linux and macOS). Sourced, not executed.

# shellcheck disable=SC2034  # the paths below are used by the scripts that source this file
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_DIR="$DEPLOY_DIR/docker"
PYTHON_DIR="$DEPLOY_DIR/python"
ENV_FILE="$DOCKER_DIR/.env"
ENV_EXAMPLE="$DOCKER_DIR/.env.example"
VENV_DIR="$PYTHON_DIR/.venv"
VENV_PYTHON="$VENV_DIR/bin/python"
REQUIREMENTS="$PYTHON_DIR/requirements.txt"

step() { printf '\n==> %s\n' "$1"; }
ok()   { printf '    [OK] %s\n' "$1"; }
info() { printf '    %s\n' "$1"; }
warn() { printf '    [WARN] %s\n' "$1"; }
die()  { printf '\n[ERROR] %s\n' "$1" >&2; exit 1; }

have() { command -v "$1" >/dev/null 2>&1; }

# Asks a yes/no question; "yes" without asking when ASSUME_YES=1. No terminal means "no".
confirm() {
    if [ "${ASSUME_YES:-0}" = "1" ]; then info "$1 -> yes (--yes)"; return 0; fi
    if [ ! -t 0 ]; then return 1; fi
    local answer
    read -r -p "    $1 [y/N] " answer || return 1
    case "$answer" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

# Prints the command of a Python 3.8+ interpreter, or nothing.
find_python() {
    local c
    for c in python3 python; do
        if have "$c" && "$c" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)' >/dev/null 2>&1; then
            echo "$c"; return 0
        fi
    done
    return 1
}

random_hex() {
    if have openssl; then
        openssl rand -hex 32
    else
        od -An -tx1 -N32 /dev/urandom | tr -d ' \n'
        echo
    fi
}

# Value of KEY in .env (last occurrence, surrounding quotes removed), or empty.
env_value() {
    [ -f "$ENV_FILE" ] || return 0
    local line
    line="$(grep -E "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE" | tail -n 1 || true)"
    line="${line#*=}"
    line="$(printf '%s' "$line" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/")"
    printf '%s' "$line"
}

wait_http() {
    local url="$1" timeout="$2" deadline
    deadline=$(( $(date +%s) + timeout ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if curl -fsS -o /dev/null --max-time 5 "$url" 2>/dev/null; then return 0; fi
        sleep 3
    done
    return 1
}

compose() { (cd "$DOCKER_DIR" && docker compose "$@"); }

# Prints "yes" when something other than this stack listens on the TCP port.
port_in_use() {
    local port="$1"
    if have ss; then
        ss -ltnH 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]$port\$" && echo yes
    elif have lsof; then
        lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && echo yes
    fi
    return 0
}

summary() {
    cat <<'EOF'

============================================================
 Guest Desk is running
============================================================
  Web panel     : http://localhost
  API health    : http://localhost:8080/api/health

  Demo accounts (also on the "Demo quick login" of the sign-in screen):
    Platform admin : admin@guestdesk.io / Admin@GuestDesk1
    Test owner     : owner@guestdesk.io / Owner@GuestDesk1

  In the _Deployment folder:
    Stop (keeps the data)        : bash manage.sh down
    Reset (DELETES the database) : bash manage.sh reset   then run bash start-all.sh again
    Logs                         : bash manage.sh logs
============================================================
EOF
}
