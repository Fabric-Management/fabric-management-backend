#!/usr/bin/env bash
# Verifies the application starts successfully (catches circular deps, Flyway, etc.).
# Requires: local DB running. Usage: scripts/check-run.sh [timeout_seconds]
set -euo pipefail

TIMEOUT="${1:-90}"
LOG=$(mktemp)
PID=""
cleanup() {
  if [[ -n "${PID}" ]] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
  fi
  rm -f "$LOG"
}
trap cleanup EXIT

cd "$(dirname "$0")/.."
echo "Starting application (timeout ${TIMEOUT}s)..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -q > "$LOG" 2>&1 &
PID=$!

for ((i=0; i<TIMEOUT; i+=3)); do
  if grep -q "Started FabricManagementApplication" "$LOG" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    PID=""
    echo "Application started successfully."
    exit 0
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Process exited early. Last lines of output:"
    tail -80 "$LOG"
    exit 1
  fi
  sleep 3
done

kill "$PID" 2>/dev/null || true
PID=""
echo "Timeout: application did not start within ${TIMEOUT}s. Last lines:"
tail -80 "$LOG"
exit 1
