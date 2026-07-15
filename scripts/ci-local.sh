#!/usr/bin/env bash
# Run the CI gates locally before pushing — mirrors .github/workflows/ci.yml.
#
# Usage:
#   bash scripts/ci-local.sh              # quality + tests (Dependency-Check skipped unless NVD_API_KEY set)
#   NVD_API_KEY=xxx bash scripts/ci-local.sh   # also run the OWASP Dependency-Check security gate
#   SKIP_TESTS=1 bash scripts/ci-local.sh      # quality gates only (fast)
#
# The Dependency-Check step contacts the NVD API. With a warm cache (~/.m2/dependency-check-data)
# and nvdValidForHours=168 it reuses local data for 7 days and won't hit NVD; on a cold cache while
# NVD is down (503) it will fail — that's an upstream NVD outage, not your code.
set -uo pipefail
cd "$(dirname "$0")/.."

MVN="./mvnw"
[ -x "$MVN" ] || MVN="mvn"
fail=0
step() {
  echo ""
  echo "════════════════════════════════════════════════════════════"
  echo "▶ $*"
  echo "════════════════════════════════════════════════════════════"
  "$@" || { echo "✗ FAILED: $*"; fail=1; }
}

echo "== Code Quality Checks =="
step "$MVN" fmt:check
step "$MVN" checkstyle:check
step "$MVN" spotbugs:check

if [ "${SKIP_TESTS:-}" = "1" ]; then
  echo "== Tests: skipped (SKIP_TESTS=1) =="
else
  echo "== Tests =="
  step "$MVN" clean verify -Pcoverage
fi

if [ -z "${NVD_API_KEY:-}" ]; then
  echo ""
  echo "== Dependency-Check: SKIPPED (export NVD_API_KEY to run the security gate) =="
else
  echo "== Security Scan (OWASP Dependency-Check) =="
  step "$MVN" org.owasp:dependency-check-maven:check \
    -DfailBuildOnCVSS=7 \
    -DdataDirectory="${HOME}/.m2/dependency-check-data" \
    -DskipProvidedScope=true \
    -DsuppressionFile=dependency-check-suppression.xml \
    -DnvdApiKey="${NVD_API_KEY}" \
    -DnvdValidForHours=168 \
    -DnvdApiDelay=6000
fi

echo ""
if [ "$fail" -ne 0 ]; then
  echo "❌ ci-local FAILED — fix the steps above before pushing."
  exit 1
fi
echo "✅ ci-local PASSED — safe to push."
