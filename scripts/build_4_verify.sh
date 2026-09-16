#!/usr/bin/env bash

set -uo pipefail

if [[ $# -ne 4 ]]; then
  echo "Usage: $0 BASELINE_DIR ARCHIVE_DIR EVIDENCE_FILE EXPLANATIONS_FILE" >&2
  exit 2
fi

readonly BASELINE_DIR="$1"
readonly ARCHIVE_DIR="$2"
readonly EVIDENCE_FILE="$3"
readonly EXPLANATIONS_FILE="$4"
readonly BUILD_LOG="${ARCHIVE_DIR}/build.log"
readonly ENVIRONMENT_FILE="${ARCHIVE_DIR}/environment.txt"
readonly STATUS_FILE="${ARCHIVE_DIR}/run-status.txt"
readonly CHECKSUM_FILE="${ARCHIVE_DIR}/checksums.sha256"

for required in \
  "${BASELINE_DIR}/build.log" \
  "${BASELINE_DIR}/environment.txt" \
  "${EXPLANATIONS_FILE}"; do
  if [[ ! -s "${required}" ]]; then
    echo "Missing or empty BUILD-4 input: ${required}" >&2
    exit 2
  fi
done

for report_directory in surefire-reports failsafe-reports; do
  if [[ ! -d "${BASELINE_DIR}/${report_directory}" ]] \
      || [[ -z "$(find "${BASELINE_DIR}/${report_directory}" -name 'TEST-*.xml' -print -quit)" ]]; then
    echo "Missing non-empty baseline report directory: ${BASELINE_DIR}/${report_directory}" >&2
    exit 2
  fi
done

if [[ -e "${ARCHIVE_DIR}" ]]; then
  echo "Archive already exists; choose a new path: ${ARCHIVE_DIR}" >&2
  exit 2
fi

mkdir -p "${ARCHIVE_DIR}"
: >"${BUILD_LOG}"
cp "${EXPLANATIONS_FILE}" "${ARCHIVE_DIR}/expected-deltas.json"

record_environment() {
  {
    date
    uname -a
    java -version
    ./mvnw -version
    python3 --version
    docker --version
    docker compose version
    git rev-parse HEAD
    git status --short
  } >"${ENVIRONMENT_FILE}" 2>&1
}

run_logged() {
  local label="$1"
  shift
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "${label}" | tee -a "${BUILD_LOG}"
  "$@" 2>&1 | tee -a "${BUILD_LOG}"
  local command_status="${PIPESTATUS[0]}"
  printf '[exit=%s] %s\n' "${command_status}" "${label}" | tee -a "${BUILD_LOG}"
  return "${command_status}"
}

write_checksums() {
  (
    cd "${ARCHIVE_DIR}" || exit 1
    find . -type f ! -name "$(basename "${CHECKSUM_FILE}")" -print \
      | LC_ALL=C sort \
      | while IFS= read -r artifact; do
          shasum -a 256 "${artifact}"
        done
  ) >"${CHECKSUM_FILE}"
}

finish() {
  local status="$1"
  if ! write_checksums; then
    echo "Failed to write archive checksums: ${CHECKSUM_FILE}" >&2
    status=1
  fi
  printf 'BUILD-4 verification archive: %s\n' "${ARCHIVE_DIR}"
  printf 'BUILD-4 evidence report: %s\n' "${EVIDENCE_FILE}"
  exit "${status}"
}

record_environment

if ! run_logged \
  "Python governance tests" \
  python3 -B -m unittest discover -s scripts -p 'test_*.py'; then
  printf 'FAILED: Python governance tests\n' >"${STATUS_FILE}"
  finish 1
fi

if ! run_logged \
  "PostgreSQL image governance" \
  python3 -B scripts/postgres_image.py --check; then
  printf 'FAILED: PostgreSQL image governance\n' >"${STATUS_FILE}"
  finish 1
fi

if ! run_logged "Compose image resolution" docker compose config --images; then
  printf 'FAILED: Compose image resolution\n' >"${STATUS_FILE}"
  finish 1
fi

governed_image="$(python3 -B scripts/postgres_image.py --print-image)"
if ! run_logged "Local architecture image pull: ${governed_image}" docker pull "${governed_image}"; then
  printf 'FAILED: local architecture image pull\n' >"${STATUS_FILE}"
  finish 1
fi

run_logged "Maven clean verify" ./mvnw -B -ntp clean verify
maven_status="$?"

if [[ -d target/surefire-reports ]]; then
  cp -R target/surefire-reports "${ARCHIVE_DIR}/surefire-reports"
fi
if [[ -d target/failsafe-reports ]]; then
  cp -R target/failsafe-reports "${ARCHIVE_DIR}/failsafe-reports"
fi
mkdir -p "${ARCHIVE_DIR}/artifacts"
for artifact in target/spotbugsXml.xml target/jacoco.exec; do
  if [[ -f "${artifact}" ]]; then
    cp "${artifact}" "${ARCHIVE_DIR}/artifacts/"
  fi
done

comparison_status=2
if [[ -d "${ARCHIVE_DIR}/surefire-reports" && -d "${ARCHIVE_DIR}/failsafe-reports" ]]; then
  run_logged \
    "Baseline identity and status comparison" \
    python3 -B scripts/build_4_test_reports.py compare \
      "${BASELINE_DIR}" \
      "${ARCHIVE_DIR}" \
      "${EVIDENCE_FILE}" \
      --title "BUILD-4 PostgreSQL 16 verification" \
      --explanations "${EXPLANATIONS_FILE}"
  comparison_status="$?"
  if [[ -f "${EVIDENCE_FILE}" ]]; then
    cp "${EVIDENCE_FILE}" "${ARCHIVE_DIR}/evidence.md"
  fi
fi

if [[ "${maven_status}" -eq 0 && "${comparison_status}" -eq 0 ]]; then
  printf 'PASSED\n' >"${STATUS_FILE}"
  finish 0
fi

printf 'FAILED: maven=%s comparison=%s\n' \
  "${maven_status}" "${comparison_status}" >"${STATUS_FILE}"
finish 1
