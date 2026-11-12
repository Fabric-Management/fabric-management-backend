#!/usr/bin/env bash
# Run the CI gates locally before pushing. Mirrors .github/workflows/ci.yml, job by job and in the
# same order: code quality, dependency security, tests and coverage, container build.
#
# Usage:
#   bash scripts/ci-local.sh                    # everything except Dependency-Check (needs a key)
#   NVD_API_KEY=xxx bash scripts/ci-local.sh    # also the OWASP Dependency-Check security gate
#   SKIP_TESTS=1 bash scripts/ci-local.sh       # quality gates only (fast)
#   SKIP_CONTAINER=1 bash scripts/ci-local.sh   # without the Docker image build
#
# Each step prints how long it took, and the end repeats every step with its time and result. The
# whole output is also written to /tmp/ci-local.log (CI_LOCAL_LOG to change it): not to target/,
# which the test step cleans. On a Mac the script keeps the machine awake until it finishes.
#
# The Dependency-Check step contacts the NVD API. With a warm cache (~/.m2/dependency-check-data)
# and nvdValidForHours=168 it reuses local data for 7 days and won't hit NVD; on a cold cache while
# NVD is down (503) it will fail — that's an upstream NVD outage, not your code.
set -uo pipefail
cd "$(dirname "$0")/.."

LOG="${CI_LOCAL_LOG:-/tmp/ci-local.log}"
exec > >(tee "$LOG") 2>&1

# On a Mac, stay awake until this script exits: a sleep in the middle stops the test containers and
# turns the timings into nonsense. caffeinate -w ends by itself with this process.
if [ "$(uname)" = "Darwin" ] && command -v caffeinate >/dev/null 2>&1; then
  caffeinate -i -w $$ &
fi

MVN="./mvnw"
[ -x "$MVN" ] || MVN="mvn"
failed=()
summary=()
step() {
  echo ""
  echo "════════════════════════════════════════════════════════════"
  echo "▶ $*"
  echo "════════════════════════════════════════════════════════════"
  local started=$SECONDS
  local line
  if "$@"; then
    line="✓ $(( (SECONDS - started) / 60 ))m $(( (SECONDS - started) % 60 ))s: $*"
  else
    line="✗ FAILED after $(( (SECONDS - started) / 60 ))m $(( (SECONDS - started) % 60 ))s: $*"
    failed+=("$*")
  fi
  echo "$line"
  summary+=("$line")
}

echo "== Code Quality =="
step python3 -B -m unittest discover -s scripts -p 'test_*.py'
# Reads the pinned PostgreSQL index from the Docker registry. Tag drift and newer maintenance
# releases are reported by the weekly freshness workflow (BUILD-5), not here.
step python3 -B scripts/postgres_image.py --check
step "$MVN" fmt:check
step "$MVN" checkstyle:check
step "$MVN" spotbugs:check

echo ""
echo "== Dependency Security =="
step python3 scripts/check-suppression-expiry.py --warn-days 0
if [ -z "${NVD_API_KEY:-}" ]; then
  echo "Dependency-Check: SKIPPED (export NVD_API_KEY to run the security gate)"
else
  # Same plugin version and options as CI.
  step "$MVN" org.owasp:dependency-check-maven:12.2.2:check \
    -DfailBuildOnCVSS=7 \
    -Dformats=HTML,JSON \
    -DdataDirectory="${HOME}/.m2/dependency-check-data" \
    -DassemblyAnalyzerEnabled=false \
    -DskipProvidedScope=true \
    -DsuppressionFile=dependency-check-suppression.xml \
    -DnvdApiKey="${NVD_API_KEY}" \
    -DnvdValidForHours=168 \
    -DnvdApiDelay=6000
fi

echo ""
if [ "${SKIP_TESTS:-}" = "1" ]; then
  echo "== Tests & Coverage: skipped (SKIP_TESTS=1) =="
else
  echo "== Tests & Coverage =="
  step "$MVN" clean verify -Pcoverage
fi

echo ""
if [ "${SKIP_CONTAINER:-}" = "1" ]; then
  echo "== Container Build: skipped (SKIP_CONTAINER=1) =="
elif ! command -v docker >/dev/null 2>&1; then
  echo "== Container Build: skipped because Docker is not installed =="
else
  echo "== Container Build =="
  step docker build --platform linux/amd64 -f Dockerfile.service -t fabric-management-backend:ci .
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Summary (full output: $LOG)"
printf '%s\n' "${summary[@]}"
echo "Total: $(( SECONDS / 60 ))m $(( SECONDS % 60 ))s"
if [ "${#failed[@]}" -ne 0 ]; then
  echo "❌ ci-local FAILED — fix these steps before pushing:"
  printf '   - %s\n' "${failed[@]}"
  exit 1
fi
echo "✅ ci-local PASSED — safe to push."
