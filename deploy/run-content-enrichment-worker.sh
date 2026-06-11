#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCK_FILE="${CONTENT_ENRICHMENT_LOCK_FILE:-/tmp/newsletter-content-enrichment-worker.lock}"

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"

cd "$SCRIPT_DIR"
mkdir -p logs/content-enrichment-worker

run_worker() {
  docker compose --profile batch run --rm --no-deps content-enrichment-worker
}

if command -v flock >/dev/null 2>&1; then
  flock -n "$LOCK_FILE" bash -c 'docker compose --profile batch run --rm --no-deps content-enrichment-worker'
else
  if [ -f "$LOCK_FILE" ]; then
    echo "Content enrichment worker is already running. lock=$LOCK_FILE"
    exit 0
  fi

  trap 'rm -f "$LOCK_FILE"' EXIT
  echo "$$" > "$LOCK_FILE"
  run_worker
fi
