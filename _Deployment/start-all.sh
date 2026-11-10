#!/usr/bin/env bash
# Guest Desk one-click start (Linux/macOS): dependencies, build and start, seed data, summary.
# Stops at the first error. Safe to run again: an existing .env is kept and the seed steps skip
# what already exists.
# Usage: ./start-all.sh [--yes] [--skip-seed]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=steps/common.sh
source "$HERE/steps/common.sh"

DEPS_ARGS=()
SKIP_SEED=0
for arg in "$@"; do
    case "$arg" in
        -y|--yes) DEPS_ARGS+=("--yes") ;;
        --skip-seed) SKIP_SEED=1 ;;
        *) die "Unknown option: $arg (usage: $0 [--yes] [--skip-seed])" ;;
    esac
done

started=$(date +%s)
echo "############################################################"
echo " Guest Desk - one-click deployment"
echo "############################################################"

bash "$HERE/steps/01-check-dependencies.sh" "${DEPS_ARGS[@]+"${DEPS_ARGS[@]}"}" || die "Step 1 (dependencies) failed. Nothing was started."
bash "$HERE/steps/02-build-and-start.sh" || die "Step 2 (build and start) failed."
if [ "$SKIP_SEED" = "0" ]; then
    bash "$HERE/steps/03-seed.sh" || die "Step 3 (seed data) failed. The stack is running; fix the problem and run bash steps/03-seed.sh again."
fi

summary
echo "  Total time: $(( $(date +%s) - started )) s"
