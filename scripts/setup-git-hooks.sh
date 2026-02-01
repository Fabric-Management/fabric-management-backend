#!/bin/bash
# =============================================================================
# SETUP GIT HOOKS
# =============================================================================
# Installs pre-commit hook for migration-entity consistency checks
#
# Usage: ./scripts/setup-git-hooks.sh
# Last Updated: 2025-01-27

set -euo pipefail

# Colors
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m'

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
HOOKS_DIR="${PROJECT_ROOT}/.git/hooks"
SOURCE_HOOK="${SCRIPT_DIR}/hooks/pre-commit"
TARGET_HOOK="${HOOKS_DIR}/pre-commit"

# Validate git repository
if [ ! -d "${PROJECT_ROOT}/.git" ]; then
    echo -e "${RED}❌ .git directory not found. Are you in a git repository?${NC}"
    exit 1
fi

# Create hooks directory if it doesn't exist
mkdir -p "${HOOKS_DIR}"

# Validate source hook exists
if [ ! -f "${SOURCE_HOOK}" ]; then
    echo -e "${RED}❌ Source hook not found: ${SOURCE_HOOK}${NC}"
    exit 1
fi

# Copy and make executable
cp "${SOURCE_HOOK}" "${TARGET_HOOK}"
chmod +x "${TARGET_HOOK}"

echo -e "${GREEN}✅ Pre-commit hook installed: ${TARGET_HOOK}${NC}"
echo ""
echo -e "${YELLOW}💡 The hook checks: Java format (when .java staged) + migration-entity consistency.${NC}"
echo -e "${YELLOW}💡 To skip (not recommended): git commit --no-verify${NC}"

